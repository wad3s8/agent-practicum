import { Status, type StatusProps } from '@alfalab/core-components/status';
import type { EventPriority } from '../../../types/events';
import styles from '../EventsTable.module.css';

const PRIORITY_META: Record<EventPriority, { label: string; color: StatusProps['color'] }> = {
  high: { label: 'Высокий', color: 'red' },
  medium: { label: 'Средний', color: 'orange' },
  low: { label: 'Низкий', color: 'green' },
};

type PriorityCellProps = {
  priority: EventPriority;
};

export function PriorityCell({ priority }: PriorityCellProps) {
  const priorityData = PRIORITY_META[priority];

  return (
    <Status
      className={styles.status}
      color={priorityData.color}
      shape="rounded"
      size={24}
      uppercase={false}
      view="muted-alt"
    >
      {priorityData.label}
    </Status>
  );
}
