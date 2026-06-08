import { Typography } from '@alfalab/core-components/typography';
import { PencilSIcon } from '@alfalab/icons-glyph/PencilSIcon';
import type { AnalyticsTaskComplexity } from '../../../types/analytics';
import styles from '../AnalyticsTasksTable.module.css';

const COMPLEXITY_LABELS: Record<AnalyticsTaskComplexity, string> = {
  easy: 'Лёгкая',
  hard: 'Сложная',
};

type ComplexityCellProps = {
  complexity: AnalyticsTaskComplexity;
  disabled?: boolean;
  onToggle?: () => void;
};

export function ComplexityCell({ complexity, disabled = false, onToggle }: ComplexityCellProps) {
  if (onToggle) {
    return (
      <button className={styles.complexity} disabled={disabled} type="button" onClick={onToggle}>
        <Typography.Text tag="span" view="primary-small">
          {COMPLEXITY_LABELS[complexity]}
        </Typography.Text>
        <PencilSIcon className={styles.complexityIcon} />
      </button>
    );
  }

  return (
    <div className={styles.complexity}>
      <Typography.Text tag="span" view="primary-small">
        {COMPLEXITY_LABELS[complexity]}
      </Typography.Text>
      <PencilSIcon className={styles.complexityIcon} />
    </div>
  );
}
