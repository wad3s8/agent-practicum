import type { SignificantEvent } from '../../types/events';

export type EventsTableProps = {
  events: SignificantEvent[];
  onToggleEvent: (eventId: string, checked: boolean) => void;
};
