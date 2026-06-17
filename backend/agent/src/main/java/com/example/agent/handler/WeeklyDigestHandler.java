package com.example.agent.handler;

import com.example.agent.dto.DashboardStatsResponse;
import com.example.agent.dto.DashboardTaskResponse;
import com.example.agent.entity.Message;
import com.example.agent.entity.SenderType;
import com.example.agent.entity.SignificantEvent;
import com.example.agent.repository.SignificantEventRepository;
import com.example.agent.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyDigestHandler {

    private final ChatClient chatClient;
    private final DashboardService dashboardService;
    private final SignificantEventRepository significantEventRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter DATE_FMT_FULL = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final String TEAM_EXTRACT_PROMPT = """
            Из запроса пользователя извлеки ключ проекта/команды в Jira (например: PDM, BACKEND, FRONTEND).
            Если ключ не указан явно — ответь словом NULL.
            Ответь ТОЛЬКО ключом проекта или NULL, без объяснений.
            """;

    private static final String DIGEST_SYSTEM_PROMPT = """
            Ты — аналитик команды разработки. Составь дайджест за неделю на основе данных из Jira.
            Используй Markdown-форматирование. Отвечай на русском языке. Будь конкретным и информативным.

            Структура дайджеста:
            ## Дайджест за неделю %s – %s

            ### Итоги
            Кратко: что было сделано, что в работе, что не успели.

            ### Статистика
            Таблица или список с ключевыми цифрами и динамикой относительно прошлой недели.

            ### Выполненные задачи
            Список закрытых задач с исполнителями.

            ### В работе
            Список активных задач, отметь просроченные.

            ### Критические события
            Список проблем (просроченные, горящие дедлайны, блокеры). Если проблем нет — напиши "Критических событий нет".

            ### Вывод
            Краткая оценка недели: удалось ли выполнить план, что требует внимания на следующей неделе.
            """;

    public String handle(String userText, List<Message> history) {
        String teamKey = extractTeamKey(userText);
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);

        DashboardStatsResponse stats;
        List<DashboardTaskResponse> tasks;
        try {
            stats = dashboardService.getStats(teamKey, weekStart);
            tasks = dashboardService.getTasks(teamKey, weekStart, weekStart.plusDays(6));
        } catch (Exception e) {
            log.error("Failed to fetch dashboard data: {}", e.getMessage());
            return "Не удалось получить данные из Jira. Попробуйте позже.";
        }

        List<SignificantEvent> events;
        try {
            events = teamKey != null
                    ? significantEventRepository.findByTeamKeyIn(List.of(teamKey))
                    : significantEventRepository.findAll();
        } catch (Exception e) {
            log.warn("Failed to fetch significant events: {}", e.getMessage());
            events = List.of();
        }

        String dataSummary = buildDataSummary(stats, tasks, events);
        return formatWithLLM(userText, dataSummary, history, weekStart);
    }

    private String extractTeamKey(String userText) {
        try {
            String result = chatClient.prompt()
                    .system(TEAM_EXTRACT_PROMPT)
                    .user(userText)
                    .call()
                    .content()
                    .trim()
                    .toUpperCase();
            return "NULL".equals(result) || result.isBlank() ? null : result;
        } catch (Exception e) {
            log.warn("Team key extraction failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildDataSummary(DashboardStatsResponse stats,
                                    List<DashboardTaskResponse> tasks,
                                    List<SignificantEvent> events) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== СТАТИСТИКА ===\n");
        sb.append("Всего задач: ").append(stats.backlogCount())
                .append(" (").append(delta(stats.backlogDelta())).append(" vs прошлая неделя)\n");
        sb.append("В работе: ").append(stats.inWorkCount())
                .append(" (").append(delta(stats.inWorkDelta())).append(")\n");
        sb.append("Завершено: ").append(stats.doneCount())
                .append(" (").append(delta(stats.doneDelta())).append(")\n\n");

        List<DashboardTaskResponse> done = tasks.stream()
                .filter(t -> "CLOSED".equals(t.status())).toList();
        List<DashboardTaskResponse> open = tasks.stream()
                .filter(t -> "OPEN".equals(t.status())).toList();

        sb.append("=== ВЫПОЛНЕННЫЕ ЗАДАЧИ (").append(done.size()).append(") ===\n");
        for (DashboardTaskResponse t : done) {
            sb.append("- [").append(t.jiraIssueKey()).append("] ").append(t.taskName());
            if (!t.assignees().isEmpty()) sb.append(" → ").append(String.join(", ", t.assignees()));
            sb.append("\n");
        }

        sb.append("\n=== В РАБОТЕ (").append(open.size()).append(") ===\n");
        for (DashboardTaskResponse t : open) {
            sb.append("- [").append(t.jiraIssueKey()).append("] ").append(t.taskName());
            if (!t.assignees().isEmpty()) sb.append(" → ").append(String.join(", ", t.assignees()));
            if (t.deadlineOverdue()) sb.append(" [ПРОСРОЧЕНО]");
            if (t.dueDate() != null) sb.append(" (дедлайн: ").append(t.dueDate()).append(")");
            sb.append("\n");
        }

        if (!events.isEmpty()) {
            sb.append("\n=== КРИТИЧЕСКИЕ СОБЫТИЯ (").append(events.size()).append(") ===\n");
            for (SignificantEvent e : events) {
                sb.append("[").append(e.getPriority()).append("] ")
                        .append(e.getTaskName()).append(": ").append(e.getProblem()).append("\n");
                if (!e.getAssignees().isEmpty()) {
                    sb.append("  Исполнители: ").append(String.join(", ", e.getAssignees())).append("\n");
                }
            }
        } else {
            sb.append("\n=== КРИТИЧЕСКИЕ СОБЫТИЯ ===\nКритических событий нет.\n");
        }

        return sb.toString();
    }

    private String delta(int d) {
        if (d > 0) return "+" + d;
        if (d < 0) return String.valueOf(d);
        return "±0";
    }

    private String formatWithLLM(String userText, String dataSummary,
                                  List<Message> history, LocalDate weekStart) {
        LocalDate sunday = weekStart.plusDays(6);
        String systemPrompt = String.format(DIGEST_SYSTEM_PROMPT,
                weekStart.format(DATE_FMT), sunday.format(DATE_FMT_FULL));

        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        history.forEach(msg -> messages.add(
                msg.getSender() == SenderType.USER
                        ? new UserMessage(msg.getText())
                        : new AssistantMessage(msg.getText())
        ));
        messages.add(new UserMessage("Запрос: " + userText + "\n\nДанные из Jira:\n" + dataSummary));

        return chatClient.prompt()
                .messages(messages)
                .call()
                .content();
    }
}
