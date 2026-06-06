package com.example.agent.service;

import com.example.agent.client.JiraClient;
import com.example.agent.dto.*;
import com.example.agent.dto.jira.JiraIssueDto;
import com.example.agent.dto.jira.JiraSearchRequest;
import com.example.agent.dto.jira.JiraSearchResponse;
import com.example.agent.entity.TaskComplexity;
import com.example.agent.repository.TaskComplexityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final List<String> JIRA_FIELDS =
            List.of("summary", "assignee", "reporter", "duedate", "status",
                    "project", "customfield_10015");

    private static final List<String> ROADMAP_PARENT_FIELDS =
            List.of("summary", "assignee", "duedate", "status", "customfield_10015");

    private static final List<String> ROADMAP_SUBTASK_FIELDS =
            List.of("summary", "assignee", "duedate", "status", "customfield_10015", "labels", "parent");

    private static final Set<String> IN_WORK_STATUSES = Set.of(
            "in progress", "в работе", "в процессе", "горят сроки",
            "in development", "in review", "в разработке"
    );

    private static final Set<String> KNOWN_PHASE_LABELS = Set.of(
            "аналитика", "разработка", "тест", "интеграция", "внедрение"
    );

    private static final List<String> TASK_COLORS = List.of(
            "#FFDCE2", "#D4F1F9", "#D4F9D4", "#FFF3CC", "#EAD4F9", "#FFE4CC"
    );

    private static final Set<String> CLOSED_STATUSES = Set.of(
            "done", "готово", "closed", "закрыто", "resolved", "решено"
    );

    private static final String COMPLEXITY_SYSTEM_PROMPT = """
            Ты оцениваешь сложность задач в Jira.
            По названию задачи определи: EASY (лёгкая) или HARD (сложная).

            HARD — рефакторинг, интеграция с внешними сервисами, архитектурные изменения,
                   security, миграции данных, сложные алгоритмы.
            EASY — UI-правки, мелкие фиксы, документация, переименования,
                   небольшие улучшения без бизнес-логики.

            Отвечай ТОЛЬКО валидным JSON без markdown:
            {"complexity":"HARD"}
            """;

    private final JiraClient jiraClient;
    private final TaskComplexityRepository taskComplexityRepository;
    private final ChatClient chatClient;

    @Value("${jira.base-url}")
    private String jiraBaseUrl;

    // ── GET tasks ─────────────────────────────────────────────────────────────

    public List<DashboardTaskResponse> getTasks(String teamKey, LocalDate weekStart) {
        String jql = buildJql(teamKey, weekStart);
        List<JiraIssueDto> issues = fetchAllIssues(jql);

        if (issues.isEmpty()) return List.of();

        // Загружаем все ручные overrides одним запросом
        List<String> issueKeys = issues.stream().map(JiraIssueDto::key).toList();
        Map<String, String> overrides = taskComplexityRepository
                .findByJiraIssueKeyIn(issueKeys)
                .stream()
                .collect(Collectors.toMap(TaskComplexity::getJiraIssueKey, TaskComplexity::getComplexity));

        return issues.stream()
                .map(issue -> toResponse(issue, overrides))
                .toList();
    }

    // ── PATCH complexity ──────────────────────────────────────────────────────

    @Transactional
    public DashboardTaskResponse updateComplexity(String issueKey, String complexity) {
        TaskComplexity record = taskComplexityRepository.findById(issueKey)
                .orElseGet(() -> new TaskComplexity(issueKey, complexity));
        record.setComplexity(complexity);
        record.setOverriddenAt(java.time.Instant.now());
        taskComplexityRepository.save(record);

        // Возвращаем обновлённую задачу
        String jql = "key = \"" + issueKey + "\"";
        List<JiraIssueDto> issues = fetchAllIssues(jql);
        if (issues.isEmpty()) throw new NoSuchElementException("Jira issue not found: " + issueKey);

        Map<String, String> overrides = Map.of(issueKey, complexity);
        return toResponse(issues.getFirst(), overrides);
    }

    // ── GET stats ─────────────────────────────────────────────────────────────

    public DashboardStatsResponse getStats(String teamKey, LocalDate weekStart) {
        LocalDate monday = resolveMonday(weekStart);
        LocalDate prevMonday = monday.minusWeeks(1);

        List<JiraIssueDto> current = fetchIssuesRaw(teamKey, monday);
        List<JiraIssueDto> prev = fetchIssuesRaw(teamKey, prevMonday);

        int inWork = (int) current.stream().filter(this::isInWork).count();
        int done = (int) current.stream().filter(this::isDone).count();
        int total = current.size();

        int prevInWork = (int) prev.stream().filter(this::isInWork).count();
        int prevDone = (int) prev.stream().filter(this::isDone).count();
        int prevTotal = prev.size();

        return new DashboardStatsResponse(
                inWork, inWork - prevInWork,
                total, total - prevTotal,
                done, done - prevDone
        );
    }

    // ── GET charts ────────────────────────────────────────────────────────────

    public DashboardChartsResponse getCharts(String teamKey, LocalDate weekStart) {
        List<DashboardTaskResponse> tasks = getTasks(teamKey, weekStart);

        List<PersonTaskStats> completed = aggregatePerPerson(
                tasks.stream().filter(t -> "CLOSED".equals(t.status())).toList()
        );
        List<PersonTaskStats> active = aggregatePerPerson(
                tasks.stream().filter(t -> "OPEN".equals(t.status())).toList()
        );

        return new DashboardChartsResponse(completed, active);
    }

    private List<PersonTaskStats> aggregatePerPerson(List<DashboardTaskResponse> tasks) {
        Map<String, int[]> map = new LinkedHashMap<>();
        for (DashboardTaskResponse t : tasks) {
            for (String assignee : t.assignees()) {
                int[] counts = map.computeIfAbsent(assignee, k -> new int[2]);
                if ("EASY".equals(t.complexity())) counts[0]++;
                else counts[1]++;
            }
        }
        return map.entrySet().stream()
                .map(e -> new PersonTaskStats(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    // ── GET roadmap ───────────────────────────────────────────────────────────

    public DashboardRoadmapResponse getRoadmap(String teamKey) {
        // Загружаем основные задачи (без subtask-фильтра — Jira не всегда имеет issuetype subtask)
        String parentJql = buildJql(teamKey, null);
        List<JiraIssueDto> parentIssues = fetchAllIssues(parentJql, ROADMAP_PARENT_FIELDS);

        if (parentIssues.isEmpty()) return new DashboardRoadmapResponse(List.of());

        // Загружаем подзадачи для всех найденных задач за один запрос
        String keys = parentIssues.stream()
                .map(i -> "\"" + i.key() + "\"")
                .collect(Collectors.joining(", "));
        String subtaskJql = "parent in (" + keys + ") ORDER BY created ASC";
        List<JiraIssueDto> subtasks = fetchAllIssues(subtaskJql, ROADMAP_SUBTASK_FIELDS);

        // Группируем подзадачи по родителю
        Map<String, List<JiraIssueDto>> subtasksByParent = subtasks.stream()
                .filter(s -> s.fields().parent() != null)
                .collect(Collectors.groupingBy(s -> s.fields().parent().key()));

        List<RoadmapTask> roadmapTasks = new ArrayList<>();
        int colorIdx = 0;
        for (JiraIssueDto parent : parentIssues) {
            String color = TASK_COLORS.get(colorIdx % TASK_COLORS.size());
            colorIdx++;

            List<JiraIssueDto> children = subtasksByParent.getOrDefault(parent.key(), List.of());
            List<RoadmapPhase> phases = buildPhases(children);

            roadmapTasks.add(new RoadmapTask(
                    parent.key(),
                    parent.fields().summary(),
                    color,
                    parent.fields().startDate(),
                    parent.fields().duedate(),
                    phases
            ));
        }

        return new DashboardRoadmapResponse(roadmapTasks);
    }

    private List<RoadmapPhase> buildPhases(List<JiraIssueDto> subtasks) {
        Map<String, Integer> phaseNameCount = new LinkedHashMap<>();
        List<RoadmapPhase> phases = new ArrayList<>();

        for (JiraIssueDto sub : subtasks) {
            String phaseName = resolvePhaseNameFromLabels(sub);
            int count = phaseNameCount.merge(phaseName, 1, Integer::sum);
            String displayName = count > 1 ? phaseName + " " + count : phaseName;

            String assignee = sub.fields().assignee() != null
                    ? sub.fields().assignee().displayName() : "";

            phases.add(new RoadmapPhase(
                    displayName,
                    assignee,
                    sub.fields().startDate(),
                    sub.fields().duedate()
            ));
        }
        return phases;
    }

    private String resolvePhaseNameFromLabels(JiraIssueDto issue) {
        if (issue.fields().labels() != null) {
            for (String label : issue.fields().labels()) {
                String lower = label.toLowerCase();
                if (KNOWN_PHASE_LABELS.contains(lower)) {
                    return capitalize(lower);
                }
            }
        }
        // Если метка не совпала — используем summary подзадачи
        return issue.fields().summary() != null ? issue.fields().summary() : "Этап";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ── Status helpers ────────────────────────────────────────────────────────

    private boolean isInWork(JiraIssueDto issue) {
        if (issue.fields().status() == null) return false;
        return IN_WORK_STATUSES.contains(issue.fields().status().name().toLowerCase());
    }

    private boolean isDone(JiraIssueDto issue) {
        if (issue.fields().status() == null) return false;
        return CLOSED_STATUSES.contains(issue.fields().status().name().toLowerCase());
    }

    private LocalDate resolveMonday(LocalDate weekStart) {
        LocalDate base = weekStart != null ? weekStart : LocalDate.now();
        return base.with(DayOfWeek.MONDAY);
    }

    // ── Raw fetch (без AI-классификации) ─────────────────────────────────────

    private List<JiraIssueDto> fetchIssuesRaw(String teamKey, LocalDate weekStart) {
        return fetchAllIssues(buildJql(teamKey, weekStart), JIRA_FIELDS);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private DashboardTaskResponse toResponse(JiraIssueDto issue, Map<String, String> overrides) {
        String status = resolveStatus(issue);
        String dueDate = issue.fields().duedate();
        String startDate = issue.fields().startDate();
        boolean overdue = dueDate != null
                && LocalDate.parse(dueDate).isBefore(LocalDate.now())
                && "OPEN".equals(status);

        // Сложность: ручной override → иначе спрашиваем ИИ
        boolean complexityOverridden = overrides.containsKey(issue.key());
        String complexity = complexityOverridden
                ? overrides.get(issue.key())
                : classifyComplexity(issue.fields().summary());

        String initiatorName = issue.fields().reporter() != null
                ? issue.fields().reporter().displayName() : "";
        // Роль репортёра в Jira не возвращается стандартными полями — используем jobTitle если есть
        String initiatorRole = issue.fields().reporter() != null
                && issue.fields().reporter().emailAddress() != null
                ? resolveRole(issue.fields().reporter().emailAddress()) : "";

        List<String> assignees = issue.fields().assignee() != null
                ? List.of(issue.fields().assignee().displayName())
                : List.of();

        return new DashboardTaskResponse(
                issue.key(),
                initiatorRole,
                initiatorName,
                issue.fields().summary(),
                complexity,
                complexityOverridden,
                formatDate(startDate),
                formatDate(dueDate),
                overdue,
                assignees,
                jiraBaseUrl + "/browse/" + issue.key(),
                status
        );
    }

    private String resolveStatus(JiraIssueDto issue) {
        if (issue.fields().status() == null) return "OPEN";
        String name = issue.fields().status().name().toLowerCase();
        return CLOSED_STATUSES.contains(name) ? "CLOSED" : "OPEN";
    }

    private String formatDate(String isoDate) {
        if (isoDate == null) return null;
        try {
            return LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("dd.MM"));
        } catch (Exception e) {
            return isoDate;
        }
    }

    /**
     * Роль определяем по домену почты или возвращаем пустую строку.
     * Расширить при наличии кастомных полей в Jira.
     */
    private String resolveRole(String email) {
        return "";
    }

    // ── AI complexity ─────────────────────────────────────────────────────────

    private record ComplexityResult(String complexity) {}

    private String classifyComplexity(String summary) {
        if (summary == null) return "HARD";
        try {
            ComplexityResult result = chatClient.prompt()
                    .system(COMPLEXITY_SYSTEM_PROMPT)
                    .user("Задача: " + summary)
                    .call()
                    .entity(ComplexityResult.class);
            String c = result.complexity().toUpperCase();
            return c.equals("EASY") || c.equals("HARD") ? c : "HARD";
        } catch (Exception e) {
            log.warn("Complexity AI failed for '{}': {}", summary, e.getMessage());
            return "HARD";
        }
    }

    // ── JQL ───────────────────────────────────────────────────────────────────

    private String buildJql(String teamKey, LocalDate weekStart) {
        String projectFilter = teamKey != null
                ? "project = \"" + teamKey + "\""
                : "project IN (" + getAccessibleProjectKeys() + ")";

        if (weekStart == null) return projectFilter + " ORDER BY created DESC";

        LocalDate monday = weekStart.with(java.time.DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        String fmt = DateTimeFormatter.ISO_LOCAL_DATE.toString();

        return projectFilter
                + " AND updated >= \"" + monday.format(DateTimeFormatter.ISO_LOCAL_DATE) + "\""
                + " AND updated <= \"" + sunday.format(DateTimeFormatter.ISO_LOCAL_DATE) + "\""
                + " ORDER BY created DESC";
    }

    private String getAccessibleProjectKeys() {
        try {
            return jiraClient.getProjects().stream()
                    .map(p -> "\"" + p.key() + "\"")
                    .collect(Collectors.joining(", "));
        } catch (Exception e) {
            return "\"\"";
        }
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    private List<JiraIssueDto> fetchAllIssues(String jql) {
        return fetchAllIssues(jql, JIRA_FIELDS);
    }

    private List<JiraIssueDto> fetchAllIssues(String jql, List<String> fields) {
        List<JiraIssueDto> result = new ArrayList<>();
        String nextPageToken = null;

        do {
            try {
                JiraSearchRequest request = new JiraSearchRequest(jql, 100, nextPageToken, fields);
                JiraSearchResponse page = jiraClient.search(request);
                if (page.issues() == null || page.issues().isEmpty()) break;
                result.addAll(page.issues());
                nextPageToken = Boolean.TRUE.equals(page.isLast()) ? null : page.nextPageToken();
            } catch (Exception e) {
                log.error("Error fetching issues (jql={}): {}", jql, e.getMessage());
                break;
            }
        } while (nextPageToken != null);

        return result;
    }
}
