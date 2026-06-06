package com.example.agent.service;

import com.example.agent.entity.CaseType;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrchestratorService {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
            Ты — роутер запросов. Проанализируй сообщение пользователя и определи к какому кейсу оно относится.

            Доступные кейсы:
            - MEETING_SUMMARY: пользователь хочет оформить, структурировать или получить саммари заметок с внутренней рабочей встречи
            - CONFERENCE_INFO: пользователь хочет найти информацию в Confluence (корпоративная база знаний): задаёт вопрос, просит найти документ, инструкцию, политику или другую информацию из wiki
            - TASK_ASSIGNMENT: пользователь хочет назначить задачу Jira на конкретного исполнителя
            - JIRA_INFO: пользователь хочет получить информацию о задачах, проектах, статусах или исполнителях из Jira
            - GENERAL: всё остальное

            Ответь ТОЛЬКО названием кейса, без объяснений.
            """;

    public CaseType detect(String userMessage) {
        String result = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userMessage)
                .call()
                .content()
                .trim()
                .toUpperCase();

        try {
            return CaseType.valueOf(result);
        } catch (IllegalArgumentException e) {
            return CaseType.GENERAL;
        }
    }
}
