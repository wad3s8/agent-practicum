import { useMemo, useState } from 'react';
import { Button } from '@alfalab/core-components/button';
import { useQuery } from '@tanstack/react-query';
import confluenceIconUrl from '../../assets/icons/confluence.svg';
import jiraIconUrl from '../../assets/icons/jira.svg';
import {
  fetchDashboardCharts,
  fetchDashboardRoadmap,
  fetchDashboardStats,
  fetchDashboardTasks,
  fetchTeams,
  getApiErrorMessage,
  type DashboardStatsResponse,
  type DashboardTaskResponse,
  type PersonTaskStatsResponse,
  type RoadmapTaskResponse,
} from '../../api/client';
import { AnalyticsBarChart } from '../../components/AnalyticsBarChart/AnalyticsBarChart';
import { AnalyticsFilters } from '../../components/AnalyticsFilters/AnalyticsFilters';
import type { AnalyticsFiltersValue } from '../../components/AnalyticsFilters/types';
import { AnalyticsTasksTable } from '../../components/AnalyticsTasksTable/AnalyticsTasksTable';
import { AnalyticsTimeline } from '../../components/AnalyticsTimeline/AnalyticsTimeline';
import { TaskComparison } from '../../components/TaskComparison/TaskComparison';
import type { FilterOption } from '../../components/EventsFilters/types';
import type {
  AnalyticsTask,
  AnalyticsTaskPriority,
  AnalyticsTimelineMonth,
  AnalyticsTimelineRow,
  AnalyticsTimelineTone,
  AnalyticsTimelineWeek,
  PersonTasksMetric,
  TaskComparisonMetric,
} from '../../types/analytics';
import { addDays, formatDisplayDate, formatIsoDate, getPeriodEnd, getPeriodStart, getStartOfWeek } from '../../utils/dates';
import styles from './AnalyticsPage.module.css';

const ALL_TEAMS_OPTION: FilterOption = { key: 'all', content: 'Все команды' };
const TIMELINE_WEEK_COUNT = 8;
const TIMELINE_START_OFFSET_WEEKS = -2;
const EMPTY_DASHBOARD_STATS: DashboardStatsResponse = {
  inWorkCount: 0,
  inWorkDelta: 0,
  backlogCount: 0,
  backlogDelta: 0,
  doneCount: 0,
  doneDelta: 0,
};
const TIMELINE_TONES: AnalyticsTimelineTone[] = ['info', 'positive', 'negative'];

function mapPriority(priority: string | null | undefined): AnalyticsTaskPriority {
  const known = ['Highest', 'High', 'Medium', 'Low', 'Lowest'] as const;
  return known.find((p) => p === priority) ?? null;
}

function formatTaskPeriod(startDate: string | null, dueDate: string | null) {
  if (startDate && dueDate) {
    return `${startDate} - ${dueDate}`;
  }

  return startDate ?? dueDate ?? 'Не указан';
}

function mapDashboardTask(task: DashboardTaskResponse): AnalyticsTask {
  return {
    id: task.jiraIssueKey,
    initiator: {
      role: task.initiatorRole || 'Инициатор',
      name: task.initiatorName || 'Не указан',
    },
    title: task.taskName || task.jiraIssueKey,
    priority: mapPriority(task.priority),
    period: formatTaskPeriod(task.startDate, task.dueDate),
    periodCritical: task.deadlineOverdue,
    performers: task.assignees ?? [],
    sourceUrl: task.jiraUrl,
    status: task.status === 'CLOSED' ? 'closed' : 'open',
  };
}

function buildComparisonMetrics(stats: DashboardStatsResponse | undefined): TaskComparisonMetric[] {
  const dashboardStats = stats ?? EMPTY_DASHBOARD_STATS;

  return [
    {
      id: 'active',
      label: 'В работе',
      value: dashboardStats.inWorkCount,
      deltaLabel: 'к прошлой неделе',
      deltaValue: dashboardStats.inWorkDelta,
      tone: 'neutral',
    },
    {
      id: 'backlog',
      label: 'Все задачи',
      value: dashboardStats.backlogCount,
      deltaLabel: 'к прошлой неделе',
      deltaValue: dashboardStats.backlogDelta,
      tone: 'info',
    },
    {
      id: 'done',
      label: 'Завершено',
      value: dashboardStats.doneCount,
      deltaLabel: 'к прошлой неделе',
      deltaValue: dashboardStats.doneDelta,
      tone: 'accent',
    },
  ];
}

function parseIsoLocalDate(value: string) {
  const [year, month, day] = value.split('-').map(Number);

  return new Date(year, month - 1, day);
}

function capitalize(value: string) {
  return value.slice(0, 1).toUpperCase() + value.slice(1);
}

function createTimelineWeeks(): AnalyticsTimelineWeek[] {
  const firstWeekStart = getStartOfWeek();
  firstWeekStart.setDate(firstWeekStart.getDate() + TIMELINE_START_OFFSET_WEEKS * 7);

  return Array.from({ length: TIMELINE_WEEK_COUNT }, (_, weekIndex) => {
    const startDate = addDays(firstWeekStart, weekIndex * 7);
    const endDate = addDays(startDate, 6);

    return {
      id: formatIsoDate(startDate),
      label: `Неделя ${weekIndex + 1}`,
      dates: `${formatDisplayDate(startDate)} - ${formatDisplayDate(endDate)}`,
      startDate: formatIsoDate(startDate),
      endDate: formatIsoDate(endDate),
    };
  });
}

function createTimelineMonths(weeks: AnalyticsTimelineWeek[]): AnalyticsTimelineMonth[] {
  const months: AnalyticsTimelineMonth[] = [];

  weeks.forEach((week, weekIndex) => {
    const monthLabel = capitalize(parseIsoLocalDate(week.startDate).toLocaleDateString('ru-RU', { month: 'long' }));
    const currentMonth = months.at(-1);

    if (currentMonth?.label === monthLabel) {
      currentMonth.span += 1;
      return;
    }

    months.push({
      id: `${monthLabel}-${weekIndex}`,
      label: monthLabel,
      startWeek: weekIndex,
      span: 1,
    });
  });

  return months;
}

function dateToWeekIndex(dateStr: string | null, weeks: AnalyticsTimelineWeek[]): number {
  if (!dateStr) return 0;
  for (let i = 0; i < weeks.length; i++) {
    if (dateStr <= weeks[i].endDate) return i;
  }
  return weeks.length - 1;
}

function mapPersonTaskStats(stats: PersonTaskStatsResponse[]): PersonTasksMetric[] {
  return stats.map((stat) => ({
    id: stat.personName.toLowerCase().replace(/\s+/g, '-'),
    name: stat.personName,
    easy: stat.easyCount,
    hard: stat.hardCount,
  }));
}

function mapRoadmapToTimeline(tasks: RoadmapTaskResponse[], weeks: AnalyticsTimelineWeek[]): AnalyticsTimelineRow[] {
  return tasks.map((task, taskIndex) => ({
    id: task.key,
    title: task.name,
    tone: TIMELINE_TONES[taskIndex % TIMELINE_TONES.length],
    stages: task.phases.map((phase, phaseIndex) => {
      const startWeek = dateToWeekIndex(phase.startDate, weeks);
      const endWeek = dateToWeekIndex(phase.endDate, weeks);
      return {
        id: `${task.key}-${phaseIndex}`,
        title: phase.phaseName,
        assignee: phase.assignee ?? undefined,
        startWeek,
        span: Math.max(1, endWeek - startWeek + 1),
      };
    }),
  }));
}

export function AnalyticsPage() {
  const [filters, setFilters] = useState<AnalyticsFiltersValue>({ team: 'all', period: 'week' });
  const teamKey = filters.team === 'all' ? undefined : filters.team;
  const periodStart = getPeriodStart(filters.period);
  const periodEnd = getPeriodEnd(filters.period);
  const teamsQuery = useQuery({ queryKey: ['teams'], queryFn: fetchTeams });
  const dashboardTasksQuery = useQuery({
    queryKey: ['dashboard-tasks', teamKey, periodStart, periodEnd],
    queryFn: () => fetchDashboardTasks({ teamKey, weekStart: periodStart, weekEnd: periodEnd }),
  });
  const dashboardStatsQuery = useQuery({
    queryKey: ['dashboard-stats', teamKey, periodStart],
    queryFn: () => fetchDashboardStats({ teamKey, weekStart: periodStart }),
  });
  const dashboardChartsQuery = useQuery({
    queryKey: ['dashboard-charts', teamKey, periodStart],
    queryFn: () => fetchDashboardCharts({ teamKey, weekStart: periodStart }),
  });
  const dashboardRoadmapQuery = useQuery({
    queryKey: ['dashboard-roadmap', teamKey],
    queryFn: () => fetchDashboardRoadmap({ teamKey }),
  });

  const teamOptions = useMemo<FilterOption[]>(
    () => [
      ALL_TEAMS_OPTION,
      ...(teamsQuery.data ?? []).map((team) => ({
        key: team.jiraProjectKey,
        content: team.name,
      })),
    ],
    [teamsQuery.data],
  );
  const jiraProjectUrl = teamKey ? `https://vladutrobin2006.atlassian.net/jira/software/projects/${teamKey}/list` : undefined;
  const confluenceProjectUrl = teamKey ? `https://vladutrobin2006.atlassian.net/wiki/spaces/${teamKey}` : undefined;
  const tasks = useMemo(() => (dashboardTasksQuery.data ?? []).map(mapDashboardTask), [dashboardTasksQuery.data]);
  const comparisonMetrics = useMemo(() => buildComparisonMetrics(dashboardStatsQuery.data), [dashboardStatsQuery.data]);
  const timelineWeeks = useMemo(() => createTimelineWeeks(), []);
  const timelineMonths = useMemo(() => createTimelineMonths(timelineWeeks), [timelineWeeks]);
  const completedTasksByPerson = useMemo(
    () => mapPersonTaskStats(dashboardChartsQuery.data?.completedPerPerson ?? []),
    [dashboardChartsQuery.data],
  );
  const activeTasksByPerson = useMemo(
    () => mapPersonTaskStats(dashboardChartsQuery.data?.activePerPerson ?? []),
    [dashboardChartsQuery.data],
  );
  const timelineRows = useMemo(
    () => mapRoadmapToTimeline(dashboardRoadmapQuery.data?.tasks ?? [], timelineWeeks),
    [dashboardRoadmapQuery.data, timelineWeeks],
  );

  return (
    <div className={styles.page}>
      <div className={styles.topSection}>
        <header className={styles.header}>
          <AnalyticsFilters teamOptions={teamOptions} value={filters} onChange={setFilters} />
          <div className={styles.externalLinks}>
            <Button
              className={styles.linkButton}
              disabled={!jiraProjectUrl}
              href={jiraProjectUrl}
              leftAddons={<img alt="" className={styles.serviceIcon} src={jiraIconUrl} />}
              size={32}
              target="_blank"
              view="transparent"
            >
              Jira
            </Button>
            <Button
              className={styles.linkButton}
              disabled={!confluenceProjectUrl}
              href={confluenceProjectUrl}
              leftAddons={<img alt="" className={styles.serviceIcon} src={confluenceIconUrl} />}
              size={32}
              target="_blank"
              view="transparent"
            >
              Confluence
            </Button>
          </div>
        </header>

        {dashboardTasksQuery.isLoading && <p className={styles.state}>Загружаем задачи...</p>}
        {dashboardTasksQuery.isError && (
          <p className={styles.error} role="alert">
            {getApiErrorMessage(dashboardTasksQuery.error, 'Не удалось загрузить задачи')}
          </p>
        )}
        {!dashboardTasksQuery.isLoading && !dashboardTasksQuery.isError && (
          <>
            {tasks.length === 0 && <p className={styles.state}>Задач по выбранным фильтрам нет</p>}
            <AnalyticsTasksTable tasks={tasks} />
          </>
        )}
      </div>

      {dashboardStatsQuery.isError && (
        <p className={styles.error} role="alert">
          {getApiErrorMessage(dashboardStatsQuery.error, 'Не удалось загрузить сводку')}
        </p>
      )}
      <TaskComparison metrics={comparisonMetrics} />

      <div className={styles.chartsGrid}>
        <AnalyticsBarChart data={completedTasksByPerson} title="Количество выполненных задач на человека" />
        <AnalyticsBarChart data={activeTasksByPerson} title="Количество активных задач на человека" />
      </div>

      <AnalyticsTimeline months={timelineMonths} rows={timelineRows} weeks={timelineWeeks} />
    </div>
  );
}
