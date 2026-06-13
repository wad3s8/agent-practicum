import { Typography } from '@alfalab/core-components/typography';
import styles from '../AnalyticsTasksTable.module.css';

type PerformersCellProps = {
  performers: string[];
};

export function PerformersCell({ performers }: PerformersCellProps) {
  return (
    <div className={styles.performers}>
      {performers.map((performer, index) => (
        <Typography.Text key={performer} tag="div" view="primary-small">
          {index + 1}. {performer}
        </Typography.Text>
      ))}
    </div>
  );
}
