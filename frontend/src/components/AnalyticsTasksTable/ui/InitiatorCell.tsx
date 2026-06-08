import { Typography } from '@alfalab/core-components/typography';
import type { AnalyticsTask } from '../../../types/analytics';
import styles from '../AnalyticsTasksTable.module.css';

type InitiatorCellProps = {
  initiator: AnalyticsTask['initiator'];
};

export function InitiatorCell({ initiator }: InitiatorCellProps) {
  return (
    <div className={styles.initiator}>
      <Typography.Text tag="div" view="primary-small" weight="medium">
        {initiator.role}
      </Typography.Text>
      <Typography.Text tag="div" view="primary-small">
        {initiator.name}
      </Typography.Text>
    </div>
  );
}
