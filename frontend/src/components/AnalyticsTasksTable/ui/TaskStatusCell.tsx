import { Status, type StatusProps } from '@alfalab/core-components/status';
import type { AnalyticsTaskStatus } from '../../../types/analytics';
import styles from '../AnalyticsTasksTable.module.css';

const STATUS_META: Record<AnalyticsTaskStatus, { label: string; color: StatusProps['color'] }> = {
  open: { label: 'Открыта', color: 'green' },
  closed: { label: 'Закрыта', color: 'blue' },
};

type TaskStatusCellProps = {
  status: AnalyticsTaskStatus;
};

export function TaskStatusCell({ status }: TaskStatusCellProps) {
  const statusData = STATUS_META[status];

  return (
    <Status
      className={styles.status}
      color={statusData.color}
      shape="rounded"
      size={24}
      uppercase={false}
      view="muted-alt"
    >
      {statusData.label}
    </Status>
  );
}
