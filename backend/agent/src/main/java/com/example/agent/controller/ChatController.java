package com.example.agent.controller;

import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatClient chatClient;

    @GetMapping("/api")
    public String getMessage(String question){
        return chatClient.prompt()
                .system("Ответь на вопрос")
                .user(question)
                .call()
                .content();
    }
}
