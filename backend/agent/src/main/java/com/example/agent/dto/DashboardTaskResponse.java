package com.example.agent.dto;

import java.util.List;

public record DashboardTaskResponse(
        String jiraIssueKey,
        /** Роль создателя задачи (из Jira) */
        String initiatorRole,
        /** Имя создателя задачи */
        String initiatorName,
        String taskName,
        /** Highest | High | Medium | Low | Lowest | null */
        String priority,
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
