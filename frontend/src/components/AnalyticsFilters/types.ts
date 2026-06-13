import type { SelectProps } from '@alfalab/core-components/select';

export type AnalyticsFiltersValue = {
  team: string;
  period: string;
};

export type AnalyticsFiltersProps = {
  value: AnalyticsFiltersValue;
  teamOptions?: SelectProps['options'];
  onChange: (value: AnalyticsFiltersValue) => void;
};
