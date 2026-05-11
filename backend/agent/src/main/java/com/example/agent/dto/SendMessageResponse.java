package com.example.agent.dto;

public record SendMessageResponse(Long chatId, MessageResponse userMessage, MessageResponse aiMessage) {}
