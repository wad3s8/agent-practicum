import type { CSSProperties } from 'react';
import type { AnalyticsTimelineWeek } from '../../../types/analytics';
import styles from './CurrentDateLine.module.css';

const DAY_IN_MS = 24 * 60 * 60 * 1000;

type CurrentDateLineProps = {
  weeks: AnalyticsTimelineWeek[];
  currentDate?: Date;
};

function parseLocalDate(date: string) {
  const [year, month, day] = date.split('-').map(Number);

  return new Date(year, month - 1, day);
}

function getStartOfDay(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function getCurrentDatePosition(weeks: AnalyticsTimelineWeek[], currentDate: Date) {
  const current = getStartOfDay(currentDate).getTime();

  for (let weekIndex = 0; weekIndex < weeks.length; weekIndex += 1) {
    const week = weeks[weekIndex];
    const start = parseLocalDate(week.startDate).getTime();
    const end = parseLocalDate(week.endDate).getTime() + DAY_IN_MS;

    if (current >= start && current < end) {
      const offset = ((current - start) / (end - start)) * 100 + 20;

      return {
        column: weekIndex + 2,
        offset,
      };
    }
  }

  return null;
}

export function CurrentDateLine({ weeks, currentDate = new Date() }: CurrentDateLineProps) {
  const position = getCurrentDatePosition(weeks, currentDate);

  if (!position) {
    return null;
  }

  return (
    <div className={styles.overlay} aria-hidden="true">
      <span
        className={styles.column}
        style={
          {
            '--analytics-current-date-column': position.column,
            '--analytics-current-date-offset': `${position.offset}%`,
          } as CSSProperties
        }
      >
        <span className={styles.line}>
          <span className={styles.label}>Сегодня</span>
        </span>
      </span>
    </div>
  );
}
