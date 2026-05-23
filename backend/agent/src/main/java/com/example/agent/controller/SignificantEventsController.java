package com.example.agent.controller;

import com.example.agent.dto.SignificantEventResponse;
import com.example.agent.dto.TeamResponse;
import com.example.agent.security.UserDetailsAdapter;
import com.example.agent.service.SignificantEventsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SignificantEventsController {

    private final SignificantEventsService eventsService;

    /**
     * GET /api/teams
     * Возвращает все команды (Jira-проекты), доступные в Jira.
     */
    @GetMapping("/teams")
    public List<TeamResponse> getTeams() {
        return eventsService.getTeams();
    }

    /**
     * GET /api/events?teamKey=PDM&weekStart=2025-05-19
     */
    @GetMapping("/events")
    public List<SignificantEventResponse> getEvents(
            @AuthenticationPrincipal UserDetailsAdapter currentUser,
            @RequestParam(required = false) String teamKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {
        return eventsService.getEvents(currentUser.getUser(), teamKey, weekStart);
    }

    /**
     * PATCH /api/events/{issueKey}/acknowledge
     * Переключает состояние чекбокса (отмечено / не отмечено).
     */
    @PatchMapping("/events/{issueKey}/acknowledge")
    public SignificantEventResponse toggleAcknowledge(
            @PathVariable String issueKey,
            @AuthenticationPrincipal UserDetailsAdapter currentUser
    ) {
        return eventsService.toggleAcknowledge(currentUser.getUser(), issueKey);
    }
}
