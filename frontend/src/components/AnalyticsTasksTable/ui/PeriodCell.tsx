import { Typography } from '@alfalab/core-components/typography';
import clsx from 'clsx';
import styles from '../AnalyticsTasksTable.module.css';

type PeriodCellProps = {
  period: string;
  critical?: boolean;
};

export function PeriodCell({ period, critical = false }: PeriodCellProps) {
  return (
    <Typography.Text className={clsx(critical && styles.periodCritical)} tag="span" view="primary-small" weight={"medium"}>
      {period}
    </Typography.Text>
  );
}
