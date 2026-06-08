import { Select, type SelectProps } from '@alfalab/core-components/select';
import type { EventsFiltersProps } from './types';
import styles from './EventsFilters.module.css';

type SelectChangeHandler = NonNullable<SelectProps['onChange']>;

const DEFAULT_TEAM_OPTIONS: SelectProps['options'] = [
  { key: 'all', content: 'Все команды' },
];

const PERIOD_OPTIONS: SelectProps['options'] = [
  { key: 'week', content: 'Неделя' },
  { key: 'month', content: 'Месяц' },
  { key: 'quarter', content: 'Квартал' },
];

export function EventsFilters({ value, teamOptions = DEFAULT_TEAM_OPTIONS, onChange }: EventsFiltersProps) {
  const handleTeamChange: SelectChangeHandler = ({ selected }) => {
    onChange({ ...value, team: selected?.key ?? 'all' });
  };

  const handlePeriodChange: SelectChangeHandler = ({ selected }) => {
    onChange({ ...value, period: selected?.key ?? 'week' });
  };

  return (
    <div className={styles.filters}>
      <Select
        className={styles.teamSelect}
        fieldClassName={styles.selectField}
        options={teamOptions}
        selected={value.team}
        size={40}
        optionsSize={40}
        onChange={handleTeamChange}
      />
      <Select
        className={styles.periodSelect}
        fieldClassName={styles.selectField}
        options={PERIOD_OPTIONS}
        selected={value.period}
        size={40}
        optionsSize={40}
        onChange={handlePeriodChange}
      />
    </div>
  );
}
