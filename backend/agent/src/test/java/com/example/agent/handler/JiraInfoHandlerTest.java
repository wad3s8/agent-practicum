package com.example.agent.handler;

import com.example.agent.client.JiraClient;
import com.example.agent.dto.jira.JiraFieldsDto;
import com.example.agent.dto.jira.JiraIssueDto;
import com.example.agent.dto.jira.JiraSearchRequest;
import com.example.agent.dto.jira.JiraSearchResponse;
import com.example.agent.dto.jira.JiraStatusDto;
import com.example.agent.dto.jira.JiraUserDto;
import com.example.agent.entity.Message;
import com.example.agent.entity.SenderType;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JiraInfoHandlerTest {

    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec spec;
    @Mock ChatClient.CallResponseSpec callSpec;
    @Mock JiraClient jiraClient;
    @Captor ArgumentCaptor<List<org.springframework.ai.chat.messages.Message>> messagesCaptor;

    JiraInfoHandler handler;

    @BeforeEach
    void setUp() {
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.messages(any(List.class))).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        handler = new JiraInfoHandler(chatClient, jiraClient);
    }

    @Test
    void handle_issuesFound_returnsFormattedResponse() {
        when(callSpec.content())
                .thenReturn("project = TEST AND statusCategory != Done")  // JQL generation
                .thenReturn("Найдено 2 задачи: TEST-1 и TEST-2");         // formatting

        JiraIssueDto issue1 = buildIssue("TEST-1", "Задача 1", "In Progress", "Иван");
        JiraIssueDto issue2 = buildIssue("TEST-2", "Задача 2", "To Do", null);
        when(jiraClient.search(any(JiraSearchRequest.class)))
                .thenReturn(new JiraSearchResponse(List.of(issue1, issue2), 2, true, null));

        String result = handler.handle("Открытые задачи в проекте TEST", List.of());

        assertThat(result).isEqualTo("Найдено 2 задачи: TEST-1 и TEST-2");
        verify(jiraClient).search(any(JiraSearchRequest.class));
    }

    @Test
    void handle_noIssues_returnsNotFoundMessage() {
        when(callSpec.content()).thenReturn("project = EMPTY");
        when(jiraClient.search(any())).thenReturn(new JiraSearchResponse(List.of(), 0, true, null));

        String result = handler.handle("Задачи в пустом проекте", List.of());

        assertThat(result).containsIgnoringCase("не найдены");
    }

    @Test
    void handle_jiraSearchThrows_returnsErrorMessage() {
        when(callSpec.content()).thenReturn("project = BROKEN");
        when(jiraClient.search(any())).thenThrow(new RuntimeException("Jira timeout"));

        String result = handler.handle("Задачи в проекте BROKEN", List.of());

        assertThat(result).containsIgnoringCase("ошибка");
    }

    @Test
    void handle_withHistory_includesItInFormattingCall() {
        when(callSpec.content())
                .thenReturn("assignee = currentUser()")
                .thenReturn("Ваши задачи: ...");

        JiraIssueDto issue = buildIssue("MY-1", "Моя задача", "Open", "Я");
        when(jiraClient.search(any())).thenReturn(new JiraSearchResponse(List.of(issue), 1, true, null));

        Message history = message("Покажи мои задачи", SenderType.USER);
        handler.handle("Ещё раз покажи мои задачи", List.of(history));

        // Second LLM call (formatting) gets messages: system + 1 history + current = 3
        verify(spec).messages(messagesCaptor.capture());
        assertThat(messagesCaptor.getValue()).hasSize(3);
    }

    @Test
    void handle_nullIssuesFromJira_returnsNotFoundMessage() {
        when(callSpec.content()).thenReturn("order by created");
        when(jiraClient.search(any())).thenReturn(new JiraSearchResponse(null, 0, true, null));

        String result = handler.handle("Все задачи", List.of());

        assertThat(result).containsIgnoringCase("не найдены");
    }

    private JiraIssueDto buildIssue(String key, String summary, String status, String assigneeName) {
        JiraUserDto assignee = assigneeName != null
                ? new JiraUserDto(assigneeName, assigneeName.toLowerCase() + "@test.com", "acc-" + assigneeName)
                : null;
        JiraFieldsDto fields = new JiraFieldsDto(
                summary, null, assignee, null, new JiraStatusDto(status), null, null, null
        );
        return new JiraIssueDto(key, "https://jira/browse/" + key, fields);
    }

    private Message message(String text, SenderType sender) {
        Message m = new Message();
        m.setText(text);
        m.setSender(sender);
        return m;
    }
}
