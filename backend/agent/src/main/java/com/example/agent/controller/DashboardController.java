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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekEnd
    ) {
        return dashboardService.getTasks(teamKey, weekStart, weekEnd);
    }

    @GetMapping("/stats")
    public DashboardStatsResponse getStats(
            @RequestParam(required = false) String teamKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {
        return dashboardService.getStats(teamKey, weekStart);
    }

    @GetMapping("/charts")
    public DashboardChartsResponse getCharts(
            @RequestParam(required = false) String teamKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekEnd
    ) {
        return dashboardService.getCharts(teamKey, weekStart, weekEnd);
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
