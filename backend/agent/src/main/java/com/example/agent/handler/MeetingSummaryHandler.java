package com.example.agent.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MeetingSummaryHandler {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
            Ты — профессиональный бизнес-ассистент. Твоя задача — оформить сырые заметки встречи \
            в чистое структурированное саммари на русском языке.

            Используй Markdown-форматирование. Структура:
            ## Встреча
            Дата, участники (если упомянуты)

            ## Ключевые темы
            Краткое изложение обсуждённых вопросов

            ## Принятые решения
            Список решений

            ## Задачи
            - [ ] Задача — Ответственный (если упомянут)

            ## Следующие шаги
            Что запланировано

            Если какого-то раздела нет в заметках — не включай его.
            """;

    public String handle(String userText) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userText)
                .call()
                .content();
    }
}
