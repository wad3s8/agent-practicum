import { useEffect, useState } from 'react';
import { AUTH_CHANGE_EVENT, AUTH_UNAUTHORIZED_EVENT } from '../constants/auth';
import { isAuthenticated } from '../utils/auth';

export function useAuthState() {
  const [authenticated, setAuthenticated] = useState(isAuthenticated);

  useEffect(() => {
    const syncAuthState = () => setAuthenticated(isAuthenticated());

    window.addEventListener(AUTH_CHANGE_EVENT, syncAuthState);
    window.addEventListener(AUTH_UNAUTHORIZED_EVENT, syncAuthState);
    window.addEventListener('storage', syncAuthState);

    return () => {
      window.removeEventListener(AUTH_CHANGE_EVENT, syncAuthState);
      window.removeEventListener(AUTH_UNAUTHORIZED_EVENT, syncAuthState);
      window.removeEventListener('storage', syncAuthState);
    };
  }, []);

  return authenticated;
}
