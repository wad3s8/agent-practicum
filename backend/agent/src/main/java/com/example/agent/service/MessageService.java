package com.example.agent.service;

import com.example.agent.dto.MessageResponse;
import com.example.agent.dto.SendMessageRequest;
import com.example.agent.dto.SendMessageResponse;
import com.example.agent.entity.Chat;
import com.example.agent.entity.Message;
import com.example.agent.entity.SenderType;
import com.example.agent.entity.User;
import com.example.agent.repository.ChatRepository;
import com.example.agent.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final ChatService chatService;

    @Transactional
    public SendMessageResponse sendMessage(SendMessageRequest request, User user) {
        Chat chat;
        if (request.chatId() == null) {
            chat = new Chat();
            chat.setUser(user);
            String text = request.text();
            chat.setTitle(text.length() > 50 ? text.substring(0, 50) : text);
            chat.setCreatedAt(Instant.now());
            chat = chatRepository.save(chat);
        } else {
            chat = chatService.getOwnedChat(request.chatId(), user);
        }

        Message message = new Message();
        message.setText(request.text());
        message.setSender(SenderType.USER);
        message.setCreatedAt(Instant.now());
        message.setChat(chat);
        messageRepository.save(message);

        return new SendMessageResponse(chat.getId(), toResponse(message));
    }

    public List<MessageResponse> getChatHistory(Long chatId, User user) {
        Chat chat = chatService.getOwnedChat(chatId, user);
        return messageRepository.findByChatOrderByCreatedAtAsc(chat)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private MessageResponse toResponse(Message message) {
        return new MessageResponse(message.getId(), message.getText(), message.getSender(), message.getCreatedAt());
    }
}
