package com.example.agent.dto;

import jakarta.validation.constraints.Pattern;

public record UpdateComplexityRequest(
        @Pattern(regexp = "EASY|HARD", message = "complexity must be EASY or HARD")
        String complexity
) {
}
