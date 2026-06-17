import { useMemo, useState } from 'react';
import { Button } from '@alfalab/core-components/button';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import confluenceIconUrl from '../../assets/icons/confluence.svg';
import jiraIconUrl from '../../assets/icons/jira.svg';
import {
  fetchDashboardStats,
  fetchDashboardTasks,
  fetchTeams,
  getApiErrorMessage,
  updateTaskComplexity,
  type DashboardStatsResponse,
  type DashboardTaskResponse,
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
  AnalyticsTaskComplexity,
  AnalyticsTimelineMonth,
  AnalyticsTimelineRow,
  AnalyticsTimelineWeek,
  PersonTasksMetric,
  TaskComparisonMetric,
} from '../../types/analytics';
import { addDays, formatDisplayDate, formatIsoDate, getStartOfWeek, getWeekStartForPeriod } from '../../utils/dates';
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
const MOCK_COMPLETED_TASKS_BY_PERSON: PersonTasksMetric[] = [
  { id: 'mock-completed-vlad-utrobin', name: 'Влад Утробин', easy: 4, hard: 2 },
  { id: 'mock-completed-anna-smirnova', name: 'Анна Смирнова', easy: 3, hard: 1 },
  { id: 'mock-completed-ivan-petrov', name: 'Иван Петров', easy: 2, hard: 3 },
  { id: 'mock-completed-maria-orlova', name: 'Мария Орлова', easy: 5, hard: 1 },
  { id: 'mock-completed-oleg-kim', name: 'Олег Ким', easy: 1, hard: 2 },
];
const MOCK_ACTIVE_TASKS_BY_PERSON: PersonTasksMetric[] = [
  { id: 'mock-active-vlad-utrobin', name: 'Влад Утробин', easy: 2, hard: 4 },
  { id: 'mock-active-anna-smirnova', name: 'Анна Смирнова', easy: 5, hard: 2 },
  { id: 'mock-active-ivan-petrov', name: 'Иван Петров', easy: 3, hard: 3 },
  { id: 'mock-active-maria-orlova', name: 'Мария Орлова', easy: 4, hard: 1 },
  { id: 'mock-active-oleg-kim', name: 'Олег Ким', easy: 2, hard: 2 },
];
const MOCK_TIMELINE_ROWS: AnalyticsTimelineRow[] = [
  {
    id: 'mock-timeline-product-roadmap',
    title: 'Product roadmap',
    tone: 'negative',
    stages: [
      { id: 'mock-timeline-product-roadmap-discovery', title: 'Discovery', assignee: 'Анна Смирнова', startWeek: 0, span: 2 },
      {
        id: 'mock-timeline-product-roadmap-priorities',
        title: 'Приоритизация',
        assignee: 'Влад Утробин',
        startWeek: 2,
        span: 2,
        isCritical: true,
      },
      { id: 'mock-timeline-product-roadmap-sync', title: 'Согласование', assignee: 'Иван Петров', startWeek: 4, span: 3 },
    ],
  },
  {
    id: 'mock-timeline-logging',
    title: 'Логирование сервера',
    tone: 'positive',
    stages: [
      { id: 'mock-timeline-logging-backend', title: 'Backend', assignee: 'Влад Утробин', startWeek: 1, span: 2 },
      { id: 'mock-timeline-logging-review', title: 'Review', assignee: 'Мария Орлова', startWeek: 3, span: 1 },
      { id: 'mock-timeline-logging-rollout', title: 'Rollout', assignee: 'Олег Ким', startWeek: 4, span: 2 },
    ],
  },
  {
    id: 'mock-timeline-stakeholders',
    title: 'Stakeholder review',
    tone: 'info',
    stages: [
      { id: 'mock-timeline-stakeholders-draft', title: 'Draft', assignee: 'Иван Петров', startWeek: 0, span: 3, lane: 1 },
      { id: 'mock-timeline-stakeholders-feedback', title: 'Feedback', assignee: 'Анна Смирнова', startWeek: 2, span: 2, lane: 2 },
      { id: 'mock-timeline-stakeholders-final', title: 'Final notes', assignee: 'Мария Орлова', startWeek: 5, span: 2, lane: 1 },
    ],
  },
  {
    id: 'mock-timeline-jira-cleanup',
    title: 'Jira cleanup',
    tone: 'info',
    stages: [
      { id: 'mock-timeline-jira-cleanup-triage', title: 'Triage', assignee: 'Олег Ким', startWeek: 2, span: 1 },
      { id: 'mock-timeline-jira-cleanup-update', title: 'Обновление статусов', assignee: 'Анна Смирнова', startWeek: 3, span: 2 },
      { id: 'mock-timeline-jira-cleanup-report', title: 'Отчёт', assignee: 'Влад Утробин', startWeek: 6, span: 1 },
    ],
  },
];

function mapComplexity(complexity: string): AnalyticsTaskComplexity {
  return complexity === 'EASY' ? 'easy' : 'hard';
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
    complexity: mapComplexity(task.complexity),
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

export function AnalyticsPage() {
  const queryClient = useQueryClient();
  const [filters, setFilters] = useState<AnalyticsFiltersValue>({ team: 'all', period: 'week' });
  const [updatingTaskId, setUpdatingTaskId] = useState<string | null>(null);
  const teamKey = filters.team === 'all' ? undefined : filters.team;
  const weekStart = getWeekStartForPeriod(filters.period);
  const dashboardTasksQueryKey = ['dashboard-tasks', teamKey, weekStart] as const;
  const dashboardStatsQueryKey = ['dashboard-stats', teamKey, weekStart] as const;
  const teamsQuery = useQuery({ queryKey: ['teams'], queryFn: fetchTeams });
  const dashboardTasksQuery = useQuery({
    queryKey: dashboardTasksQueryKey,
    queryFn: () => fetchDashboardTasks({ teamKey, weekStart }),
  });
  const dashboardStatsQuery = useQuery({
    queryKey: dashboardStatsQueryKey,
    queryFn: () => fetchDashboardStats({ teamKey, weekStart }),
  });
  const complexityMutation = useMutation({
    mutationFn: ({ taskId, complexity }: { taskId: string; complexity: AnalyticsTaskComplexity }) =>
      updateTaskComplexity(taskId, complexity === 'easy' ? 'EASY' : 'HARD'),
    onMutate: ({ taskId }) => setUpdatingTaskId(taskId),
    onSuccess: (updatedTask) => {
      queryClient.setQueryData<DashboardTaskResponse[]>(dashboardTasksQueryKey, (currentTasks) =>
        currentTasks?.map((task) => (task.jiraIssueKey === updatedTask.jiraIssueKey ? updatedTask : task)),
      );
    },
    onSettled: () => setUpdatingTaskId(null),
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
  const tasks = useMemo(() => (dashboardTasksQuery.data ?? []).map(mapDashboardTask), [dashboardTasksQuery.data]);
  const comparisonMetrics = useMemo(() => buildComparisonMetrics(dashboardStatsQuery.data), [dashboardStatsQuery.data]);
  const completedTasksByPerson = MOCK_COMPLETED_TASKS_BY_PERSON;
  const activeTasksByPerson = MOCK_ACTIVE_TASKS_BY_PERSON;
  const timelineWeeks = useMemo(() => createTimelineWeeks(), []);
  const timelineMonths = useMemo(() => createTimelineMonths(timelineWeeks), [timelineWeeks]);
  const timelineRows = MOCK_TIMELINE_ROWS;

  const handleComplexityChange = (taskId: string, complexity: AnalyticsTaskComplexity) => {
    complexityMutation.mutate({ taskId, complexity });
  };

  return (
    <div className={styles.page}>
      <div className={styles.topSection}>
        <header className={styles.header}>
          <AnalyticsFilters teamOptions={teamOptions} value={filters} onChange={setFilters} />
          <div className={styles.externalLinks}>
            <Button
              className={styles.linkButton}
              href="https://jira.alfabank.ru"
              leftAddons={<img alt="" className={styles.serviceIcon} src={jiraIconUrl} />}
              size={32}
              target="_blank"
              view="transparent"
            >
              Jira
            </Button>
            <Button
              className={styles.linkButton}
              href="https://confluence.alfabank.ru"
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
            <AnalyticsTasksTable
              tasks={tasks}
              updatingTaskId={updatingTaskId}
              onComplexityChange={handleComplexityChange}
            />
          </>
        )}
        {complexityMutation.isError && (
          <p className={styles.error} role="alert">
            {getApiErrorMessage(complexityMutation.error, 'Не удалось обновить сложность')}
          </p>
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
