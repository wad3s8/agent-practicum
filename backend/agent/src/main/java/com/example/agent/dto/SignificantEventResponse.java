package com.example.agent.dto;

import java.time.Instant;
import java.util.List;

public record SignificantEventResponse(
        String jiraIssueKey,
        /** RED | YELLOW | GREEN */
        String priority,
        String taskName,
        String teamName,
        String teamKey,
        List<String> assignees,
        String problem,
        String jiraUrl,
        boolean acknowledged,
        Instant acknowledgedAt
) {
}
