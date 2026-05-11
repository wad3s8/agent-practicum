package com.example.agent.dto;

import java.time.Instant;

public record ChatResponse(Long id, String title, boolean pinned, Instant createdAt) {}
