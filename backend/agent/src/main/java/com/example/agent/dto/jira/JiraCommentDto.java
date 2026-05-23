package com.example.agent.dto.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraCommentDto(JiraUserDto author, JsonNode body, String created) {
}
