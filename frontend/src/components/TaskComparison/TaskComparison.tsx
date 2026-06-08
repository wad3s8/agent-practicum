import { Typography } from '@alfalab/core-components/typography';
import clsx from 'clsx';
import type { TaskComparisonProps } from './types';
import styles from './TaskComparison.module.css';

const TONE_CLASS_NAMES = {
  neutral: undefined,
  info: styles.cardInfo,
  accent: styles.cardAccent,
} as const;

export function TaskComparison({ metrics }: TaskComparisonProps) {
  return (
    <section className={styles.section} aria-labelledby="task-comparison-title">
      <Typography.Title
        className={styles.title}
        id="task-comparison-title"
        tag="h2"
        view="xsmall"
        weight="bold"
      >
        Сводка по задачам
      </Typography.Title>
      <div className={styles.cards}>
        {metrics.map((metric) => (
          <article key={metric.id} className={clsx(styles.card, TONE_CLASS_NAMES[metric.tone])}>
            <div className={styles.topLine}>
              <span className={styles.dot} />
              <Typography.Text tag="span" view="primary-small" weight="bold">
                {metric.label}
              </Typography.Text>
              <Typography.Text className={styles.value} tag="span" view="primary-small" weight="bold">
                {metric.value}
              </Typography.Text>
            </div>
            <div className={styles.deltaLine}>
              <Typography.Text className={styles.deltaLabel} tag="span" view="secondary-small">
                {metric.deltaLabel}
              </Typography.Text>
              <Typography.Text className={styles.deltaValue} tag="span" view="secondary-small">
                {metric.deltaValue}
              </Typography.Text>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
