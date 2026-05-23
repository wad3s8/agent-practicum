package com.example.agent.dto.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraFieldsDto(
        String summary,
        String duedate,
        JiraUserDto assignee,
        JiraStatusDto status,
        JiraProjectDto project,
        JiraCommentsDto comment
) {
}
