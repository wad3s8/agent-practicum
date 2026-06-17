import { Status, type StatusProps } from '@alfalab/core-components/status';
import type { AnalyticsTaskPriority } from '../../../types/analytics';
import styles from '../AnalyticsTasksTable.module.css';

type PriorityMeta = { label: string; color: StatusProps['color'] };

const PRIORITY_META: Record<NonNullable<AnalyticsTaskPriority>, PriorityMeta> = {
  Highest: { label: 'Высокий', color: 'red' },
  High:    { label: 'Высокий', color: 'red' },
  Medium:  { label: 'Высокий', color: 'red' },
  Low:     { label: 'Низкий',  color: 'blue' },
  Lowest:  { label: 'Низкий',  color: 'blue' },
};

const PRIORITY_NONE: PriorityMeta = { label: 'Нет', color: 'grey' };

type PriorityCellProps = {
  priority: AnalyticsTaskPriority;
};

export function PriorityCell({ priority }: PriorityCellProps) {
  const meta = priority ? (PRIORITY_META[priority] ?? PRIORITY_NONE) : PRIORITY_NONE;

  return (
    <Status
      className={styles.status}
      color={meta.color}
      shape="rounded"
      size={24}
      uppercase={false}
      view="muted-alt"
    >
      {meta.label}
    </Status>
  );
}
