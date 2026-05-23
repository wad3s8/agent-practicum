package com.example.agent.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "significant_events")
@Getter
@Setter
@NoArgsConstructor
public class SignificantEvent {

    /** Jira issue key используется как PK (например, PROJ-123). */
    @Id
    @Column(name = "jira_issue_key")
    private String jiraIssueKey;

    /** RED | YELLOW | GREEN */
    @Column(nullable = false)
    private String priority;

    @Column(nullable = false)
    private String taskName;

    @Column(nullable = false)
    private String teamKey;

    private String teamName;

    /** Имена исполнителей, разделённые символом | */
    @Column(name = "assignees_raw")
    private String assigneesRaw;

    private String problem;

    private String jiraUrl;

    private Instant detectedAt;

    private Instant lastUpdatedAt;

    public List<String> getAssignees() {
        if (assigneesRaw == null || assigneesRaw.isBlank()) return List.of();
        return Arrays.asList(assigneesRaw.split("\\|"));
    }

    public void setAssignees(List<String> assignees) {
        this.assigneesRaw = assignees == null ? "" : String.join("|", assignees);
    }
}
