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
            - MEETING_SUMMARY: пользователь хочет оформить, структурировать или получить саммари заметок встречи
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
