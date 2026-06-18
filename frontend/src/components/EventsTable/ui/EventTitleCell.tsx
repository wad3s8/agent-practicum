import { Typography } from '@alfalab/core-components/typography';
import styles from '../EventsTable.module.css';

type EventTitleCellProps = {
  title: string;
};

export function EventTitleCell({ title }: EventTitleCellProps) {
  return (
    <Typography.Text className={styles.title} tag="div" view="primary-medium" weight="regular">
      {title}
    </Typography.Text>
  );
}
