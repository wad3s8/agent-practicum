export type AnalyticsTaskStatus = 'open' | 'closed';

export type AnalyticsTaskPriority = 'Highest' | 'High' | 'Medium' | 'Low' | 'Lowest' | null;

export type AnalyticsTask = {
  id: string;
  initiator: {
    role: string;
    name: string;
  };
  title: string;
  priority: AnalyticsTaskPriority;
  period: string;
  periodCritical?: boolean;
  performers: string[];
  sourceUrl: string;
  status: AnalyticsTaskStatus;
};

export type TaskComparisonMetric = {
  id: string;
  label: string;
  value: number;
  deltaLabel: string;
  deltaValue: number;
  tone: 'neutral' | 'info' | 'accent';
};

export type PersonTasksMetric = {
  id: string;
  name: string;
  easy: number;
  hard: number;
};

export type AnalyticsTimelineTone = 'info' | 'negative' | 'positive' | 'accent';

export type AnalyticsTimelineMonth = {
  id: string;
  label: string;
  startWeek: number;
  span: number;
};

export type AnalyticsTimelineWeek = {
  id: string;
  label: string;
  dates: string;
  startDate: string;
  endDate: string;
};

export type AnalyticsTimelineStage = {
  id: string;
  title: string;
  assignee?: string;
  startWeek: number;
  span: number;
  lane?: number;
  isCritical?: boolean;
};

export type AnalyticsTimelineRow = {
  id: string;
  title: string;
  tone: AnalyticsTimelineTone;
  stages: AnalyticsTimelineStage[];
};
