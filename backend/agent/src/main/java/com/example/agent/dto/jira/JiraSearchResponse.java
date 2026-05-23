package com.example.agent.dto.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraSearchResponse(
        List<JiraIssueDto> issues,
        Integer total,
        @JsonProperty("isLast") Boolean isLast,
        String nextPageToken
) {
}
