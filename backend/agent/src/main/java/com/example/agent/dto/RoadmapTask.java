package com.example.agent.dto;

import java.util.List;

public record RoadmapTask(
        String key,
        String name,
        /** HEX-цвет строки в Gantt-диаграмме, уникальный для каждой задачи */
        String color,
        /** Общая дата начала задачи */
        String startDate,
        /** Общая дата окончания задачи */
        String endDate,
        List<RoadmapPhase> phases
) {
}
