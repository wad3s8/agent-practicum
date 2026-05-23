package com.example.agent.dto.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraUserDto(String displayName, String emailAddress, String accountId) {
}
