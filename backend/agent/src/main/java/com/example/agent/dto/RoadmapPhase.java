package com.example.agent.dto;

public record RoadmapPhase(
        /** Название этапа: "Аналитика", "Разработка 1", "Тест" и т.д. */
        String phaseName,
        /** Исполнитель этапа */
        String assignee,
        /** Дата начала ISO (yyyy-MM-dd) */
        String startDate,
        /** Дата окончания ISO (yyyy-MM-dd) */
        String endDate
) {
}
