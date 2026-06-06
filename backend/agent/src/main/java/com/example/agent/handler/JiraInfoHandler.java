package com.example.agent.handler;

import com.example.agent.client.JiraClient;
import com.example.agent.dto.jira.JiraIssueDto;
import com.example.agent.dto.jira.JiraSearchRequest;
import com.example.agent.entity.Message;
import com.example.agent.entity.SenderType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JiraInfoHandler {

    private final ChatClient chatClient;
    private final JiraClient jiraClient;

    private static final List<String> JIRA_FIELDS =
            List.of("summary", "assignee", "reporter", "status", "duedate", "project");

    private static final String JQL_SYSTEM_PROMPT = """
            Ты — эксперт по Jira Query Language (JQL). Переведи запрос пользователя в JQL-строку.
            Ответь ТОЛЬКО JQL-строкой, без объяснений, без кавычек, без markdown.
            Сегодняшняя дата: %s
            Примеры:
            - "открытые задачи в проекте ABC" → project = ABC AND statusCategory != Done
            - "просроченные задачи" → duedate < now() AND statusCategory != Done
            - "задачи Ивана" → assignee = "Ivan"
            """;

    private static final String FORMAT_SYSTEM_PROMPT = """
            Ты — ассистент по задачам Jira. Представь полученные данные о задачах пользователю в удобном формате.
            Используй Markdown. Отвечай на русском языке. Будь кратким и информативным.
            """;

    public String handle(String userText, List<Message> history) {
        String jql = generateJql(userText);
        log.debug("Generated JQL: {}", jql);

        List<JiraIssueDto> issues = fetchIssues(jql);
        if (issues == null) {
            return "Ошибка при обращении к Jira. Попробуйте позже.";
        }
        if (issues.isEmpty()) {
            return "По вашему запросу задачи не найдены.";
        }

        return formatWithLLM(userText, issues, history);
    }

    private String generateJql(String userText) {
        String today = LocalDate.now().toString();
        try {
            return chatClient.prompt()
                    .system(String.format(JQL_SYSTEM_PROMPT, today))
                    .user(userText)
                    .call()
                    .content()
                    .trim();
        } catch (Exception e) {
            log.error("JQL generation failed: {}", e.getMessage());
            return "order by created DESC";
        }
    }

    private List<JiraIssueDto> fetchIssues(String jql) {
        try {
            JiraSearchRequest request = new JiraSearchRequest(jql, 20, JIRA_FIELDS);
            List<JiraIssueDto> issues = jiraClient.search(request).issues();
            return issues != null ? issues : List.of();
        } catch (Exception e) {
            log.error("Jira search failed for JQL '{}': {}", jql, e.getMessage());
            return null;
        }
    }

    private String formatWithLLM(String userText, List<JiraIssueDto> issues, List<Message> history) {
        String issuesText = buildIssuesSummary(issues);

        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(FORMAT_SYSTEM_PROMPT));
        history.forEach(msg -> messages.add(
                msg.getSender() == SenderType.USER
                        ? new UserMessage(msg.getText())
                        : new AssistantMessage(msg.getText())
        ));
        messages.add(new UserMessage("Запрос пользователя: " + userText + "\n\nДанные из Jira:\n" + issuesText));

        return chatClient.prompt()
                .messages(messages)
                .call()
                .content();
    }

    private String buildIssuesSummary(List<JiraIssueDto> issues) {
        StringBuilder sb = new StringBuilder();
        for (JiraIssueDto issue : issues) {
            sb.append("Ключ: ").append(issue.key()).append("\n");
            sb.append("Название: ").append(issue.fields().summary()).append("\n");
            if (issue.fields().status() != null) {
                sb.append("Статус: ").append(issue.fields().status().name()).append("\n");
            }
            if (issue.fields().assignee() != null) {
                sb.append("Исполнитель: ").append(issue.fields().assignee().displayName()).append("\n");
            }
            if (issue.fields().reporter() != null) {
                sb.append("Автор: ").append(issue.fields().reporter().displayName()).append("\n");
            }
            if (issue.fields().duedate() != null) {
                sb.append("Дедлайн: ").append(issue.fields().duedate()).append("\n");
            }
            if (issue.fields().project() != null) {
                sb.append("Проект: ").append(issue.fields().project().name()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
