package com.example.agent.service;

import com.example.agent.client.JiraClient;
import com.example.agent.dto.*;
import com.example.agent.dto.jira.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    @Mock JiraClient jiraClient;

    DashboardService service;

    private static final LocalDate WEEK = LocalDate.of(2025, 5, 19); // Monday

    @BeforeEach
    void setUp() {
        service = new DashboardService(jiraClient);
        ReflectionTestUtils.setField(service, "jiraBaseUrl", "https://jira.example.com");
    }

    // ────────────────────────────────────────────────────────────────
    // getStats
    // ────────────────────────────────────────────────────────────────

    @Test
    void getStats_countsInWorkDoneAndTotal_correctly() {
        // current week: 2 in progress, 1 done, 1 planned → total 4
        List<JiraIssueDto> current = List.of(
                issue("T-1", "В работе"),
                issue("T-2", "in progress"),
                issue("T-3", "Готово"),
                issue("T-4", "To Do")
        );
        // previous week: 1 in progress, 1 done → total 2
        List<JiraIssueDto> prev = List.of(
                issue("T-5", "in progress"),
                issue("T-6", "done")
        );

        when(jiraClient.search(any()))
                .thenReturn(page(current, true))
                .thenReturn(page(prev, true));

        DashboardStatsResponse stats = service.getStats("TEST", WEEK);

        assertThat(stats.inWorkCount()).isEqualTo(2);
        assertThat(stats.inWorkDelta()).isEqualTo(1);   // 2 - 1

        assertThat(stats.doneCount()).isEqualTo(1);
        assertThat(stats.doneDelta()).isEqualTo(0);     // 1 - 1

        assertThat(stats.backlogCount()).isEqualTo(4);
        assertThat(stats.backlogDelta()).isEqualTo(2);  // 4 - 2
    }

    @Test
    void getStats_emptyJira_returnsZeros() {
        when(jiraClient.search(any()))
                .thenReturn(page(List.of(), true));

        DashboardStatsResponse stats = service.getStats("EMPTY", WEEK);

        assertThat(stats.inWorkCount()).isZero();
        assertThat(stats.backlogCount()).isZero();
        assertThat(stats.doneCount()).isZero();
        assertThat(stats.inWorkDelta()).isZero();
    }

    @Test
    void getStats_negativeDelta_whenCurrentWeekLessThanPrevious() {
        List<JiraIssueDto> current = List.of(issue("T-1", "done"));
        List<JiraIssueDto> prev = List.of(
                issue("T-2", "done"),
                issue("T-3", "done"),
                issue("T-4", "done")
        );

        when(jiraClient.search(any()))
                .thenReturn(page(current, true))
                .thenReturn(page(prev, true));

        DashboardStatsResponse stats = service.getStats("TEST", WEEK);

        assertThat(stats.doneCount()).isEqualTo(1);
        assertThat(stats.doneDelta()).isEqualTo(-2); // 1 - 3
    }

    // ────────────────────────────────────────────────────────────────
    // getCharts
    // ────────────────────────────────────────────────────────────────

    @Test
    void getCharts_completedPerPerson_groupsByAssigneeAndPriority() {
        // Two closed tasks for Иван (1 easy=Medium, 1 hard=High)
        JiraIssueDto ivan1 = issueWithAssignee("T-1", "Готово", "Иван", "Medium");
        JiraIssueDto ivan2 = issueWithAssignee("T-2", "Готово", "Иван", "High");
        // One closed task for Мария (hard=Highest)
        JiraIssueDto maria = issueWithAssignee("T-3", "Готово", "Мария", "Highest");

        when(jiraClient.search(any())).thenReturn(page(List.of(ivan1, ivan2, maria), true));

        DashboardChartsResponse charts = service.getCharts("TEST", WEEK, WEEK.plusDays(6));

        // All three tasks are CLOSED → completedPerPerson
        assertThat(charts.completedPerPerson()).hasSize(2);
        PersonTaskStats ivanStats = charts.completedPerPerson().stream()
                .filter(p -> "Иван".equals(p.personName())).findFirst().orElseThrow();
        assertThat(ivanStats.easyCount()).isEqualTo(1);
        assertThat(ivanStats.hardCount()).isEqualTo(1);

        PersonTaskStats mariaStats = charts.completedPerPerson().stream()
                .filter(p -> "Мария".equals(p.personName())).findFirst().orElseThrow();
        assertThat(mariaStats.hardCount()).isEqualTo(1);

        // No open tasks → activePerPerson is empty
        assertThat(charts.activePerPerson()).isEmpty();
    }

    @Test
    void getCharts_activePerPerson_onlyInWorkTasks() {
        JiraIssueDto inWork = issueWithAssignee("T-1", "in progress", "Петр", "Low");
        JiraIssueDto done = issueWithAssignee("T-2", "Готово", "Петр", "Low");

        when(jiraClient.search(any())).thenReturn(page(List.of(inWork, done), true));

        DashboardChartsResponse charts = service.getCharts("TEST", WEEK, WEEK.plusDays(6));

        // activePerPerson uses OPEN tasks (in progress is OPEN, done is CLOSED)
        assertThat(charts.activePerPerson()).hasSize(1);
        assertThat(charts.activePerPerson().getFirst().personName()).isEqualTo("Петр");
        assertThat(charts.activePerPerson().getFirst().easyCount()).isEqualTo(1);

        assertThat(charts.completedPerPerson()).hasSize(1);
        assertThat(charts.completedPerPerson().getFirst().personName()).isEqualTo("Петр");
    }

    // ────────────────────────────────────────────────────────────────
    // getRoadmap
    // ────────────────────────────────────────────────────────────────

    @Test
    void getRoadmap_buildsTasksWithPhasesFromSubtasks() {
        JiraIssueDto parent = issue("PROJ-1", "To Do");
        JiraIssueDto subtask1 = subtask("PROJ-2", "PROJ-1", "аналитика", "Ольга");
        JiraIssueDto subtask2 = subtask("PROJ-3", "PROJ-1", "разработка", "Иван");

        when(jiraClient.search(any()))
                .thenReturn(page(List.of(parent), true))   // main issues
                .thenReturn(page(List.of(subtask1, subtask2), true)); // subtasks

        DashboardRoadmapResponse roadmap = service.getRoadmap("PROJ");

        assertThat(roadmap.tasks()).hasSize(1);
        RoadmapTask task = roadmap.tasks().getFirst();
        assertThat(task.key()).isEqualTo("PROJ-1");
        assertThat(task.phases()).hasSize(2);

        RoadmapPhase phase1 = task.phases().get(0);
        assertThat(phase1.phaseName()).isEqualTo("Аналитика");
        assertThat(phase1.assignee()).isEqualTo("Ольга");

        RoadmapPhase phase2 = task.phases().get(1);
        assertThat(phase2.phaseName()).isEqualTo("Разработка");
        assertThat(phase2.assignee()).isEqualTo("Иван");
    }

    @Test
    void getRoadmap_multipleSubtasksWithSamePhase_numberedCorrectly() {
        JiraIssueDto parent = issue("PROJ-1", "To Do");
        JiraIssueDto dev1 = subtask("PROJ-2", "PROJ-1", "разработка", "Иван");
        JiraIssueDto dev2 = subtask("PROJ-3", "PROJ-1", "разработка", "Мария");

        when(jiraClient.search(any()))
                .thenReturn(page(List.of(parent), true))
                .thenReturn(page(List.of(dev1, dev2), true));

        DashboardRoadmapResponse roadmap = service.getRoadmap("PROJ");

        List<RoadmapPhase> phases = roadmap.tasks().getFirst().phases();
        assertThat(phases.get(0).phaseName()).isEqualTo("Разработка");
        assertThat(phases.get(1).phaseName()).isEqualTo("Разработка 2");
    }

    @Test
    void getRoadmap_noSubtasks_returnsTaskWithEmptyPhases() {
        JiraIssueDto parent = issue("PROJ-1", "To Do");

        when(jiraClient.search(any()))
                .thenReturn(page(List.of(parent), true))
                .thenReturn(page(List.of(), true));

        DashboardRoadmapResponse roadmap = service.getRoadmap("PROJ");

        assertThat(roadmap.tasks()).hasSize(1);
        assertThat(roadmap.tasks().getFirst().phases()).isEmpty();
    }

    @Test
    void getRoadmap_noIssues_returnsEmptyList() {
        when(jiraClient.search(any())).thenReturn(page(List.of(), true));

        DashboardRoadmapResponse roadmap = service.getRoadmap("EMPTY");

        assertThat(roadmap.tasks()).isEmpty();
    }

    @Test
    void getRoadmap_assignsUniqueColors() {
        List<JiraIssueDto> parents = List.of(
                issue("P-1", "To Do"),
                issue("P-2", "To Do"),
                issue("P-3", "To Do")
        );

        when(jiraClient.search(any()))
                .thenReturn(page(parents, true))
                .thenReturn(page(List.of(), true)); // no subtasks

        DashboardRoadmapResponse roadmap = service.getRoadmap("PROJ");

        List<String> colors = roadmap.tasks().stream().map(RoadmapTask::color).toList();
        assertThat(colors).doesNotHaveDuplicates();
    }

    // ────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────

    private JiraIssueDto issue(String key, String status) {
        JiraFieldsDto fields = new JiraFieldsDto(
                "Summary of " + key, null, null, null,
                new JiraStatusDto(status), null, null,
                "2025-05-01", null, null, null
        );
        return new JiraIssueDto(key, "https://jira/browse/" + key, fields);
    }

    private JiraIssueDto issueWithAssignee(String key, String status, String assigneeName) {
        return issueWithAssignee(key, status, assigneeName, null);
    }

    private JiraIssueDto issueWithAssignee(String key, String status, String assigneeName, String priority) {
        JiraUserDto assignee = new JiraUserDto(assigneeName, assigneeName + "@test.com", "acc-" + assigneeName);
        JiraPriorityDto priorityDto = priority != null ? new JiraPriorityDto(priority) : null;
        JiraFieldsDto fields = new JiraFieldsDto(
                "Summary of " + key, "2025-05-25", assignee, null,
                new JiraStatusDto(status), null, null,
                "2025-05-01", null, null, priorityDto
        );
        return new JiraIssueDto(key, "https://jira/browse/" + key, fields);
    }

    private JiraIssueDto subtask(String key, String parentKey, String phaseLabel, String assigneeName) {
        JiraUserDto assignee = new JiraUserDto(assigneeName, assigneeName + "@test.com", "acc-" + assigneeName);
        JiraIssueRefDto parent = new JiraIssueRefDto(parentKey, "id-" + parentKey);
        JiraFieldsDto fields = new JiraFieldsDto(
                phaseLabel, "2025-05-25", assignee, null,
                new JiraStatusDto("In Progress"), null, null,
                "2025-05-01", List.of(phaseLabel), parent, null
        );
        return new JiraIssueDto(key, "https://jira/browse/" + key, fields);
    }

    private JiraSearchResponse page(List<JiraIssueDto> issues, boolean isLast) {
        return new JiraSearchResponse(issues, issues.size(), isLast, null);
    }


}
