export type EventPriority = 'high' | 'medium' | 'low';

export type SignificantEvent = {
  id: string;
  priority: EventPriority;
  title: string;
  performers: string[];
  description: string;
  sourceUrl: string;
  selected?: boolean;
};
