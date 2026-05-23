package com.example.agent.service;

import com.example.agent.client.JiraClient;
import com.example.agent.dto.SignificantEventResponse;
import com.example.agent.dto.TeamResponse;
import com.example.agent.entity.AcknowledgedEvent;
import com.example.agent.entity.SignificantEvent;
import com.example.agent.entity.User;
import com.example.agent.repository.AcknowledgedEventRepository;
import com.example.agent.repository.SignificantEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SignificantEventsService {

    private final JiraClient jiraClient;
    private final AcknowledgedEventRepository acknowledgedEventRepository;
    private final SignificantEventRepository significantEventRepository;

    // ── Teams — берём напрямую из Jira ───────────────────────────────────────

    public List<TeamResponse> getTeams() {
        return jiraClient.getProjects().stream()
                .map(p -> new TeamResponse(p.key(), p.name()))
                .toList();
    }

    // ── Events (читаем из БД, куда поллинг уже всё положил) ──────────────────

    @Transactional(readOnly = true)
    public List<SignificantEventResponse> getEvents(User user, String teamKey, LocalDate weekStart) {
        List<SignificantEvent> dbEvents = teamKey != null
                ? significantEventRepository.findByTeamKeyIn(List.of(teamKey))
                : significantEventRepository.findAll();

        if (weekStart != null) {
            LocalDate monday = weekStart.with(java.time.DayOfWeek.MONDAY);
            LocalDate sunday = monday.plusDays(6);
            dbEvents = dbEvents.stream()
                    .filter(e -> {
                        Instant ref = e.getLastUpdatedAt() != null ? e.getLastUpdatedAt() : e.getDetectedAt();
                        if (ref == null) return false;
                        LocalDate day = ref.atZone(java.time.ZoneOffset.UTC).toLocalDate();
                        return !day.isBefore(monday) && !day.isAfter(sunday);
                    })
                    .toList();
        }

        Map<String, AcknowledgedEvent> ackMap = acknowledgedEventRepository.findByUser(user)
                .stream()
                .collect(Collectors.toMap(AcknowledgedEvent::getJiraIssueKey, e -> e));

        return dbEvents.stream()
                .map(e -> toResponse(e, ackMap))
                .sorted(Comparator
                        .comparingInt((SignificantEventResponse e) -> e.acknowledged() ? 1 : 0)
                        .thenComparingInt(e -> priorityOrder(e.priority())))
                .toList();
    }

    // ── Acknowledge toggle ────────────────────────────────────────────────────

    @Transactional
    public SignificantEventResponse toggleAcknowledge(User user, String issueKey) {
        SignificantEvent event = significantEventRepository.findById(issueKey)
                .orElseThrow(() -> new NoSuchElementException("Significant event not found: " + issueKey));

        acknowledgedEventRepository.findByUserAndJiraIssueKey(user, issueKey)
                .ifPresentOrElse(
                        acknowledgedEventRepository::delete,
                        () -> acknowledgedEventRepository.save(new AcknowledgedEvent(user, issueKey))
                );

        Map<String, AcknowledgedEvent> ackMap = acknowledgedEventRepository.findByUser(user)
                .stream()
                .collect(Collectors.toMap(AcknowledgedEvent::getJiraIssueKey, e -> e));

        return toResponse(event, ackMap);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private SignificantEventResponse toResponse(SignificantEvent e, Map<String, AcknowledgedEvent> ackMap) {
        AcknowledgedEvent ack = ackMap.get(e.getJiraIssueKey());
        return new SignificantEventResponse(
                e.getJiraIssueKey(),
                e.getPriority(),
                e.getTaskName(),
                e.getTeamName(),
                e.getTeamKey(),
                e.getAssignees(),
                e.getProblem(),
                e.getJiraUrl(),
                ack != null,
                ack != null ? ack.getAcknowledgedAt() : null
        );
    }

    private int priorityOrder(String priority) {
        return switch (priority) {
            case "RED" -> 0;
            case "YELLOW" -> 1;
            default -> 2;
        };
    }
}
