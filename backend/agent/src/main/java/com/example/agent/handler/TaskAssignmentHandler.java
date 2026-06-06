package com.example.agent.handler;

import com.example.agent.client.JiraClient;
import com.example.agent.dto.jira.JiraAssigneeRequest;
import com.example.agent.dto.jira.JiraUserDto;
import com.example.agent.entity.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskAssignmentHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatClient chatClient;
    private final JiraClient jiraClient;

    private static final String EXTRACT_PROMPT = """
            Извлеки из сообщения пользователя ключ задачи Jira и имя исполнителя.
            Ответь ТОЛЬКО валидным JSON без markdown-блоков, строго в формате:
            {"issueKey":"PROJ-123","assigneeName":"Иван Петров"}
            Если ключ задачи или имя не найдены — используй null для соответствующего поля.
            """;

    public String handle(String userText, List<Message> history) {
        TaskExtractionResult extraction = extractFromMessage(userText);

        if (extraction == null) {
            return "Не удалось разобрать запрос. Попробуйте уточнить.\nПример: «Назначь задачу PROJ-123 на Ивана Петрова»";
        }
        if (extraction.issueKey() == null) {
            return "Не удалось определить ключ задачи. Укажите его явно, например: PROJ-123";
        }
        if (extraction.assigneeName() == null) {
            return "Не удалось определить имя исполнителя. Уточните имя, например: «назначь на Ивана Петрова»";
        }

        List<JiraUserDto> users;
        try {
            users = jiraClient.searchUsers(extraction.assigneeName());
        } catch (Exception e) {
            log.error("Jira user search failed: {}", e.getMessage());
            return "Ошибка при поиске пользователя в Jira: " + e.getMessage();
        }

        if (users == null || users.isEmpty()) {
            return "Пользователь **" + extraction.assigneeName() + "** не найден в Jira. Проверьте имя и попробуйте снова.";
        }

        JiraUserDto assignee = users.get(0);

        try {
            jiraClient.assignIssue(extraction.issueKey(), new JiraAssigneeRequest(assignee.accountId()));
        } catch (Exception e) {
            log.error("Jira assign failed for issue {}: {}", extraction.issueKey(), e.getMessage());
            return "Ошибка при назначении задачи **" + extraction.issueKey() + "**: " + e.getMessage();
        }

        return String.format(
                "Задача **%s** успешно назначена на **%s** (%s).",
                extraction.issueKey(),
                assignee.displayName(),
                assignee.emailAddress() != null ? assignee.emailAddress() : "email не указан"
        );
    }

    private TaskExtractionResult extractFromMessage(String userText) {
        try {
            String json = chatClient.prompt()
                    .system(EXTRACT_PROMPT)
                    .user(userText)
                    .call()
                    .content()
                    .trim();
            return OBJECT_MAPPER.readValue(json, TaskExtractionResult.class);
        } catch (Exception e) {
            log.error("Task extraction failed: {}", e.getMessage());
            return null;
        }
    }
}
