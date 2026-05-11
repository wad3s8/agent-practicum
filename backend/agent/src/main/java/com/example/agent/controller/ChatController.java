package com.example.agent.controller;

import com.example.agent.dto.ChatResponse;
import com.example.agent.dto.MessageResponse;
import com.example.agent.dto.UpdateChatTitleRequest;
import com.example.agent.security.UserDetailsAdapter;
import com.example.agent.service.ChatService;
import com.example.agent.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final MessageService messageService;

    @GetMapping
    public List<ChatResponse> getUserChats(@AuthenticationPrincipal UserDetailsAdapter currentUser) {
        return chatService.getUserChats(currentUser.getUser());
    }

    @GetMapping("/{chatId}/messages")
    public List<MessageResponse> getChatHistory(
            @PathVariable Long chatId,
            @AuthenticationPrincipal UserDetailsAdapter currentUser
    ) {
        return messageService.getChatHistory(chatId, currentUser.getUser());
    }

    @PatchMapping("/{chatId}/title")
    public ChatResponse updateTitle(
            @PathVariable Long chatId,
            @RequestBody UpdateChatTitleRequest request,
            @AuthenticationPrincipal UserDetailsAdapter currentUser
    ) {
        return chatService.updateTitle(chatId, currentUser.getUser(), request.title());
    }

    @PatchMapping("/{chatId}/pin")
    public ChatResponse togglePin(
            @PathVariable Long chatId,
            @AuthenticationPrincipal UserDetailsAdapter currentUser
    ) {
        return chatService.togglePin(chatId, currentUser.getUser());
    }

    @DeleteMapping("/{chatId}")
    public ResponseEntity<Void> deleteChat(
            @PathVariable Long chatId,
            @AuthenticationPrincipal UserDetailsAdapter currentUser
    ) {
        chatService.deleteChat(chatId, currentUser.getUser());
        return ResponseEntity.noContent().build();
    }
}
