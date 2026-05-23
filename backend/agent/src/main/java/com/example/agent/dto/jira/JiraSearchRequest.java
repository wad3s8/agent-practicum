package com.example.agent.dto.jira;

import java.util.List;

public record JiraSearchRequest(
        String jql,
        int maxResults,
        String nextPageToken,
        List<String> fields
) {
    public JiraSearchRequest(String jql, int maxResults, List<String> fields) {
        this(jql, maxResults, null, fields);
    }
}
