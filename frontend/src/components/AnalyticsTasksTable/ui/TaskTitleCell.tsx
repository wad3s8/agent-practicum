import { Typography } from '@alfalab/core-components/typography';
import styles from '../AnalyticsTasksTable.module.css';

type TaskTitleCellProps = {
  title: string;
};

export function TaskTitleCell({ title }: TaskTitleCellProps) {
  return (
    <Typography.Text className={styles.taskTitle} tag="div" view="primary-small" weight="medium">
      {title}
    </Typography.Text>
  );
}
