import type { ElementType } from 'react';
import type { ROUTES } from '../../constants/routes';

export type SidebarItem = {
  id: string;
  label: string;
  icon: ElementType<{ className?: string }>;
  route: (typeof ROUTES)[keyof typeof ROUTES];
};
