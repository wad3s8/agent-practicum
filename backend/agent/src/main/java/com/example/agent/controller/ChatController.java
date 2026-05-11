package com.example.agent.controller;

import com.example.agent.entity.Chat;
import com.example.agent.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;
}
