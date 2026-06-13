import { Sidebar } from '../Sidebar/Sidebar';
import type { AppShellProps } from './types';
import styles from './AppShell.module.css';

export function AppShell({ children }: AppShellProps) {
  return (
    <div className={styles.shell}>
      <Sidebar />
      <main className={styles.content}>{children}</main>
    </div>
  );
}
