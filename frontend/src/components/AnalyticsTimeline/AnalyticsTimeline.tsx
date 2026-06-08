import type { CSSProperties } from 'react';
import { Typography } from '@alfalab/core-components/typography';
import clsx from 'clsx';
import type { AnalyticsTimelineProps } from './types';
import { CurrentDateLine } from './ui/CurrentDateLine';
import styles from './AnalyticsTimeline.module.css';

const MIN_WEEK_COLUMN_WIDTH = '152px';
const TITLE_COLUMN_WIDTH = 'minmax(210px, 1.35fr)';

const ROW_TONE_CLASS_NAMES = {
  info: styles.infoRow,
  negative: styles.negativeRow,
  positive: styles.positiveRow,
} as const;

function getRowLanes(row: AnalyticsTimelineProps['rows'][number]) {
  return Math.max(1, ...row.stages.map((stage) => stage.lane ?? 1));
}

export function AnalyticsTimeline({ currentDate, months, rows, weeks }: AnalyticsTimelineProps) {
  const columnsTemplate = `${TITLE_COLUMN_WIDTH} repeat(${weeks.length}, minmax(${MIN_WEEK_COLUMN_WIDTH}, 1fr))`;
  const gridStyle = { '--analytics-timeline-columns': columnsTemplate } as CSSProperties;

  return (
    <section className={styles.section} aria-labelledby="analytics-timeline-title">
      <div className={styles.surface}>
        <div className={styles.viewport}>
          <div className={styles.monthRow} style={gridStyle}>
            <span className={styles.cornerCell} aria-hidden="true" />
            {months.map((month) => (
              <Typography.Text
                key={month.id}
                className={styles.monthCell}
                style={{ gridColumn: `${month.startWeek + 2} / span ${month.span}` }}
                tag="span"
                view="primary-small"
                weight="bold"
              >
                {month.label}
              </Typography.Text>
            ))}
          </div>
          <div className={styles.timelineArea} style={gridStyle}>
            <CurrentDateLine currentDate={currentDate} weeks={weeks} />
            <div className={styles.weekRow}>
              <span className={styles.cornerCell} aria-hidden="true" />
              {weeks.map((week, weekIndex) => (
                <div key={week.id} className={styles.weekCell} style={{ gridColumn: weekIndex + 2 }}>
                  <Typography.Text tag="span" view="secondary-small" weight="bold">
                    {week.label}
                  </Typography.Text>
                  <Typography.Text className={styles.weekDates} tag="span" view="secondary-small">
                    {week.dates}
                  </Typography.Text>
                </div>
              ))}
            </div>
            <div className={styles.rows}>
              {rows.map((row) => {
                const lanes = getRowLanes(row);

                return (
                  <div
                    key={row.id}
                    className={clsx(styles.bodyRow, ROW_TONE_CLASS_NAMES[row.tone])}
                    style={{ '--analytics-timeline-lanes': lanes } as CSSProperties}
                  >
                    <Typography.Text
                      className={styles.rowTitle}
                      style={{ gridRow: `1 / span ${lanes}` }}
                      tag="span"
                      view="primary-small"
                      weight="medium"
                    >
                      {row.title}
                    </Typography.Text>
                    {row.stages.map((stage) => (
                      <div
                        key={stage.id}
                        className={clsx(styles.stage, stage.isCritical && styles.criticalStage)}
                        style={{
                          gridColumn: `${stage.startWeek + 2} / span ${stage.span}`,
                          gridRow: stage.lane ?? 1,
                        }}
                      >
                        <Typography.Text className={styles.stageTitle} tag="span" view="primary-small" weight="bold">
                          {stage.title}
                        </Typography.Text>
                        {stage.assignee && (
                          <Typography.Text className={styles.stageAssignee} tag="span" view="secondary-small">
                            {stage.assignee}
                          </Typography.Text>
                        )}
                      </div>
                    ))}
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
