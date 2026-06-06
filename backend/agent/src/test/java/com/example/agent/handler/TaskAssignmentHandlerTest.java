package com.example.agent.handler;

import com.example.agent.client.JiraClient;
import com.example.agent.dto.jira.JiraCreateIssueRequest;
import com.example.agent.dto.jira.JiraCreateIssueResponse;
import com.example.agent.dto.jira.JiraProjectDto;
import com.example.agent.dto.jira.JiraUserDto;
import com.example.agent.entity.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskAssignmentHandlerTest {

    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec spec;
    @Mock ChatClient.CallResponseSpec callSpec;
    @Mock JiraClient jiraClient;

    TaskAssignmentHandler handler;

    @BeforeEach
    void setUp() {
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        handler = new TaskAssignmentHandler(chatClient, jiraClient);
    }

    @Test
    void handle_successfulCreationWithAssignee_returnsConfirmation() {
        when(callSpec.content()).thenReturn(
                """
                {"taskTitle":"Настроить CI","projectHint":"PROJ","assigneeName":"Иван Петров"}""");
        JiraUserDto user = new JiraUserDto("Иван Петров", "ivan@example.com", "acc-ivan");
        when(jiraClient.getProjects()).thenReturn(List.of(new JiraProjectDto("PROJ", "My Project", null)));
        when(jiraClient.searchUsers("Иван Петров")).thenReturn(List.of(user));
        when(jiraClient.createIssue(any())).thenReturn(new JiraCreateIssueResponse("10001", "PROJ-5", "https://jira/PROJ-5"));

        String result = handler.handle("Создай задачу Настроить CI для Ивана Петрова в проекте PROJ", List.of());

        assertThat(result).contains("PROJ-5").contains("Иван Петров").contains("Настроить CI");
        verify(jiraClient).createIssue(any(JiraCreateIssueRequest.class));
    }

    @Test
    void handle_projectMatchedByName_usesCorrectKey() {
        when(callSpec.content()).thenReturn(
                """
                {"taskTitle":"Задача","projectHint":"My PM Team","assigneeName":null}""");
        when(jiraClient.getProjects()).thenReturn(List.of(
                new JiraProjectDto("SAM1", "Annual Product Roadmap", null),
                new JiraProjectDto("PDM", "My PM Team", null)
        ));
        when(jiraClient.createIssue(any())).thenReturn(new JiraCreateIssueResponse("10002", "PDM-1", "https://jira/PDM-1"));

        String result = handler.handle("Создай задачу в проекте My PM Team", List.of());

        assertThat(result).contains("PDM-1");
    }

    @Test
    void handle_noProjectHintInMessage_usesFirstProject() {
        when(callSpec.content()).thenReturn(
                """
                {"taskTitle":"Починить баг","projectHint":null,"assigneeName":null}""");
        when(jiraClient.getProjects()).thenReturn(List.of(new JiraProjectDto("AP", "Alpha Project", null)));
        when(jiraClient.createIssue(any())).thenReturn(new JiraCreateIssueResponse("10002", "AP-1", "https://jira/AP-1"));

        String result = handler.handle("Создай задачу Починить баг", List.of());

        assertThat(result).contains("AP-1").contains("Починить баг");
    }

    @Test
    void handle_noProjectsInJira_returnsError() {
        when(callSpec.content()).thenReturn(
                """
                {"taskTitle":"Задача","projectHint":null,"assigneeName":null}""");
        when(jiraClient.getProjects()).thenReturn(List.of());

        String result = handler.handle("Создай задачу", List.of());

        assertThat(result).containsIgnoringCase("проект");
        verify(jiraClient, never()).createIssue(any());
    }

    @Test
    void handle_projectHintNotFound_returnsError() {
        when(callSpec.content()).thenReturn(
                """
                {"taskTitle":"Задача","projectHint":"NONEXISTENT","assigneeName":null}""");
        when(jiraClient.getProjects()).thenReturn(List.of(new JiraProjectDto("PROJ", "My Project", null)));

        String result = handler.handle("Создай задачу в проекте NONEXISTENT", List.of());

        assertThat(result).containsIgnoringCase("не найден");
        verify(jiraClient, never()).createIssue(any());
    }

    @Test
    void handle_userNotFound_returnsUserNotFoundMessage() {
        when(callSpec.content()).thenReturn(
                """
                {"taskTitle":"Задача","projectHint":"PROJ","assigneeName":"Неизвестный"}""");
        when(jiraClient.getProjects()).thenReturn(List.of(new JiraProjectDto("PROJ", "My Project", null)));
        when(jiraClient.searchUsers("Неизвестный")).thenReturn(List.of());

        String result = handler.handle("Создай задачу для Неизвестного", List.of());

        assertThat(result).containsIgnoringCase("не найден");
        verify(jiraClient, never()).createIssue(any());
    }

    @Test
    void handle_invalidJson_returnsExtractionError() {
        when(callSpec.content()).thenReturn("не могу разобрать");

        String result = handler.handle("сделай что-нибудь", List.of());

        assertThat(result).containsIgnoringCase("не удалось");
        verify(jiraClient, never()).createIssue(any());
    }

    @Test
    void handle_nullTaskTitle_returnsError() {
        when(callSpec.content()).thenReturn(
                """
                {"taskTitle":null,"projectHint":"PROJ","assigneeName":"Иван"}""");

        String result = handler.handle("что-то создай", List.of());

        assertThat(result).containsIgnoringCase("название задачи");
        verify(jiraClient, never()).createIssue(any());
    }

    @Test
    void handle_jiraCreateThrows_returnsErrorMessage() {
        when(callSpec.content()).thenReturn(
                """
                {"taskTitle":"Тест","projectHint":"PROJ","assigneeName":null}""");
        when(jiraClient.getProjects()).thenReturn(List.of(new JiraProjectDto("PROJ", "My Project", null)));
        when(jiraClient.createIssue(any())).thenThrow(new RuntimeException("Jira недоступна"));

        String result = handler.handle("Создай задачу Тест", List.of());

        assertThat(result).containsIgnoringCase("ошибка");
    }
}
