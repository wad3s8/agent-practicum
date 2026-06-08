import type { AnalyticsTask, AnalyticsTaskComplexity } from '../../types/analytics';

export type AnalyticsTasksTableProps = {
  tasks: AnalyticsTask[];
  updatingTaskId?: string | null;
  onComplexityChange?: (taskId: string, complexity: AnalyticsTaskComplexity) => void;
};
