package com.example.agent.handler;

import com.example.agent.client.JiraClient;
import com.example.agent.dto.jira.JiraCreateIssueRequest;
import com.example.agent.dto.jira.JiraCreateIssueResponse;
import com.example.agent.dto.jira.JiraProjectDto;
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
            Извлеки из сообщения пользователя параметры для создания задачи в Jira.
            Ответь ТОЛЬКО валидным JSON без markdown-блоков, строго в формате:
            {"taskTitle":"Название задачи","projectKey":"PROJ","assigneeName":"Иван Петров"}
            Правила:
            - taskTitle: название задачи из сообщения пользователя, если не указано — придумай краткое
            - projectKey: ключ проекта Jira (обычно заглавные буквы, например "PROJ"), если не упомянут — null
            - assigneeName: имя исполнителя, если не указан — null
            """;

    public String handle(String userText, List<Message> history) {
        TaskExtractionResult extraction = extractFromMessage(userText);
        if (extraction == null) {
            return "Не удалось разобрать запрос. Пример: «Создай задачу Настроить CI для Ивана Петрова в проекте PROJ»";
        }
        if (extraction.taskTitle() == null) {
            return "Не удалось определить название задачи. Уточните, например: «Создай задачу Настроить CI»";
        }

        String projectKey = resolveProjectKey(extraction.projectKey());
        if (projectKey == null) {
            return "Не удалось определить проект. Укажите ключ проекта, например: «в проекте PROJ»";
        }

        String accountId = null;
        String assigneeDisplay = null;
        if (extraction.assigneeName() != null) {
            JiraUserDto user = findUser(extraction.assigneeName());
            if (user == null) {
                return "Пользователь **" + extraction.assigneeName() + "** не найден в Jira. Проверьте имя и попробуйте снова.";
            }
            accountId = user.accountId();
            assigneeDisplay = user.displayName();
        }

        JiraCreateIssueRequest.Assignee assignee = accountId != null
                ? new JiraCreateIssueRequest.Assignee(accountId)
                : null;

        JiraCreateIssueRequest request = new JiraCreateIssueRequest(
                new JiraCreateIssueRequest.Fields(
                        new JiraCreateIssueRequest.Project(projectKey),
                        extraction.taskTitle(),
                        new JiraCreateIssueRequest.Issuetype("Task"),
                        assignee
                )
        );

        try {
            JiraCreateIssueResponse response = jiraClient.createIssue(request);
            if (assigneeDisplay != null) {
                return String.format("Задача **%s** («%s») успешно создана и назначена на **%s**.",
                        response.key(), extraction.taskTitle(), assigneeDisplay);
            } else {
                return String.format("Задача **%s** («%s») успешно создана в проекте **%s**.",
                        response.key(), extraction.taskTitle(), projectKey);
            }
        } catch (Exception e) {
            log.error("Jira createIssue failed: {}", e.getMessage());
            return "Ошибка при создании задачи в Jira: " + e.getMessage();
        }
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

    private String resolveProjectKey(String projectKey) {
        if (projectKey != null) return projectKey;
        try {
            List<JiraProjectDto> projects = jiraClient.getProjects();
            if (projects != null && !projects.isEmpty()) {
                return projects.get(0).key();
            }
        } catch (Exception e) {
            log.error("Failed to fetch Jira projects: {}", e.getMessage());
        }
        return null;
    }

    private JiraUserDto findUser(String name) {
        try {
            List<JiraUserDto> users = jiraClient.searchUsers(name);
            return (users != null && !users.isEmpty()) ? users.get(0) : null;
        } catch (Exception e) {
            log.error("Jira user search failed: {}", e.getMessage());
            return null;
        }
    }
}
