import { useMemo, useState } from 'react';
import { Button } from '@alfalab/core-components/button';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import confluenceIconUrl from '../../assets/icons/confluence.svg';
import jiraIconUrl from '../../assets/icons/jira.svg';
import {
  fetchDashboardTasks,
  fetchTeams,
  getApiErrorMessage,
  updateTaskComplexity,
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

function buildComparisonMetrics(tasks: AnalyticsTask[]): TaskComparisonMetric[] {
  const activeTasks = tasks.filter((task) => task.status === 'open').length;
  const closedTasks = tasks.filter((task) => task.status === 'closed').length;
  const overdueTasks = tasks.filter((task) => task.periodCritical).length;

  return [
    { id: 'active', label: 'В работе', value: activeTasks, deltaLabel: 'из выборки', deltaValue: tasks.length, tone: 'neutral' },
    { id: 'overdue', label: 'Просрочено', value: overdueTasks, deltaLabel: 'требуют внимания', deltaValue: overdueTasks, tone: 'info' },
    { id: 'done', label: 'Завершено', value: closedTasks, deltaLabel: 'из выборки', deltaValue: tasks.length, tone: 'accent' },
  ];
}

function createEmptyPersonMetric(id: string): PersonTasksMetric {
  return { id, name: 'Нет данных', easy: 0, hard: 0 };
}

function buildPersonMetrics(tasks: AnalyticsTask[], status: AnalyticsTask['status'], emptyId: string): PersonTasksMetric[] {
  const metrics = new Map<string, PersonTasksMetric>();

  tasks
    .filter((task) => task.status === status)
    .forEach((task) => {
      task.performers.forEach((performer) => {
        if (!performer) {
          return;
        }

        const id = performer.toLowerCase().replace(/\s+/g, '-');
        const metric = metrics.get(id) ?? { id, name: performer, easy: 0, hard: 0 };

        metric[task.complexity] += 1;
        metrics.set(id, metric);
      });
    });

  const sortedMetrics = [...metrics.values()]
    .sort((firstMetric, secondMetric) => secondMetric.easy + secondMetric.hard - firstMetric.easy - firstMetric.hard)
    .slice(0, 5);

  return sortedMetrics.length > 0 ? sortedMetrics : [createEmptyPersonMetric(emptyId)];
}

function parseIsoLocalDate(value: string) {
  const [year, month, day] = value.split('-').map(Number);

  return new Date(year, month - 1, day);
}

function parseDisplayDate(value: string | null) {
  if (!value) {
    return null;
  }

  const [day, month] = value.split('.').map(Number);

  if (!day || !month) {
    return null;
  }

  return new Date(new Date().getFullYear(), month - 1, day);
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

function findTimelineWeekIndex(date: Date, weeks: AnalyticsTimelineWeek[]) {
  for (let weekIndex = 0; weekIndex < weeks.length; weekIndex += 1) {
    const weekStart = parseIsoLocalDate(weeks[weekIndex].startDate);
    const weekEnd = addDays(parseIsoLocalDate(weeks[weekIndex].endDate), 1);

    if (date >= weekStart && date < weekEnd) {
      return weekIndex;
    }
  }

  return date < parseIsoLocalDate(weeks[0].startDate) ? 0 : weeks.length - 1;
}

function buildTimelineRows(tasks: DashboardTaskResponse[], weeks: AnalyticsTimelineWeek[]): AnalyticsTimelineRow[] {
  return tasks.slice(0, 8).map((task) => {
    const startDate = parseDisplayDate(task.startDate);
    const dueDate = parseDisplayDate(task.dueDate);
    const startWeek = startDate ? findTimelineWeekIndex(startDate, weeks) : 0;
    const endWeek = dueDate ? findTimelineWeekIndex(dueDate, weeks) : startWeek;
    const span = Math.min(weeks.length - startWeek, Math.max(1, endWeek - startWeek + 1));
    const closed = task.status === 'CLOSED';

    return {
      id: task.jiraIssueKey,
      title: task.taskName || task.jiraIssueKey,
      tone: task.deadlineOverdue ? 'negative' : closed ? 'positive' : 'info',
      stages: [
        {
          id: `${task.jiraIssueKey}-stage`,
          title: closed ? 'Завершено' : 'В работе',
          assignee: task.assignees?.[0],
          startWeek,
          span,
          isCritical: task.deadlineOverdue,
        },
      ],
    };
  });
}

export function AnalyticsPage() {
  const queryClient = useQueryClient();
  const [filters, setFilters] = useState<AnalyticsFiltersValue>({ team: 'all', period: 'week' });
  const [updatingTaskId, setUpdatingTaskId] = useState<string | null>(null);
  const teamKey = filters.team === 'all' ? undefined : filters.team;
  const weekStart = getWeekStartForPeriod(filters.period);
  const dashboardTasksQueryKey = ['dashboard-tasks', teamKey, weekStart] as const;
  const teamsQuery = useQuery({ queryKey: ['teams'], queryFn: fetchTeams });
  const dashboardTasksQuery = useQuery({
    queryKey: dashboardTasksQueryKey,
    queryFn: () => fetchDashboardTasks({ teamKey, weekStart }),
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
  const comparisonMetrics = useMemo(() => buildComparisonMetrics(tasks), [tasks]);
  const completedTasksByPerson = useMemo(() => buildPersonMetrics(tasks, 'closed', 'empty-closed'), [tasks]);
  const activeTasksByPerson = useMemo(() => buildPersonMetrics(tasks, 'open', 'empty-open'), [tasks]);
  const timelineWeeks = useMemo(() => createTimelineWeeks(), []);
  const timelineMonths = useMemo(() => createTimelineMonths(timelineWeeks), [timelineWeeks]);
  const timelineRows = useMemo(
    () => buildTimelineRows(dashboardTasksQuery.data ?? [], timelineWeeks),
    [dashboardTasksQuery.data, timelineWeeks],
  );

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

      <TaskComparison metrics={comparisonMetrics} />
      <div className={styles.chartsGrid}>
        <AnalyticsBarChart data={completedTasksByPerson} title="Количество выполненных задач на человека" />
        <AnalyticsBarChart data={activeTasksByPerson} title="Количество активных задач на человека" />
      </div>
      <AnalyticsTimeline months={timelineMonths} rows={timelineRows} weeks={timelineWeeks} />
    </div>
  );
}
