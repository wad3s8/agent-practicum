package com.example.agent.service;

import com.example.agent.dto.ChatResponse;
import com.example.agent.entity.Chat;
import com.example.agent.entity.User;
import com.example.agent.repository.ChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;

    public List<ChatResponse> getUserChats(User user) {
        return chatRepository.findByUserOrderByPinnedDescCreatedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ChatResponse updateTitle(Long chatId, User user, String title) {
        Chat chat = getOwnedChat(chatId, user);
        chat.setTitle(title);
        return toResponse(chatRepository.save(chat));
    }

    public ChatResponse togglePin(Long chatId, User user) {
        Chat chat = getOwnedChat(chatId, user);
        chat.setPinned(!chat.isPinned());
        return toResponse(chatRepository.save(chat));
    }

    public void deleteChat(Long chatId, User user) {
        Chat chat = getOwnedChat(chatId, user);
        chatRepository.delete(chat);
    }

    public Chat getOwnedChat(Long chatId, User user) {
        return chatRepository.findByIdAndUser(chatId, user)
                .orElseThrow(() -> new NoSuchElementException("Chat not found"));
    }

    public ChatResponse toResponse(Chat chat) {
        return new ChatResponse(chat.getId(), chat.getTitle(), chat.isPinned(), chat.getCreatedAt(), chat.getCaseType());
    }
}
