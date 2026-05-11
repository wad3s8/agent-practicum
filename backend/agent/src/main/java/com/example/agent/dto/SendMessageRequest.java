package com.example.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(Long chatId, @NotBlank String text) {}
