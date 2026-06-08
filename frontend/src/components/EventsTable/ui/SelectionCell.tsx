import { Checkbox } from '@alfalab/core-components/checkbox';

type SelectionCellProps = {
  checked: boolean;
  eventTitle: string;
  onChange: (checked: boolean) => void;
};

export function SelectionCell({ checked, eventTitle, onChange }: SelectionCellProps) {
  return (
    <Checkbox
      aria-label={`Выбрать событие: ${eventTitle}`}
      checked={checked}
      size={20}
      onChange={(_, payload) => onChange(payload.checked)}
    />
  );
}
