package com.example.agent.service;

import com.example.agent.entity.CaseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrchestratorServiceTest {

    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec spec;
    @Mock ChatClient.CallResponseSpec callSpec;

    OrchestratorService service;

    @BeforeEach
    void setUp() {
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        service = new OrchestratorService(chatClient);
    }

    @Test
    void detect_meetingSummaryResponse_returnsMeetingSummary() {
        when(callSpec.content()).thenReturn("MEETING_SUMMARY");
        assertThat(service.detect("Оформи саммари встречи")).isEqualTo(CaseType.MEETING_SUMMARY);
    }

    @Test
    void detect_conferenceInfoResponse_returnsConferenceInfo() {
        when(callSpec.content()).thenReturn("CONFERENCE_INFO");
        assertThat(service.detect("Вот транскрипт конференции...")).isEqualTo(CaseType.CONFERENCE_INFO);
    }

    @Test
    void detect_taskAssignmentResponse_returnsTaskAssignment() {
        when(callSpec.content()).thenReturn("TASK_ASSIGNMENT");
        assertThat(service.detect("Назначь PROJ-10 на Ивана")).isEqualTo(CaseType.TASK_ASSIGNMENT);
    }

    @Test
    void detect_jiraInfoResponse_returnsJiraInfo() {
        when(callSpec.content()).thenReturn("JIRA_INFO");
        assertThat(service.detect("Покажи открытые задачи")).isEqualTo(CaseType.JIRA_INFO);
    }

    @Test
    void detect_generalResponse_returnsGeneral() {
        when(callSpec.content()).thenReturn("GENERAL");
        assertThat(service.detect("Привет, как дела?")).isEqualTo(CaseType.GENERAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "gibberish", "", "  ", "Не знаю"})
    void detect_unknownOrGarbageResponse_fallsBackToGeneral(String response) {
        when(callSpec.content()).thenReturn(response);
        assertThat(service.detect("какой-то запрос")).isEqualTo(CaseType.GENERAL);
    }

    @Test
    void detect_responseWithLeadingWhitespace_parsedCorrectly() {
        when(callSpec.content()).thenReturn("  JIRA_INFO  ");
        assertThat(service.detect("Задачи в проекте")).isEqualTo(CaseType.JIRA_INFO);
    }

    @Test
    void detect_lowercaseResponse_parsedCorrectly() {
        when(callSpec.content()).thenReturn("meeting_summary");
        assertThat(service.detect("Запиши встречу")).isEqualTo(CaseType.MEETING_SUMMARY);
    }
}
