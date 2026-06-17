package com.example.agent.service;

import com.example.agent.client.JiraClient;
import com.example.agent.dto.*;
import com.example.agent.dto.jira.JiraIssueDto;
import com.example.agent.dto.jira.JiraSearchRequest;
import com.example.agent.dto.jira.JiraSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
                    "project", "customfield_10015", "priority");

    private static final List<String> ROADMAP_PARENT_FIELDS =
            List.of("summary", "assignee", "duedate", "status", "customfield_10015", "labels");

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

    private final JiraClient jiraClient;

    @Value("${jira.base-url}")
    private String jiraBaseUrl;

    // ── GET tasks ─────────────────────────────────────────────────────────────

    public List<DashboardTaskResponse> getTasks(String teamKey, LocalDate periodStart, LocalDate periodEnd) {
        String jql = buildTasksJql(teamKey, periodStart, periodEnd);
        List<JiraIssueDto> issues = fetchAllIssues(jql);

        return issues.stream()
                .map(this::toResponse)
                .toList();
    }

    // ── GET stats ─────────────────────────────────────────────────────────────

    public DashboardStatsResponse getStats(String teamKey, LocalDate periodStart, LocalDate periodEnd) {
        List<JiraIssueDto> current;
        List<JiraIssueDto> prev;

        if (periodStart != null && periodEnd != null) {
            long periodLength = java.time.temporal.ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
            LocalDate prevStart = periodStart.minusDays(periodLength);
            LocalDate prevEnd = periodEnd.minusDays(periodLength);
            current = fetchAllIssues(buildTasksJql(teamKey, periodStart, periodEnd));
            prev = fetchAllIssues(buildTasksJql(teamKey, prevStart, prevEnd));
        } else {
            current = fetchIssuesSnapshot(teamKey);
            LocalDate prevMonday = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(1);
            prev = fetchIssuesRaw(teamKey, prevMonday);
        }

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

    public DashboardChartsResponse getCharts(String teamKey, LocalDate periodStart, LocalDate periodEnd) {
        List<DashboardTaskResponse> tasks = getTasks(teamKey, periodStart, periodEnd);

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
                if (!"High".equalsIgnoreCase(t.priority()) && !"Highest".equalsIgnoreCase(t.priority())) counts[0]++;
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

            List<String> labels = parent.fields().labels() != null
                    ? parent.fields().labels()
                    : List.of();
            roadmapTasks.add(new RoadmapTask(
                    parent.key(),
                    parent.fields().summary(),
                    color,
                    parent.fields().startDate(),
                    parent.fields().duedate(),
                    phases,
                    labels
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

    private List<JiraIssueDto> fetchIssuesRaw(String teamKey, LocalDate weekStart) {
        return fetchAllIssues(buildJql(teamKey, weekStart), JIRA_FIELDS);
    }

    private List<JiraIssueDto> fetchIssuesSnapshot(String teamKey) {
        return fetchAllIssues(buildJql(teamKey, null), JIRA_FIELDS);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private DashboardTaskResponse toResponse(JiraIssueDto issue) {
        String status = resolveStatus(issue);
        String dueDate = issue.fields().duedate();
        String startDate = issue.fields().startDate();
        boolean overdue = dueDate != null
                && LocalDate.parse(dueDate).isBefore(LocalDate.now())
                && "OPEN".equals(status);

        String priority = issue.fields().priority() != null ? issue.fields().priority().name() : null;

        String initiatorName = issue.fields().reporter() != null
                ? issue.fields().reporter().displayName() : "";
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
                priority,
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

    // ── JQL ───────────────────────────────────────────────────────────────────

    private String buildJql(String teamKey, LocalDate weekStart) {
        String projectFilter = teamKey != null
                ? "project = \"" + teamKey + "\""
                : "project IN (" + getAccessibleProjectKeys() + ")";

        if (weekStart == null) return projectFilter + " ORDER BY created DESC";

        LocalDate monday = weekStart.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);

        return projectFilter
                + " AND updated >= \"" + monday.format(DateTimeFormatter.ISO_LOCAL_DATE) + "\""
                + " AND updated <= \"" + sunday.format(DateTimeFormatter.ISO_LOCAL_DATE) + "\""
                + " ORDER BY created DESC";
    }

    private String buildTasksJql(String teamKey, LocalDate periodStart, LocalDate periodEnd) {
        String projectFilter = teamKey != null
                ? "project = \"" + teamKey + "\""
                : "project IN (" + getAccessibleProjectKeys() + ")";

        if (periodStart == null || periodEnd == null) {
            return projectFilter + " ORDER BY created DESC";
        }

        String inWorkList = IN_WORK_STATUSES.stream()
                .map(s -> "\"" + s + "\"")
                .collect(Collectors.joining(", "));

        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String startStr = periodStart.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String endStr = periodEnd.format(DateTimeFormatter.ISO_LOCAL_DATE);

        return projectFilter
                + " AND ((duedate >= \"" + startStr + "\" AND duedate <= \"" + endStr + "\")"
                + " OR (duedate is EMPTY AND status in (" + inWorkList + "))"
                + " OR (duedate < \"" + today + "\" AND status in (" + inWorkList + ")))"
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
