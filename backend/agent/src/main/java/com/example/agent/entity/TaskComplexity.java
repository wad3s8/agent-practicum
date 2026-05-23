package com.example.agent.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "task_complexity")
@Getter
@Setter
@NoArgsConstructor
public class TaskComplexity {

    @Id
    @Column(name = "jira_issue_key")
    private String jiraIssueKey;

    /** EASY | HARD */
    @Column(nullable = false)
    private String complexity;

    private Instant overriddenAt;

    public TaskComplexity(String jiraIssueKey, String complexity) {
        this.jiraIssueKey = jiraIssueKey;
        this.complexity = complexity;
        this.overriddenAt = Instant.now();
    }
}
