// API client wrapper for MMS backend
// - Uses credentials: 'include' so backend can set httpOnly cookies
// - Base URL is configurable via Vite env VITE_API_BASE_URL

export const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
export const LOGIN_PATH = import.meta.env.VITE_LOGIN_PATH || '';
export const REGISTER_PATH = import.meta.env.VITE_REGISTER_PATH || '';
export const LOGOUT_PATH = import.meta.env.VITE_LOGOUT_PATH || '';

async function fetchJson(path, options = {}) {
  const url = `${API_BASE}${path}`;
  const headers = options.headers ? { ...options.headers } : {};
  const opts = { ...options, credentials: 'include', headers };

  if (opts.body && !headers['Content-Type'] && !(opts.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  const res = await fetch(url, opts);
  let data = null;
  const text = await res.text();
  try {
    data = text ? JSON.parse(text) : null;
  } catch (_) {
    data = text || null;
  }

  if (!res.ok) {
    const message = (data && (data.message || data.error || data.detail)) || res.statusText || 'Request failed';
    const err = new Error(message);
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return data;
}

export function login({ email, password }) {
  if (!LOGIN_PATH) {
    const err = new Error('Local login is not configured');
    err.status = 501;
    throw err;
  }
  return fetchJson(LOGIN_PATH, {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
}

export function register(data) {
  if (!REGISTER_PATH) {
    const err = new Error('Local registration is not configured');
    err.status = 501;
    throw err;
  }
  return fetchJson(REGISTER_PATH, {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export function logout() {
  if (!LOGOUT_PATH) {
    const err = new Error('Logout endpoint is not configured');
    err.status = 501;
    throw err;
  }
  return fetchJson(LOGOUT_PATH, { method: 'POST' });
}

export function me() {
  return fetchJson('/api/users/me');
}

export function listMembers() {
  return fetchJson('/api/members');
}
