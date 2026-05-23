package com.example.agent.dto;

/**
 * Результат классификации значимого события от ИИ.
 *
 * @param priority RED | YELLOW | GREEN
 * @param problem  краткое описание проблемы на русском
 */
public record PriorityClassification(String priority, String problem) {

    public static PriorityClassification fallback(String priority, String problem) {
        return new PriorityClassification(priority, problem);
    }
}
