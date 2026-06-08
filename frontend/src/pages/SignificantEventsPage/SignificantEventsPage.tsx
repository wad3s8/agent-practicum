import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  acknowledgeEvent,
  fetchSignificantEvents,
  fetchTeams,
  getApiErrorMessage,
  type SignificantEventResponse,
} from '../../api/client';
import { EventsFilters } from '../../components/EventsFilters/EventsFilters';
import type { EventsFiltersValue, FilterOption } from '../../components/EventsFilters/types';
import { EventsTable } from '../../components/EventsTable/EventsTable';
import type { EventPriority, SignificantEvent } from '../../types/events';
import { getWeekStartForPeriod } from '../../utils/dates';
import styles from './SignificantEventsPage.module.css';

const ALL_TEAMS_OPTION: FilterOption = { key: 'all', content: 'Все команды' };

function mapPriority(priority: string): EventPriority {
  if (priority === 'RED') {
    return 'high';
  }

  if (priority === 'YELLOW') {
    return 'medium';
  }

  return 'low';
}

function mapEvent(event: SignificantEventResponse): SignificantEvent {
  return {
    id: event.jiraIssueKey,
    priority: mapPriority(event.priority),
    title: event.taskName || event.jiraIssueKey,
    performers: event.assignees ?? [],
    description: event.problem || event.teamName,
    sourceUrl: event.jiraUrl,
    selected: event.acknowledged,
  };
}

export function SignificantEventsPage() {
  const queryClient = useQueryClient();
  const [filters, setFilters] = useState<EventsFiltersValue>({ team: 'all', period: 'week' });
  const teamKey = filters.team === 'all' ? undefined : filters.team;
  const weekStart = getWeekStartForPeriod(filters.period);
  const eventsQueryKey = ['significant-events', teamKey, weekStart] as const;
  const teamsQuery = useQuery({ queryKey: ['teams'], queryFn: fetchTeams });
  const eventsQuery = useQuery({
    queryKey: eventsQueryKey,
    queryFn: () => fetchSignificantEvents({ teamKey, weekStart }),
  });
  const acknowledgeMutation = useMutation({
    mutationFn: acknowledgeEvent,
    onSuccess: (updatedEvent) => {
      queryClient.setQueryData<SignificantEventResponse[]>(eventsQueryKey, (currentEvents) =>
        currentEvents?.map((event) => (event.jiraIssueKey === updatedEvent.jiraIssueKey ? updatedEvent : event)),
      );
    },
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
  const events = useMemo(() => (eventsQuery.data ?? []).map(mapEvent), [eventsQuery.data]);

  const handleToggleEvent = (eventId: string) => {
    if (acknowledgeMutation.isPending) {
      return;
    }

    acknowledgeMutation.mutate(eventId);
  };

  return (
    <div className={styles.page}>
      <div className={styles.filters}>
        <EventsFilters teamOptions={teamOptions} value={filters} onChange={setFilters} />
      </div>

      {eventsQuery.isLoading && <p className={styles.state}>Загружаем события...</p>}
      {eventsQuery.isError && (
        <p className={styles.error} role="alert">
          {getApiErrorMessage(eventsQuery.error, 'Не удалось загрузить события')}
        </p>
      )}
      {!eventsQuery.isLoading && !eventsQuery.isError && (
        <>
          {events.length === 0 && <p className={styles.state}>Событий по выбранным фильтрам нет</p>}
          <EventsTable events={events} onToggleEvent={handleToggleEvent} />
        </>
      )}
      {acknowledgeMutation.isError && (
        <p className={styles.error} role="alert">
          {getApiErrorMessage(acknowledgeMutation.error, 'Не удалось обновить событие')}
        </p>
      )}
    </div>
  );
}
