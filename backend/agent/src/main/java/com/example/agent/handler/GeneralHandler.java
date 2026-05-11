package com.example.agent.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GeneralHandler {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
            Ты — корпоративный AI-ассистент для руководителей. Отвечай на русском языке, \
            чётко и по делу.
            """;

    public String handle(String userText) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userText)
                .call()
                .content();
    }
}
