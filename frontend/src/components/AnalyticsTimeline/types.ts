import type { AnalyticsTimelineMonth, AnalyticsTimelineRow, AnalyticsTimelineWeek } from '../../types/analytics';

export type AnalyticsTimelineProps = {
  currentDate?: Date;
  months: AnalyticsTimelineMonth[];
  rows: AnalyticsTimelineRow[];
  weeks: AnalyticsTimelineWeek[];
};
