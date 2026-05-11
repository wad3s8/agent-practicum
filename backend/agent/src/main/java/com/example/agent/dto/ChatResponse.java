package com.example.agent.dto;

import com.example.agent.entity.CaseType;

import java.time.Instant;

public record ChatResponse(Long id, String title, boolean pinned, Instant createdAt, CaseType caseType) {}
