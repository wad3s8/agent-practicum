package com.example.agent.controller;

import com.example.agent.dto.SendMessageRequest;
import com.example.agent.dto.SendMessageResponse;
import com.example.agent.security.UserDetailsAdapter;
import com.example.agent.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    public SendMessageResponse sendMessage(
            @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal UserDetailsAdapter currentUser
    ) {
        return messageService.sendMessage(request, currentUser.getUser());
    }
}
