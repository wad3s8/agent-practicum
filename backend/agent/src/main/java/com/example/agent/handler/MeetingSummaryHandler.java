package com.example.agent.handler;

import com.example.agent.entity.Message;
import com.example.agent.entity.SenderType;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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

            Если какого-то раздела нет в заметках — включай его.
            """;

    public String handle(String userText, List<Message> history) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        history.forEach(msg -> messages.add(
                msg.getSender() == SenderType.USER
                        ? new UserMessage(msg.getText())
                        : new AssistantMessage(msg.getText())
        ));
        messages.add(new UserMessage(userText));

        return chatClient.prompt()
                .messages(messages)
                .call()
                .content();
    }
}
