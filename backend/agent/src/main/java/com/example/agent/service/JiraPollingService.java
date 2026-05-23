package com.example.agent.service;

import com.example.agent.client.JiraClient;
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

    static final Set<String> CRITICAL_KEYWORDS = Set.of(
            "рухнула сборка", "упала сборка", "сборка упала", "сборка рухнула",
            "провалились тесты", "тесты провалились", "failed build",
            "build failed", "test failed", "tests failed", "ci failed",
            "автотесты провалились", "провал автотестов"
    );

    private final JiraClient jiraClient;
    private final SignificantEventRepository significantEventRepository;

    @Value("${jira.base-url}")
    private String jiraBaseUrl;

    /**
     * Периодически опрашивает Jira по всем проектам всех руководителей
     * и актуализирует таблицу significant_events.
     */
    @Scheduled(fixedRateString = "${jira.polling-interval-ms:300000}")
    @Transactional
    public void poll() {
        // 1. Получить все доступные проекты из Jira
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

        // 2. Получить имена проектов один раз
        Map<String, String> projectNames = fetchProjectNames(allProjectKeys);

        // 3. Запросить значимые задачи
        String jql = buildCurrentJql(allProjectKeys);
        List<JiraIssueDto> issues = fetchAllIssues(jql);

        // 4. Upsert — обновляем или создаём записи
        Set<String> freshKeys = new HashSet<>();
        Instant now = Instant.now();

        for (JiraIssueDto issue : issues) {
            String priority = determinePriority(issue);
            String problem = describeProblem(issue);
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

            event.setPriority(priority);
            event.setTaskName(issue.fields().summary());
            event.setTeamKey(projectKey);
            event.setTeamName(teamName);
            event.setAssignees(resolveAssignees(issue));
            event.setProblem(problem);
            event.setJiraUrl(jiraBaseUrl + "/browse/" + issue.key());
            event.setLastUpdatedAt(now);

            significantEventRepository.save(event);
            freshKeys.add(issue.key());
        }

        // 5. Удалить события, которые больше не актуальны
        List<SignificantEvent> stale = significantEventRepository.findAll().stream()
                .filter(e -> allProjectKeys.contains(e.getTeamKey()) && !freshKeys.contains(e.getJiraIssueKey()))
                .toList();

        if (!stale.isEmpty()) {
            log.info("Removing {} resolved significant event(s)", stale.size());
            significantEventRepository.deleteAll(stale);
        }

        log.info("Poll complete: {} significant event(s) in DB", freshKeys.size());
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

    // ── Pagination (nextPageToken) ─────────────────────────────────────────────

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

    // ── Priority logic ────────────────────────────────────────────────────────

    String determinePriority(JiraIssueDto issue) {
        if (isCritical(issue.fields().summary())) return "RED";

        String duedate = issue.fields().duedate();
        if (duedate != null) {
            LocalDate due = LocalDate.parse(duedate);
            LocalDate today = LocalDate.now();
            if (due.isBefore(today)) return "YELLOW";
            if (ChronoUnit.DAYS.between(today, due) <= 2) return "GREEN";
        }

        if (hasConflict(issue)) return "GREEN";

        return "GREEN";
    }

    static boolean isCritical(String summary) {
        if (summary == null) return false;
        String lower = summary.toLowerCase();
        return CRITICAL_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private boolean hasConflict(JiraIssueDto issue) {
        if (issue.fields().comment() == null) return false;
        List<JiraCommentDto> comments = issue.fields().comment().comments();
        if (comments == null || comments.size() < 2) return false;

        boolean hasKeyword = false;
        Set<String> authors = new HashSet<>();

        for (JiraCommentDto c : comments) {
            String text = extractText(c.body()).toLowerCase();
            if (text.contains("конфликт") || text.contains("не согласен")
                    || text.contains("не согласна") || text.contains("поругались")
                    || text.contains("спор") || text.contains("разногласие")
                    || text.contains("противоречие")) {
                hasKeyword = true;
            }
            if (c.author() != null) {
                authors.add(c.author().accountId() != null
                        ? c.author().accountId() : c.author().displayName());
            }
        }

        return hasKeyword && authors.size() >= 2;
    }

    String describeProblem(JiraIssueDto issue) {
        if (isCritical(issue.fields().summary())) return "Рухнула сборка";

        String duedate = issue.fields().duedate();
        if (duedate != null) {
            LocalDate due = LocalDate.parse(duedate);
            LocalDate today = LocalDate.now();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM");
            if (due.isBefore(today)) return "Просрочен дедлайн (до " + due.format(fmt) + ")";
            if (ChronoUnit.DAYS.between(today, due) <= 2)
                return "Приближение дедлайна (" + due.format(fmt) + ")";
        }

        if (hasConflict(issue)) return "Поругались";

        return "";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    /** Извлекает текст из тела комментария (plain string или ADF). */
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
        return sb.toString();
    }
}
