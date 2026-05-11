package com.example.agent.dto;

import com.example.agent.entity.SenderType;

import java.time.Instant;

public record MessageResponse(Long id, String text, SenderType sender, Instant createdAt) {}
