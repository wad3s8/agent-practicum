package com.example.agent.dto;

import java.util.List;

public record DashboardChartsResponse(
        /** Кол-во выполненных задач на человека (статус "Готово"), разбивка по сложности */
        List<PersonTaskStats> completedPerPerson,
        /** Кол-во активных задач на человека (статус "В работе"), разбивка по сложности */
        List<PersonTaskStats> activePerPerson
) {
}
