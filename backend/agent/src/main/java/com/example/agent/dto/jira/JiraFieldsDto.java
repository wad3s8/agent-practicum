package com.example.agent.dto.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraFieldsDto(
        String summary,
        String duedate,
        JiraUserDto assignee,
        JiraUserDto reporter,
        JiraStatusDto status,
        JiraProjectDto project,
        JiraCommentsDto comment,
        /** Стандартное поле "Start date" в Jira Cloud (customfield_10015) */
        @JsonProperty("customfield_10015") String startDate
) {
}
