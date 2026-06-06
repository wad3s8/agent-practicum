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
public class ConferenceInfoHandler {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
            Ты — ассистент для работы с материалами конференций и переговоров.

            Когда пользователь предоставляет транскрипт или заметки с конференции/переговоров:
            - Извлекай ключевую информацию: факты, договорённости, даты, имена, цифры
            - Структурируй найденные данные в читаемый формат
            - Отвечай на конкретные вопросы о содержании переговоров
            - Выделяй важные договорённости, решения и обязательства сторон

            Используй Markdown для форматирования. Отвечай на русском языке.
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
