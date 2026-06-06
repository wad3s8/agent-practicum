package com.example.agent.dto;

public record PersonTaskStats(
        String personName,
        int easyCount,
        int hardCount
) {
}
