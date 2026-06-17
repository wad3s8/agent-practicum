import { AUTH_CHANGE_EVENT, AUTH_TOKEN_KEY } from '../constants/auth';

export function getAccessToken() {
  return window.localStorage.getItem(AUTH_TOKEN_KEY);
}

export function isAuthenticated() {
  const token = getAccessToken();

  return Boolean(token) && !isAccessTokenExpired(token);
}

function decodeJwtPayload(token: string) {
  const [, payload] = token.split('.');

  if (!payload) {
    return null;
  }

  const normalizedPayload = payload.replace(/-/g, '+').replace(/_/g, '/');
  const paddedPayload = normalizedPayload.padEnd(Math.ceil(normalizedPayload.length / 4) * 4, '=');

  return JSON.parse(window.atob(paddedPayload)) as { exp?: number };
}

export function isAccessTokenExpired(token: string | null = getAccessToken()) {
  if (!token) {
    return true;
  }

  try {
    const payload = decodeJwtPayload(token);

    return typeof payload?.exp !== 'number' || payload.exp * 1000 <= Date.now();
  } catch {
    return true;
  }
}

export function setAccessToken(token: string) {
  window.localStorage.setItem(AUTH_TOKEN_KEY, token);
  window.dispatchEvent(new CustomEvent(AUTH_CHANGE_EVENT));
}

export function clearAccessToken() {
  window.localStorage.removeItem(AUTH_TOKEN_KEY);
  window.dispatchEvent(new CustomEvent(AUTH_CHANGE_EVENT));
}
