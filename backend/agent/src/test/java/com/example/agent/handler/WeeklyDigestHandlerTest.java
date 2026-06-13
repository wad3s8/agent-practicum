package com.example.agent.handler;

import com.example.agent.dto.DashboardStatsResponse;
import com.example.agent.dto.DashboardTaskResponse;
import com.example.agent.entity.Message;
import com.example.agent.entity.SenderType;
import com.example.agent.entity.SignificantEvent;
import com.example.agent.repository.SignificantEventRepository;
import com.example.agent.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WeeklyDigestHandlerTest {

    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec spec;
    @Mock ChatClient.CallResponseSpec callSpec;
    @Mock DashboardService dashboardService;
    @Mock SignificantEventRepository eventRepository;
    @Captor ArgumentCaptor<List<org.springframework.ai.chat.messages.Message>> messagesCaptor;

    WeeklyDigestHandler handler;

    private static final DashboardStatsResponse STATS =
            new DashboardStatsResponse(3, 1, 5, 2, 2, 1);

    @BeforeEach
    void setUp() {
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.messages(any(List.class))).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        handler = new WeeklyDigestHandler(chatClient, dashboardService, eventRepository);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void handle_withTasksAndEvents_returnsFormattedDigest() {
        when(callSpec.content())
                .thenReturn("NULL")                // team key extraction → no specific team
                .thenReturn("## Дайджест за неделю...");

        when(dashboardService.getStats(isNull(), any(LocalDate.class))).thenReturn(STATS);
        when(dashboardService.getTasks(isNull(), any(LocalDate.class))).thenReturn(List.of(
                closedTask("PDM-1", "Задача 1", "Иван"),
                openTask("PDM-2", "Задача 2", "Мария", false)
        ));
        when(eventRepository.findAll()).thenReturn(List.of());

        String result = handler.handle("Что мы сделали за неделю?", List.of());

        assertThat(result).isEqualTo("## Дайджест за неделю...");
        verify(dashboardService).getStats(isNull(), any(LocalDate.class));
        verify(dashboardService).getTasks(isNull(), any(LocalDate.class));
    }

    @Test
    void handle_withExplicitTeamKey_passesItToDashboard() {
        when(callSpec.content())
                .thenReturn("PDM")                 // team key extraction
                .thenReturn("## Дайджест PDM...");

        when(dashboardService.getStats(eq("PDM"), any(LocalDate.class))).thenReturn(STATS);
        when(dashboardService.getTasks(eq("PDM"), any(LocalDate.class))).thenReturn(List.of());
        when(eventRepository.findByTeamKeyIn(List.of("PDM"))).thenReturn(List.of());

        String result = handler.handle("Дайджест по проекту PDM", List.of());

        assertThat(result).contains("Дайджест PDM");
        verify(dashboardService).getStats(eq("PDM"), any(LocalDate.class));
        verify(eventRepository).findByTeamKeyIn(List.of("PDM"));
    }

    @Test
    void handle_withCriticalEvents_includesThemInLlmPrompt() {
        when(callSpec.content())
                .thenReturn("NULL")
                .thenReturn("Дайджест с проблемами");

        when(dashboardService.getStats(any(), any())).thenReturn(STATS);
        when(dashboardService.getTasks(any(), any())).thenReturn(List.of());

        SignificantEvent redEvent = buildEvent("PDM-99", "RED", "Сервер упал", "PDM", "Команда");
        when(eventRepository.findAll()).thenReturn(List.of(redEvent));

        handler.handle("Итоги недели", List.of());

        verify(spec).messages(messagesCaptor.capture());
        String userMsg = messagesCaptor.getValue().getLast().getText();
        assertThat(userMsg).contains("PDM-99").contains("RED").contains("Сервер упал");
    }

    @Test
    void handle_overdueTask_markedInLlmPrompt() {
        when(callSpec.content())
                .thenReturn("NULL")
                .thenReturn("Дайджест");

        when(dashboardService.getStats(any(), any())).thenReturn(STATS);
        when(dashboardService.getTasks(any(), any())).thenReturn(List.of(
                openTask("PDM-5", "Просроченная задача", "Кто-то", true)
        ));
        when(eventRepository.findAll()).thenReturn(List.of());

        handler.handle("Итоги", List.of());

        verify(spec).messages(messagesCaptor.capture());
        String userMsg = messagesCaptor.getValue().getLast().getText();
        assertThat(userMsg).contains("ПРОСРОЧЕНО");
    }

    @Test
    void handle_withConversationHistory_passesItToLlm() {
        when(callSpec.content())
                .thenReturn("NULL")
                .thenReturn("Дайджест");

        when(dashboardService.getStats(any(), any())).thenReturn(STATS);
        when(dashboardService.getTasks(any(), any())).thenReturn(List.of());
        when(eventRepository.findAll()).thenReturn(List.of());

        Message prevUser = message("Покажи дайджест", SenderType.USER);
        Message prevAi = message("Вот итоги прошлой недели...", SenderType.SYSTEM);

        handler.handle("А на этой неделе?", List.of(prevUser, prevAi));

        // system + 2 history + current user message = 4
        verify(spec).messages(messagesCaptor.capture());
        assertThat(messagesCaptor.getValue()).hasSize(4);
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    void handle_dashboardServiceThrows_returnsErrorMessage() {
        when(callSpec.content()).thenReturn("NULL");
        when(dashboardService.getStats(any(), any())).thenThrow(new RuntimeException("Jira unavailable"));

        String result = handler.handle("Дайджест за неделю", List.of());

        assertThat(result).containsIgnoringCase("не удалось");
        verify(chatClient, times(1)).prompt(); // only team key extraction, no formatting call
    }

    @Test
    void handle_eventRepositoryThrows_stillReturnsDigest() {
        when(callSpec.content())
                .thenReturn("NULL")
                .thenReturn("## Дайджест без событий");

        when(dashboardService.getStats(any(), any())).thenReturn(STATS);
        when(dashboardService.getTasks(any(), any())).thenReturn(List.of());
        when(eventRepository.findAll()).thenThrow(new RuntimeException("DB error"));

        String result = handler.handle("Дайджест", List.of());

        assertThat(result).isEqualTo("## Дайджест без событий");
    }

    @Test
    void handle_teamKeyExtractionThrows_usesNullAndContinues() {
        when(chatClient.prompt())
                .thenReturn(spec); // first call (team extraction) also stubbed via spec
        when(callSpec.content())
                .thenThrow(new RuntimeException("AI error")) // team key call fails
                .thenReturn("## Дайджест");

        when(dashboardService.getStats(isNull(), any())).thenReturn(STATS);
        when(dashboardService.getTasks(isNull(), any())).thenReturn(List.of());
        when(eventRepository.findAll()).thenReturn(List.of());

        String result = handler.handle("Итоги недели", List.of());

        assertThat(result).isEqualTo("## Дайджест");
        verify(dashboardService).getStats(isNull(), any());
    }

    // ── Stats formatting ──────────────────────────────────────────────────────

    @Test
    void handle_statsIncludedInLlmPrompt() {
        when(callSpec.content()).thenReturn("NULL").thenReturn("ok");

        DashboardStatsResponse stats = new DashboardStatsResponse(4, 2, 10, 3, 6, -1);
        when(dashboardService.getStats(any(), any())).thenReturn(stats);
        when(dashboardService.getTasks(any(), any())).thenReturn(List.of());
        when(eventRepository.findAll()).thenReturn(List.of());

        handler.handle("Итоги", List.of());

        verify(spec).messages(messagesCaptor.capture());
        String userMsg = messagesCaptor.getValue().getLast().getText();
        assertThat(userMsg)
                .contains("В работе: 4")
                .contains("+2")
                .contains("Завершено: 6")
                .contains("-1");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DashboardTaskResponse closedTask(String key, String name, String assignee) {
        return new DashboardTaskResponse(key, "", "", name, "EASY", false,
                null, null, false, List.of(assignee), "https://jira/" + key, "CLOSED");
    }

    private DashboardTaskResponse openTask(String key, String name, String assignee, boolean overdue) {
        return new DashboardTaskResponse(key, "", "", name, "HARD", false,
                null, overdue ? "01.06" : null, overdue, List.of(assignee), "https://jira/" + key, "OPEN");
    }

    private SignificantEvent buildEvent(String key, String priority, String problem,
                                        String teamKey, String teamName) {
        SignificantEvent e = new SignificantEvent();
        e.setJiraIssueKey(key);
        e.setPriority(priority);
        e.setTaskName("Задача " + key);
        e.setProblem(problem);
        e.setTeamKey(teamKey);
        e.setTeamName(teamName);
        return e;
    }

    private Message message(String text, SenderType sender) {
        Message m = new Message();
        m.setText(text);
        m.setSender(sender);
        return m;
    }
}
