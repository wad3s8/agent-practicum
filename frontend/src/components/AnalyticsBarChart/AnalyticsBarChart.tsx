import { Typography } from '@alfalab/core-components/typography';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { AnalyticsBarChartProps } from './types';
import styles from './AnalyticsBarChart.module.css';

const CHART_MARGIN = {
  top: 12,
  right: 8,
  bottom: 0,
  left: -28,
};

const Y_AXIS_TICKS = [0, 2, 4, 6, 8];

type PersonTickProps = {
  x?: number;
  y?: number;
  payload?: {
    value: string;
  };
};

function PersonTick({ x = 0, y = 0, payload }: PersonTickProps) {
  const [surname = '', name = ''] = payload?.value.split(' ') ?? [];

  return (
    <text className={styles.tick} textAnchor="middle" x={x} y={y + 18}>
      <tspan x={x}>{surname}</tspan>
      <tspan dy="14" x={x}>
        {name}
      </tspan>
    </text>
  );
}

export function AnalyticsBarChart({ title, data }: AnalyticsBarChartProps) {
  return (
    <section className={styles.card} aria-label={title}>
      <header className={styles.header}>
        <Typography.Title className={styles.title} tag="h2" view="xsmall" weight="bold">
          {title}
        </Typography.Title>
        <div className={styles.legend} aria-label="Легенда графика">
          <Typography.Text className={styles.legendItem} tag="span" view="secondary-small">
            <span className={styles.easyMarker} />
            Лёгкие
          </Typography.Text>
          <Typography.Text className={styles.legendItem} tag="span" view="secondary-small">
            <span className={styles.hardMarker} />
            Сложные
          </Typography.Text>
        </div>
      </header>
      <div className={styles.chart}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart barCategoryGap="28%" barGap={6} data={data} margin={CHART_MARGIN}>
            <CartesianGrid stroke="var(--app-color-chart-grid)" vertical={false} />
            <XAxis
              axisLine={false}
              dataKey="name"
              height={58}
              interval={0}
              tick={<PersonTick />}
              tickLine={false}
            />
            <YAxis
              axisLine={false}
              domain={[0, 8]}
              tick={{ fill: 'var(--app-color-chart-axis)', fontSize: 12 }}
              ticks={Y_AXIS_TICKS}
              tickLine={false}
              width={34}
            />
            <Tooltip
              contentStyle={{
                background: 'var(--app-color-chart-tooltip-bg)',
                borderColor: 'var(--app-color-border-subtle)',
                borderRadius: 'var(--border-radius-8)',
              }}
              cursor={{ fill: 'var(--app-color-chart-cursor)' }}
            />
            <Bar
              dataKey="easy"
              fill="var(--app-color-chart-easy)"
              isAnimationActive={false}
              maxBarSize={24}
              name="Лёгкие"
              radius={[6, 6, 0, 0]}
            />
            <Bar
              dataKey="hard"
              fill="var(--app-color-chart-hard)"
              isAnimationActive={false}
              maxBarSize={24}
              name="Сложные"
              radius={[6, 6, 0, 0]}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </section>
  );
}
