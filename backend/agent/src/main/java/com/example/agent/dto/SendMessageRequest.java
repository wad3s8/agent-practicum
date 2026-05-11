package com.example.agent.dto;

import com.example.agent.entity.CaseType;
import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(Long chatId, @NotBlank String text, CaseType caseType) {}
