package com.example.agent.service;

import com.example.agent.entity.Chat;
import com.example.agent.repository.ChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;

    public Chat addChat(Chat chat) {
        chatRepository.save(chat);
    }
}
