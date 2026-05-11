package com.example.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateChatTitleRequest(@NotBlank @Size(max = 100) String title) {}
