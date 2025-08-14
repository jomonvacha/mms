// API client wrapper for MMS backend
// - Uses credentials: 'include' so backend can set httpOnly cookies
// - Base URL is configurable via Vite env VITE_API_BASE_URL

export const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
export const LOGIN_PATH = import.meta.env.VITE_LOGIN_PATH || '';
export const REGISTER_PATH = import.meta.env.VITE_REGISTER_PATH || '';
export const LOGOUT_PATH = import.meta.env.VITE_LOGOUT_PATH || '';
export const REFRESH_PATH = import.meta.env.VITE_REFRESH_PATH || '/api/auth/refresh';

function isAbsoluteUrl(u) {
  return /^https?:\/\//i.test(u);
}

// Simple token store (memory + localStorage)
let accessToken = null;
let refreshToken = null;
let tokenType = 'Bearer';

try {
  const saved = JSON.parse(localStorage.getItem('mms_auth') || 'null');
  if (saved) {
    accessToken = saved.accessToken || null;
    refreshToken = saved.refreshToken || null;
    tokenType = saved.tokenType || 'Bearer';
  }
} catch (_) {}

export function setAuthTokens(tokens) {
  accessToken = tokens?.accessToken || tokens?.token || null;
  refreshToken = tokens?.refreshToken || null;
  tokenType = tokens?.tokenType || 'Bearer';
  try {
    localStorage.setItem('mms_auth', JSON.stringify({ accessToken, refreshToken, tokenType }));
  } catch (_) {}
}

export function clearAuthTokens() {
  accessToken = null;
  refreshToken = null;
  tokenType = 'Bearer';
  try { localStorage.removeItem('mms_auth'); } catch (_) {}
}

let refreshInFlight = null;

async function fetchJson(path, options = {}) {
  const url = isAbsoluteUrl(path) ? path : `${API_BASE}${path}`;
  const headers = options.headers ? { ...options.headers } : {};
  const opts = { ...options, credentials: 'include', headers };

  if (opts.body && !headers['Content-Type'] && !(opts.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  if (!options.noAuth && accessToken && !headers['Authorization']) {
    headers['Authorization'] = `${tokenType} ${accessToken}`;
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
    // Attempt token refresh once on 401
    const canRefresh = err.status === 401 && refreshToken && !options._retry;
    if (canRefresh) {
      try {
        await refreshTokens();
        return fetchJson(path, { ...options, _retry: true });
      } catch (_) {
        clearAuthTokens();
      }
    }
    throw err;
  }
  return data;
}

// Validate that an endpoint exists and likely supports the intended method.
// Uses OPTIONS and checks the Allow header when present.
export async function validateEndpoint(path, method = 'POST') {
  try {
    const url = isAbsoluteUrl(path) ? path : `${API_BASE}${path}`;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 4000);
    const res = await fetch(url, {
      method: 'OPTIONS',
      credentials: 'include',
      signal: controller.signal,
    });
    clearTimeout(timeout);
    if (!res.ok) return false;
    const allow = res.headers.get('Allow');
    if (!allow) return true; // many servers don't set it on OPTIONS
    return allow.toUpperCase().includes(method.toUpperCase());
  } catch (_) {
    return false;
  }
}

export function login({ username, password }) {
  if (!LOGIN_PATH) {
    const err = new Error('Local login is not configured');
    err.status = 501;
    throw err;
  }
  return fetchJson(LOGIN_PATH, {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  }).then((res) => {
    if (res && (res.accessToken || res.token)) {
      setAuthTokens({
        accessToken: res.accessToken || res.token,
        refreshToken: res.refreshToken,
        tokenType: res.tokenType || 'Bearer',
      });
    }
    return res;
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
    // No server endpoint; clear tokens client-side
    clearAuthTokens();
    return Promise.resolve({ ok: true });
  }
  return fetchJson(LOGOUT_PATH, { method: 'POST' }).finally(() => clearAuthTokens());
}

export function me() {
  return fetchJson('/api/users/me');
}

export function listMembers({ page = 0, size = 25 } = {}) {
  const qs = `?page=${encodeURIComponent(page)}&size=${encodeURIComponent(size)}`;
  return fetchJson(`/api/members${qs}`).then((data) => {
    if (data && Array.isArray(data.content)) return data.content;
    return Array.isArray(data) ? data : [];
  });
}

export function getMemberByUserId(userId) {
  return fetchJson(`/api/members/user/${encodeURIComponent(userId)}`);
}

export function updateMe(payload) {
  return fetchJson('/api/users/me', {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function changePassword(payload) {
  return fetchJson('/api/users/me/password', {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function updatePreferences(payload) {
  return fetchJson('/api/user/preferences', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function refreshTokens() {
  if (refreshInFlight) return refreshInFlight;
  if (!refreshToken) throw new Error('No refresh token');

  const refreshUrl = isAbsoluteUrl(REFRESH_PATH) ? REFRESH_PATH : `${API_BASE}${REFRESH_PATH}`;
  const urlWithParam = `${refreshUrl}?refreshToken=${encodeURIComponent(refreshToken)}`;

  refreshInFlight = fetch(urlWithParam, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
  })
    .then(async (res) => {
      const text = await res.text();
      let data = null;
      try { data = text ? JSON.parse(text) : null; } catch (_) { data = text || null; }
      if (!res.ok) {
        const e = new Error((data && (data.message || data.error || data.detail)) || res.statusText);
        e.status = res.status;
        throw e;
      }
      if (!data || !(data.accessToken || data.token)) {
        throw new Error('Invalid refresh response');
      }
      setAuthTokens({
        accessToken: data.accessToken || data.token,
        refreshToken: data.refreshToken,
        tokenType: data.tokenType || 'Bearer',
      });
      return data;
    })
    .finally(() => {
      refreshInFlight = null;
    });
  return refreshInFlight;
}
