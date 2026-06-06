package com.example.agent.handler;

import com.example.agent.client.JiraClient;
import com.example.agent.dto.jira.JiraAssigneeRequest;
import com.example.agent.dto.jira.JiraUserDto;
import com.example.agent.entity.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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
        handler = new TaskAssignmentHandler(chatClient, jiraClient, new ObjectMapper());
    }

    @Test
    void handle_successfulAssignment_returnsConfirmation() {
        when(callSpec.content()).thenReturn(
                """
                {"issueKey":"PROJ-42","assigneeName":"Иван Петров"}""");
        JiraUserDto user = new JiraUserDto("Иван Петров", "ivan@example.com", "account-abc");
        when(jiraClient.searchUsers("Иван Петров")).thenReturn(List.of(user));

        String result = handler.handle("Назначь PROJ-42 на Ивана Петрова", List.of());

        assertThat(result).contains("PROJ-42").contains("Иван Петров");
        verify(jiraClient).assignIssue(eq("PROJ-42"), any(JiraAssigneeRequest.class));
    }

    @Test
    void handle_passesCorrectAccountIdToJira() {
        when(callSpec.content()).thenReturn(
                """
                {"issueKey":"ABC-7","assigneeName":"Мария Смирнова"}""");
        JiraUserDto user = new JiraUserDto("Мария Смирнова", "maria@example.com", "uid-777");
        when(jiraClient.searchUsers("Мария Смирнова")).thenReturn(List.of(user));

        handler.handle("Назначь ABC-7 на Марию", List.of());

        verify(jiraClient).assignIssue("ABC-7", new JiraAssigneeRequest("uid-777"));
    }

    @Test
    void handle_userNotFound_returnsUserNotFoundMessage() {
        when(callSpec.content()).thenReturn(
                """
                {"issueKey":"PROJ-42","assigneeName":"Неизвестный Человек"}""");
        when(jiraClient.searchUsers("Неизвестный Человек")).thenReturn(List.of());

        String result = handler.handle("Назначь PROJ-42 на Неизвестного Человека", List.of());

        assertThat(result).containsIgnoringCase("не найден");
        verify(jiraClient, never()).assignIssue(anyString(), any());
    }

    @Test
    void handle_extractionReturnsNullIssueKey_returnsErrorMessage() {
        when(callSpec.content()).thenReturn(
                """
                {"issueKey":null,"assigneeName":"Иван Петров"}""");

        String result = handler.handle("Назначь задачу на Ивана", List.of());

        assertThat(result).containsIgnoringCase("ключ задачи");
        verify(jiraClient, never()).searchUsers(anyString());
    }

    @Test
    void handle_extractionReturnsNullAssigneeName_returnsErrorMessage() {
        when(callSpec.content()).thenReturn(
                """
                {"issueKey":"PROJ-10","assigneeName":null}""");

        String result = handler.handle("Назначь PROJ-10 на кого-нибудь", List.of());

        assertThat(result).containsIgnoringCase("исполнителя");
        verify(jiraClient, never()).searchUsers(anyString());
    }

    @Test
    void handle_invalidJson_returnsExtractionError() {
        when(callSpec.content()).thenReturn("Не могу определить задачу");

        String result = handler.handle("Назначь что-то на кого-то", List.of());

        assertThat(result).containsIgnoringCase("не удалось");
        verify(jiraClient, never()).searchUsers(anyString());
    }

    @Test
    void handle_jiraAssignThrows_returnsErrorMessage() {
        when(callSpec.content()).thenReturn(
                """
                {"issueKey":"PROJ-1","assigneeName":"Тест Тестов"}""");
        JiraUserDto user = new JiraUserDto("Тест Тестов", "test@example.com", "acc-123");
        when(jiraClient.searchUsers("Тест Тестов")).thenReturn(List.of(user));
        doThrow(new RuntimeException("Jira недоступна")).when(jiraClient).assignIssue(anyString(), any());

        String result = handler.handle("Назначь PROJ-1 на Тест Тестова", List.of());

        assertThat(result).containsIgnoringCase("ошибка");
    }
}
