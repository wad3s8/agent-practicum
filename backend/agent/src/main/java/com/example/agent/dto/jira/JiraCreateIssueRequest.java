package com.example.agent.dto.jira;

public record JiraCreateIssueRequest(Fields fields) {

    public record Fields(
            Project project,
            String summary,
            Issuetype issuetype,
            Assignee assignee
    ) {}

    public record Project(String key) {}
    public record Issuetype(String name) {}
    public record Assignee(String accountId) {}
}
