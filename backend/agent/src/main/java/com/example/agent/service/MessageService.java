package com.example.agent.service;

import com.example.agent.dto.MessageResponse;
import com.example.agent.dto.SendMessageRequest;
import com.example.agent.dto.SendMessageResponse;
import com.example.agent.entity.*;
import com.example.agent.handler.ConferenceInfoHandler;
import com.example.agent.handler.GeneralHandler;
import com.example.agent.handler.JiraInfoHandler;
import com.example.agent.handler.MeetingSummaryHandler;
import com.example.agent.handler.TaskAssignmentHandler;
import com.example.agent.handler.WeeklyDigestHandler;
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
    private final OrchestratorService orchestratorService;
    private final MeetingSummaryHandler meetingSummaryHandler;
    private final ConferenceInfoHandler conferenceInfoHandler;
    private final TaskAssignmentHandler taskAssignmentHandler;
    private final JiraInfoHandler jiraInfoHandler;
    private final GeneralHandler generalHandler;
    private final WeeklyDigestHandler weeklyDigestHandler;

    @Transactional
    public SendMessageResponse sendMessage(SendMessageRequest request, User user) {
        Chat chat;

        if (request.chatId() == null) {
            CaseType caseType = request.caseType() != null
                    ? request.caseType()
                    : orchestratorService.detect(request.text());

            chat = new Chat();
            chat.setUser(user);
            chat.setCaseType(caseType);
            chat.setCreatedAt(Instant.now());
            String text = request.text();
            chat.setTitle(text.length() > 50 ? text.substring(0, 50) : text);
            chat = chatRepository.save(chat);
        } else {
            chat = chatService.getOwnedChat(request.chatId(), user);
        }

        List<Message> history = messageRepository.findByChatOrderByCreatedAtAsc(chat);

        Message userMessage = saveMessage(chat, request.text(), SenderType.USER);
        String aiText = route(chat.getCaseType(), request.text(), history);
        Message aiMessage = saveMessage(chat, aiText, SenderType.SYSTEM);

        return new SendMessageResponse(chat.getId(), toResponse(userMessage), toResponse(aiMessage));
    }

    public List<MessageResponse> getChatHistory(Long chatId, User user) {
        Chat chat = chatService.getOwnedChat(chatId, user);
        return messageRepository.findByChatOrderByCreatedAtAsc(chat)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private String route(CaseType caseType, String text, List<Message> history) {
        return switch (caseType) {
            case MEETING_SUMMARY -> meetingSummaryHandler.handle(text, history);
            case CONFERENCE_INFO -> conferenceInfoHandler.handle(text, history);
            case TASK_ASSIGNMENT -> taskAssignmentHandler.handle(text, history);
            case JIRA_INFO -> jiraInfoHandler.handle(text, history);
            case WEEKLY_DIGEST -> weeklyDigestHandler.handle(text, history);
            case GENERAL -> generalHandler.handle(text, history);
        };
    }

    private Message saveMessage(Chat chat, String text, SenderType sender) {
        Message message = new Message();
        message.setText(text);
        message.setSender(sender);
        message.setCreatedAt(Instant.now());
        message.setChat(chat);
        return messageRepository.save(message);
    }

    private MessageResponse toResponse(Message message) {
        return new MessageResponse(message.getId(), message.getText(), message.getSender(), message.getCreatedAt());
    }
}
