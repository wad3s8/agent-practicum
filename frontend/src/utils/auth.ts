import { AUTH_CHANGE_EVENT, AUTH_TOKEN_KEY } from '../constants/auth';

export function getAccessToken() {
  return window.localStorage.getItem(AUTH_TOKEN_KEY);
}

export function isAuthenticated() {
  return Boolean(getAccessToken());
}

export function setAccessToken(token: string) {
  window.localStorage.setItem(AUTH_TOKEN_KEY, token);
  window.dispatchEvent(new CustomEvent(AUTH_CHANGE_EVENT));
}

export function clearAccessToken() {
  window.localStorage.removeItem(AUTH_TOKEN_KEY);
  window.dispatchEvent(new CustomEvent(AUTH_CHANGE_EVENT));
}
