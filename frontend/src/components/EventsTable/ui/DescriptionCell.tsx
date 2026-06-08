import { Typography } from '@alfalab/core-components/typography';
import styles from '../EventsTable.module.css';

type DescriptionCellProps = {
  description: string;
};

export function DescriptionCell({ description }: DescriptionCellProps) {
  return (
    <Typography.Text className={styles.description} tag="div" view="primary-small">
      {description}
    </Typography.Text>
  );
}
