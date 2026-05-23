package com.example.agent.service;

import com.example.agent.client.JiraClient;
import com.example.agent.dto.PriorityClassification;
import com.example.agent.dto.jira.JiraCommentDto;
import com.example.agent.dto.jira.JiraIssueDto;
import com.example.agent.dto.jira.JiraProjectDto;
import com.example.agent.dto.jira.JiraSearchRequest;
import com.example.agent.dto.jira.JiraSearchResponse;
import com.example.agent.entity.SignificantEvent;
import com.example.agent.repository.SignificantEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JiraPollingService {

    private static final List<String> JIRA_FIELDS =
            List.of("summary", "assignee", "duedate", "status", "comment", "project");

    private static final String SYSTEM_PROMPT = """
            Ты аналитик проблем в командах разработки.
            Проанализируй задачу из Jira и определи уровень значимости проблемы.

            Правила приоритетов:
            - RED   — критическая проблема, требует немедленного реагирования:
                      рухнула сборка, упали тесты, CI/CD сломан, критический баг в проде.
            - YELLOW — важная проблема: дедлайн уже просрочен (задача не сдана вовремя).
            - GREEN  — информационная: дедлайн через 1–2 дня ИЛИ конфликт/напряжение
                      в комментариях между участниками команды.

            Если задача попадает под несколько категорий — выбирай наивысший приоритет (RED > YELLOW > GREEN).

            Отвечай ТОЛЬКО валидным JSON без markdown-блоков, строго в формате:
            {"priority":"RED","problem":"краткое описание на русском"}
            """;

    private final JiraClient jiraClient;
    private final SignificantEventRepository significantEventRepository;
    private final ChatClient chatClient;

    @Value("${jira.base-url}")
    private String jiraBaseUrl;

    @Scheduled(fixedRateString = "${jira.polling-interval-ms:300000}")
    @Transactional
    public void poll() {
        List<String> allProjectKeys;
        try {
            allProjectKeys = jiraClient.getProjects().stream()
                    .map(JiraProjectDto::key)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to fetch Jira projects: {}", e.getMessage());
            return;
        }

        if (allProjectKeys.isEmpty()) {
            log.debug("No Jira projects accessible, skipping poll");
            return;
        }

        log.info("Polling Jira for {} project(s): {}", allProjectKeys.size(), allProjectKeys);

        Map<String, String> projectNames = fetchProjectNames(allProjectKeys);
        String jql = buildCurrentJql(allProjectKeys);
        List<JiraIssueDto> issues = fetchAllIssues(jql);

        Set<String> freshKeys = new HashSet<>();
        Instant now = Instant.now();

        for (JiraIssueDto issue : issues) {
            PriorityClassification classification = classify(issue);
            String projectKey = issue.fields().project() != null ? issue.fields().project().key() : "";
            String teamName = projectNames.getOrDefault(projectKey,
                    issue.fields().project() != null ? issue.fields().project().name() : "");

            SignificantEvent event = significantEventRepository.findById(issue.key())
                    .orElseGet(() -> {
                        SignificantEvent e = new SignificantEvent();
                        e.setJiraIssueKey(issue.key());
                        e.setDetectedAt(now);
                        return e;
                    });

            event.setPriority(classification.priority());
            event.setTaskName(issue.fields().summary());
            event.setTeamKey(projectKey);
            event.setTeamName(teamName);
            event.setAssignees(resolveAssignees(issue));
            event.setProblem(classification.problem());
            event.setJiraUrl(jiraBaseUrl + "/browse/" + issue.key());
            event.setLastUpdatedAt(now);

            significantEventRepository.save(event);
            freshKeys.add(issue.key());
        }

        List<SignificantEvent> stale = significantEventRepository.findAll().stream()
                .filter(e -> allProjectKeys.contains(e.getTeamKey()) && !freshKeys.contains(e.getJiraIssueKey()))
                .toList();

        if (!stale.isEmpty()) {
            log.info("Removing {} resolved significant event(s)", stale.size());
            significantEventRepository.deleteAll(stale);
        }

        log.info("Poll complete: {} significant event(s) in DB", freshKeys.size());
    }

    // ── AI classification ─────────────────────────────────────────────────────

    private PriorityClassification classify(JiraIssueDto issue) {
        try {
            String userMessage = buildClassificationPrompt(issue);
            return chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userMessage)
                    .call()
                    .entity(PriorityClassification.class);
        } catch (Exception e) {
            log.warn("AI classification failed for {}, using rule-based fallback: {}", issue.key(), e.getMessage());
            return fallbackClassify(issue);
        }
    }

    private String buildClassificationPrompt(JiraIssueDto issue) {
        String duedate = issue.fields().duedate() != null ? issue.fields().duedate() : "не указан";
        String status = issue.fields().status() != null ? issue.fields().status().name() : "неизвестен";
        String comments = extractAllComments(issue);
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        return String.format("""
                Сегодня: %s
                Название задачи: %s
                Статус: %s
                Дедлайн: %s
                Комментарии участников:
                %s
                """,
                today,
                issue.fields().summary(),
                status,
                duedate,
                comments.isBlank() ? "(нет комментариев)" : comments
        );
    }

    // ── Rule-based fallback ───────────────────────────────────────────────────

    private static final Set<String> CRITICAL_KEYWORDS = Set.of(
            "рухнула сборка", "упала сборка", "сборка упала", "сборка рухнула",
            "провалились тесты", "тесты провалились", "failed build",
            "build failed", "test failed", "tests failed", "ci failed",
            "автотесты провалились", "провал автотестов"
    );

    private PriorityClassification fallbackClassify(JiraIssueDto issue) {
        String summary = issue.fields().summary();
        if (summary != null) {
            String lower = summary.toLowerCase();
            if (CRITICAL_KEYWORDS.stream().anyMatch(lower::contains)) {
                return new PriorityClassification("RED", "Рухнула сборка");
            }
        }

        String duedate = issue.fields().duedate();
        if (duedate != null) {
            LocalDate due = LocalDate.parse(duedate);
            LocalDate today = LocalDate.now();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM");
            if (due.isBefore(today)) {
                return new PriorityClassification("YELLOW", "Просрочен дедлайн (до " + due.format(fmt) + ")");
            }
            if (ChronoUnit.DAYS.between(today, due) <= 2) {
                return new PriorityClassification("GREEN", "Приближение дедлайна (" + due.format(fmt) + ")");
            }
        }

        return new PriorityClassification("GREEN", "Требует внимания");
    }

    // ── JQL ───────────────────────────────────────────────────────────────────

    private String buildCurrentJql(List<String> projectKeys) {
        String projects = projectKeys.stream()
                .map(k -> "\"" + k + "\"")
                .collect(Collectors.joining(", "));

        String criticalKeywords = CRITICAL_KEYWORDS.stream()
                .map(kw -> "summary ~ \"" + kw + "\"")
                .collect(Collectors.joining(" OR "));

        LocalDate today = LocalDate.now();
        String todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String in2Days = today.plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE);

        return String.format(
                "project IN (%s) AND (" +
                        "duedate < \"%s\" OR " +
                        "(duedate >= \"%s\" AND duedate <= \"%s\") OR " +
                        "(%s)" +
                        ")",
                projects, todayStr, todayStr, in2Days, criticalKeywords
        );
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    private List<JiraIssueDto> fetchAllIssues(String jql) {
        List<JiraIssueDto> result = new ArrayList<>();
        String nextPageToken = null;

        do {
            try {
                JiraSearchRequest request = new JiraSearchRequest(jql, 100, nextPageToken, JIRA_FIELDS);
                JiraSearchResponse page = jiraClient.search(request);
                if (page.issues() == null || page.issues().isEmpty()) break;
                result.addAll(page.issues());
                nextPageToken = Boolean.TRUE.equals(page.isLast()) ? null : page.nextPageToken();
            } catch (Exception e) {
                log.error("Error fetching Jira issues: {}", e.getMessage());
                break;
            }
        } while (nextPageToken != null);

        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractAllComments(JiraIssueDto issue) {
        if (issue.fields().comment() == null) return "";
        List<JiraCommentDto> comments = issue.fields().comment().comments();
        if (comments == null) return "";

        return comments.stream()
                .map(c -> {
                    String author = c.author() != null ? c.author().displayName() : "Аноним";
                    String text = extractText(c.body());
                    return author + ": " + text;
                })
                .collect(Collectors.joining("\n"));
    }

    private List<String> resolveAssignees(JiraIssueDto issue) {
        if (issue.fields().assignee() == null) return List.of();
        return List.of(issue.fields().assignee().displayName());
    }

    private Map<String, String> fetchProjectNames(List<String> projectKeys) {
        try {
            return jiraClient.getProjects().stream()
                    .filter(p -> projectKeys.contains(p.key()))
                    .collect(Collectors.toMap(JiraProjectDto::key, JiraProjectDto::name));
        } catch (Exception e) {
            log.warn("Could not fetch project names: {}", e.getMessage());
            return Map.of();
        }
    }

    private String extractText(JsonNode body) {
        if (body == null) return "";
        if (body.isTextual()) return body.asText();
        StringBuilder sb = new StringBuilder();
        if (body.has("text")) sb.append(body.get("text").asText()).append(" ");
        if (body.has("content")) {
            for (JsonNode node : body.get("content")) {
                sb.append(extractText(node));
            }
        }
        return sb.toString().trim();
    }
}
