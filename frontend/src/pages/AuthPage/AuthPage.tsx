import { useState } from 'react';
import { Button } from '@alfalab/core-components/button';
import { useMutation } from '@tanstack/react-query';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { getApiErrorMessage, login, register, type AuthRequest } from '../../api/client';
import Logo from '../../components/Logo/Logo';
import { ROUTES } from '../../constants/routes';
import { useAuthState } from '../../hooks/useAuthState';
import { setAccessToken } from '../../utils/auth';
import styles from './AuthPage.module.css';

type LocationState = {
  from?: Location;
};

export function AuthPage() {
  const authenticated = useAuthState();
  const navigate = useNavigate();
  const location = useLocation();
  const [credentials, setCredentials] = useState<AuthRequest>({ login: '', password: '' });
  const [authAction, setAuthAction] = useState<'login' | 'register'>('login');
  const locationState = location.state as LocationState | null;
  const returnPath = locationState?.from?.pathname ?? ROUTES.SIGNIFICANT_EVENTS;
  const authMutation = useMutation({
    mutationFn: ({ action, request }: { action: 'login' | 'register'; request: AuthRequest }) =>
      action === 'login' ? login(request) : register(request),
    onSuccess: (response) => {
      setAccessToken(response.token);
      navigate(returnPath, { replace: true });
    },
  });

  if (authenticated) {
    return <Navigate replace to={returnPath} />;
  }

  const canSubmit = credentials.login.trim() && credentials.password.trim();

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!canSubmit || authMutation.isPending) {
      return;
    }

    setAuthAction('login');
    authMutation.mutate({ action: 'login', request: credentials });
  };

  const handleRegister = () => {
    if (!canSubmit || authMutation.isPending) {
      return;
    }

    setAuthAction('register');
    authMutation.mutate({ action: 'register', request: credentials });
  };

  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <Logo />
        <form className={styles.form} onSubmit={handleSubmit}>
          <label className={styles.field}>
            <span className={styles.label}>Логин</span>
            <input
              autoComplete="username"
              className={styles.input}
              value={credentials.login}
              onChange={(event) => setCredentials((current) => ({ ...current, login: event.target.value }))}
            />
          </label>
          <label className={styles.field}>
            <span className={styles.label}>Пароль</span>
            <input
              autoComplete="current-password"
              className={styles.input}
              type="password"
              value={credentials.password}
              onChange={(event) => setCredentials((current) => ({ ...current, password: event.target.value }))}
            />
          </label>
          {authMutation.isError && (
            <p className={styles.error} role="alert">
              {getApiErrorMessage(authMutation.error, 'Не удалось авторизоваться')}
            </p>
          )}
          <div className={styles.actions}>
            <Button className={styles.loginButton} disabled={!canSubmit || authMutation.isPending} size={48} type="submit" view="primary">
              {authMutation.isPending && authAction === 'login' ? 'Входим...' : 'Войти'}
            </Button>
            <Button className={styles.loginButton} disabled={!canSubmit || authMutation.isPending} size={48} view="secondary" onClick={handleRegister}>
              {authMutation.isPending && authAction === 'register' ? 'Создаем...' : 'Зарегистрироваться'}
            </Button>
          </div>
        </form>
      </div>
    </main>
  );
}
