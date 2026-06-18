import { Table } from '@alfalab/core-components/table';
import clsx from 'clsx';
import { DescriptionCell } from './ui/DescriptionCell';
import { EventTitleCell } from './ui/EventTitleCell';
import { PerformersCell } from './ui/PerformersCell';
import { PriorityCell } from './ui/PriorityCell';
import { SelectionCell } from './ui/SelectionCell';
import { SourceCell } from './ui/SourceCell';
import type { EventsTableProps } from './types';
import styles from './EventsTable.module.css';

export function EventsTable({ events, onToggleEvent }: EventsTableProps) {
  return (
    <section className={styles.surface} aria-label="Список значимых событий">
      <div className={styles.viewport}>
        <Table className={styles.table} wrapper={false}>
          <Table.THead>
            <Table.THeadCell className={styles.checkboxHeadCell} width="3rem" aria-label="Выбор" />
            <Table.THeadCell align={"center"} className={clsx(styles.headCell, styles.priority)} width="13%">
              Приоритет
            </Table.THeadCell>
            <Table.THeadCell className={styles.headCell} width="27%">
              Событие
            </Table.THeadCell>
            <Table.THeadCell className={styles.headCell} width="27%">
              Исполнители
            </Table.THeadCell>
            <Table.THeadCell className={styles.headCell} width="18%">
              Описание
            </Table.THeadCell>
            <Table.THeadCell className={styles.headCell} width="22%">
              Ссылка
            </Table.THeadCell>
          </Table.THead>
          <Table.TBody>
            {events.map((event) => (
              <Table.TRow key={event.id} className={clsx(event.selected && styles.rowAcknowledged)}>
                <Table.TCell className={clsx(styles.cell, styles.checkboxCell)}>
                  <SelectionCell
                    checked={event.selected ?? false}
                    eventTitle={event.title}
                    onChange={(checked) => onToggleEvent(event.id, checked)}
                  />
                </Table.TCell>
                <Table.TCell className={styles.cell} align={"center"}>
                  <PriorityCell priority={event.priority} />
                </Table.TCell>
                <Table.TCell className={styles.cell}>
                  <EventTitleCell title={event.title} />
                </Table.TCell>
                <Table.TCell className={styles.cell}>
                  <PerformersCell performers={event.performers} />
                </Table.TCell>
                <Table.TCell className={styles.cell}>
                  <DescriptionCell description={event.description} />
                </Table.TCell>
                <Table.TCell className={styles.cell}>
                  <SourceCell sourceUrl={event.sourceUrl} />
                </Table.TCell>
              </Table.TRow>
            ))}
          </Table.TBody>
        </Table>
      </div>
    </section>
  );
}
