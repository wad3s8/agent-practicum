package com.example.agent.dto.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraCommentsDto(List<JiraCommentDto> comments, Integer total) {
}
