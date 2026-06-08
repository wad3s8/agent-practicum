import type { SelectProps } from '@alfalab/core-components/select';

export type FilterOption = NonNullable<SelectProps['options']>[number];

export type EventsFiltersValue = {
  team: string;
  period: string;
};

export type EventsFiltersProps = {
  value: EventsFiltersValue;
  teamOptions?: SelectProps['options'];
  onChange: (value: EventsFiltersValue) => void;
};
