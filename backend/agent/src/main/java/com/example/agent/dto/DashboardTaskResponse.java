package com.example.agent.dto;

import java.util.List;

public record DashboardTaskResponse(
        String jiraIssueKey,
        /** Роль создателя задачи (из Jira) */
        String initiatorRole,
        /** Имя создателя задачи */
        String initiatorName,
        String taskName,
        /** EASY | HARD */
        String complexity,
        /** true — сложность выставлена вручную, false — оценил ИИ */
        boolean complexityOverridden,
        String startDate,
        String dueDate,
        /** true если дедлайн просрочен и задача не закрыта */
        boolean deadlineOverdue,
        List<String> assignees,
        String jiraUrl,
        /** OPEN | CLOSED */
        String status
) {
}
