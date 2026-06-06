package com.example.agent.controller;

import com.example.agent.dto.*;
import com.example.agent.service.DashboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * GET /api/dashboard/tasks?teamKey=PDM&weekStart=2025-05-19
     *
     * Возвращает задачи команды из Jira для таблицы Dashboard.
     * teamKey    — ключ проекта Jira (необязательный; без него — все доступные проекты)
     * weekStart  — понедельник нужной недели ISO (необязательный; без него — все задачи)
     */
    @GetMapping("/tasks")
    public List<DashboardTaskResponse> getTasks(
            @RequestParam(required = false) String teamKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {
        return dashboardService.getTasks(teamKey, weekStart);
    }

    /**
     * PATCH /api/dashboard/tasks/{issueKey}/complexity
     * Устанавливает сложность задачи вручную (переопределяет оценку ИИ).
     * Body: { "complexity": "EASY" | "HARD" }
     */
    @PatchMapping("/tasks/{issueKey}/complexity")
    public DashboardTaskResponse updateComplexity(
            @PathVariable String issueKey,
            @Valid @RequestBody UpdateComplexityRequest request
    ) {
        return dashboardService.updateComplexity(issueKey, request.complexity());
    }

    /**
     * GET /api/dashboard/stats?teamKey=PDM&weekStart=2025-05-19
     *
     * Возвращает блоки статистики:
     * — кол-во задач "В работе", "Все (бэклог)", "Готово" за выбранную неделю
     * — дельту по сравнению с предыдущей неделей
     */
    @GetMapping("/stats")
    public DashboardStatsResponse getStats(
            @RequestParam(required = false) String teamKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {
        return dashboardService.getStats(teamKey, weekStart);
    }

    /**
     * GET /api/dashboard/charts?teamKey=PDM&weekStart=2025-05-19
     *
     * Возвращает данные для двух столбчатых диаграмм:
     * — кол-во выполненных задач на человека (лёгких / сложных)
     * — кол-во активных задач на человека (лёгких / сложных)
     */
    @GetMapping("/charts")
    public DashboardChartsResponse getCharts(
            @RequestParam(required = false) String teamKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {
        return dashboardService.getCharts(teamKey, weekStart);
    }

    /**
     * GET /api/dashboard/roadmap?teamKey=PDM
     *
     * Возвращает данные для Roadmap (диаграмма Ганта):
     * — задачи команды с разбивкой на этапы (подзадачи из Jira)
     * — у каждого этапа свои даты и исполнитель
     */
    @GetMapping("/roadmap")
    public DashboardRoadmapResponse getRoadmap(
            @RequestParam(required = false) String teamKey
    ) {
        return dashboardService.getRoadmap(teamKey);
    }
}
