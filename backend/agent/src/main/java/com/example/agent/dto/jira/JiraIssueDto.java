package com.example.agent.dto.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraIssueDto(String key, String self, JiraFieldsDto fields) {
}
