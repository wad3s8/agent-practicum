import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { ROUTES } from '../../constants/routes';
import { AppShell } from '../AppShell/AppShell';
import { useAuthState } from '../../hooks/useAuthState';

export function AuthLayout() {
  const authenticated = useAuthState();
  const location = useLocation();

  if (!authenticated) {
    return <Navigate replace to={ROUTES.AUTH} state={{ from: location }} />;
  }

  return (
    <AppShell>
      <Outlet />
    </AppShell>
  );
}
