package com.example.agent.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "acknowledged_events",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "jira_issue_key"}))
@Getter
@Setter
@NoArgsConstructor
public class AcknowledgedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "jira_issue_key", nullable = false)
    private String jiraIssueKey;

    private Instant acknowledgedAt;

    public AcknowledgedEvent(User user, String jiraIssueKey) {
        this.user = user;
        this.jiraIssueKey = jiraIssueKey;
        this.acknowledgedAt = Instant.now();
    }
}
