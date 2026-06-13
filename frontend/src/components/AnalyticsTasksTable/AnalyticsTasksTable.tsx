import { Table } from '@alfalab/core-components/table';
import { ComplexityCell } from './ui/ComplexityCell';
import { InitiatorCell } from './ui/InitiatorCell';
import { PerformersCell } from './ui/PerformersCell';
import { PeriodCell } from './ui/PeriodCell';
import { SourceCell } from './ui/SourceCell';
import { TaskStatusCell } from './ui/TaskStatusCell';
import { TaskTitleCell } from './ui/TaskTitleCell';
import type { AnalyticsTasksTableProps } from './types';
import styles from './AnalyticsTasksTable.module.css';

export function AnalyticsTasksTable({ tasks, updatingTaskId, onComplexityChange }: AnalyticsTasksTableProps) {
  return (
    <section className={styles.surface} aria-label="Задачи команды">
      <div className={styles.viewport}>
        <Table className={styles.table} wrapper={false}>
          <Table.THead>
            <Table.THeadCell className={styles.headCell} width="18%">
              Инициатор
            </Table.THeadCell>
            <Table.THeadCell className={styles.headCell} width="21%">
              Задача
            </Table.THeadCell>
            <Table.THeadCell className={styles.headCell} width="11%">
              Сложность задачи
            </Table.THeadCell>
            <Table.THeadCell className={styles.headCell} width="11%">
              Сроки
            </Table.THeadCell>
            <Table.THeadCell className={styles.headCell} width="20%">
              Исполнители
            </Table.THeadCell>
            <Table.THeadCell className={styles.headCell} width="9%">
              Ссылка
            </Table.THeadCell>
            <Table.THeadCell className={styles.headCell} width="10%">
              Статус
            </Table.THeadCell>
          </Table.THead>
          <Table.TBody>
            {tasks.map((task) => (
              <Table.TRow key={task.id}>
                <Table.TCell className={styles.cell}>
                  <InitiatorCell initiator={task.initiator} />
                </Table.TCell>
                <Table.TCell className={styles.cell}>
                  <TaskTitleCell title={task.title} />
                </Table.TCell>
                <Table.TCell className={styles.cell}>
                  <ComplexityCell
                    complexity={task.complexity}
                    disabled={updatingTaskId === task.id}
                    onToggle={
                      onComplexityChange
                        ? () => onComplexityChange(task.id, task.complexity === 'easy' ? 'hard' : 'easy')
                        : undefined
                    }
                  />
                </Table.TCell>
                <Table.TCell className={styles.cell}>
                  <PeriodCell critical={task.periodCritical} period={task.period} />
                </Table.TCell>
                <Table.TCell className={styles.cell}>
                  <PerformersCell performers={task.performers} />
                </Table.TCell>
                <Table.TCell className={styles.cell}>
                  <SourceCell sourceUrl={task.sourceUrl} />
                </Table.TCell>
                <Table.TCell className={styles.cell}>
                  <TaskStatusCell status={task.status} />
                </Table.TCell>
              </Table.TRow>
            ))}
          </Table.TBody>
        </Table>
      </div>
    </section>
  );
}
