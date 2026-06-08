import { Typography } from '@alfalab/core-components/typography';
import styles from '../EventsTable.module.css';

type PerformersCellProps = {
  performers: string[];
};

export function PerformersCell({ performers }: PerformersCellProps) {
  return (
    <div className={styles.performers}>
      {performers.map((performer) => (
        <Typography.Text key={performer} tag="div" view="primary-small">
          {performer}
        </Typography.Text>
      ))}
    </div>
  );
}
