package com.example.agent.dto;

public record DashboardStatsResponse(
        /** Задачи в статусе "В работе" за выбранную неделю */
        int inWorkCount,
        /** Разница с предыдущей неделей (положительное = больше, отрицательное = меньше) */
        int inWorkDelta,

        /** Все задачи за выбранную неделю */
        int backlogCount,
        int backlogDelta,

        /** Задачи в статусе "Готово" за выбранную неделю */
        int doneCount,
        int doneDelta
) {
}
