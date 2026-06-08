import { IconButton } from '@alfalab/core-components/icon-button';
import { DoorArrowRightBoldMIcon } from '@alfalab/icons-glyph/DoorArrowRightBoldMIcon';
import { ListMIcon } from '@alfalab/icons-glyph/ListMIcon';
import { NavigationChatMIcon } from '@alfalab/icons-glyph/NavigationChatMIcon';
import { StatsChartMIcon } from '@alfalab/icons-glyph/StatsChartMIcon';
import clsx from 'clsx';
import { useLocation, useNavigate } from 'react-router-dom';
import { ROUTES } from '../../constants/routes';
import { clearAccessToken } from '../../utils/auth';
import type { SidebarItem } from './types';
import styles from './Sidebar.module.css';
import Logo from '../Logo/Logo';

const SIDEBAR_ITEMS: SidebarItem[] = [
  { id: 'tasks', label: 'Значимые события', icon: ListMIcon, route: ROUTES.SIGNIFICANT_EVENTS },
  { id: 'analytics', label: 'Аналитика', icon: StatsChartMIcon, route: ROUTES.ANALYTICS },
  { id: 'chat', label: 'Чат', icon: NavigationChatMIcon, route: ROUTES.CHAT },
];

export function Sidebar() {
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = () => {
    clearAccessToken();
    navigate(ROUTES.AUTH, { replace: true });
  };

  return (
    <aside className={styles.sidebar} aria-label="Основная навигация">
      <div className={styles.top}>
        <Logo />

        <nav className={styles.nav}>
          {SIDEBAR_ITEMS.map((item) => {
            const isActive = location.pathname === item.route;

            return (
              <IconButton
                key={item.id}
                aria-label={item.label}
                className={clsx(styles.navItem, isActive && styles.active)}
                icon={item.icon}
                onClick={() => navigate(item.route)}
                size={48}
                transparentBg
                view="transparent"
              />
            );
          })}
        </nav>
      </div>

      <IconButton
        aria-label="Выйти"
        className={styles.exitButton}
        icon={DoorArrowRightBoldMIcon}
        onClick={handleLogout}
        size={48}
        transparentBg
        view="transparent"
      />
    </aside>
  );
}
