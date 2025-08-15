// API client wrapper for MMS backend
// - Uses credentials: 'include' so backend can set httpOnly cookies
// - Base URL is configurable via Vite env VITE_API_BASE_URL

export const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
export const SIGNIN_PATH = import.meta.env.VITE_SIGNIN_PATH || '';
export const REGISTER_PATH = import.meta.env.VITE_REGISTER_PATH || '';
export const SIGNOUT_PATH = import.meta.env.VITE_SIGNOUT_PATH || '';
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
} catch (_) {
}

export function setAuthTokens(tokens) {
  accessToken = tokens?.accessToken || tokens?.token || null;
  refreshToken = tokens?.refreshToken || null;
  tokenType = tokens?.tokenType || 'Bearer';
  try {
    localStorage.setItem('mms_auth', JSON.stringify({accessToken, refreshToken, tokenType}));
  } catch (_) {
  }
}

export function clearAuthTokens() {
  accessToken = null;
  refreshToken = null;
  tokenType = 'Bearer';
  try {
    localStorage.removeItem('mms_auth');
  } catch (_) {
  }
}

let refreshInFlight = null;

async function fetchJson(path, options = {}) {
  const url = isAbsoluteUrl(path) ? path : `${API_BASE}${path}`;
  const headers = options.headers ? {...options.headers} : {};
  const opts = {...options, credentials: 'include', headers};

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
        return fetchJson(path, {...options, _retry: true});
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

export function signin({username, password}) {
  if (!SIGNIN_PATH) {
    const err = new Error('Local signin is not configured');
    err.status = 501;
    throw err;
  }
  return fetchJson(SIGNIN_PATH, {
    method: 'POST',
    body: JSON.stringify({username, password}),
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

export function signout() {
  if (!SIGNOUT_PATH) {
    // No server endpoint; clear tokens client-side
    clearAuthTokens();
    return Promise.resolve({ok: true});
  }
  return fetchJson(SIGNOUT_PATH, {method: 'POST'}).finally(() => clearAuthTokens());
}

export function me() {
  return fetchJson('/api/users/me');
}

export function listMembers({page = 0, size = 25} = {}) {
  const qs = `?page=${encodeURIComponent(page)}&size=${encodeURIComponent(size)}`;
  return fetchJson(`/api/members${qs}`).then((data) => {
    if (data && Array.isArray(data.content)) return data.content;
    return Array.isArray(data) ? data : [];
  });
}

export function getMemberByUserId(userId) {
  return fetchJson(`/api/members/user/${encodeURIComponent(userId)}`);
}

export function createMember(payload) {
  // Create a new member; payload shape depends on backend DTO
  return fetchJson('/api/members', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  });
}

// Member management (admin-only endpoints; guarded at UI via roles)
export async function updateMember(memberId, payload) {
  // Try a standard endpoint; caller may optionally pre-validate via validateEndpoint
  return fetchJson(`/api/members/${encodeURIComponent(memberId)}`, {
    method: 'PUT',
    body: JSON.stringify(payload || {}),
  });
}

export async function deleteMember(memberId) {
  return fetchJson(`/api/members/${encodeURIComponent(memberId)}`, {
    method: 'DELETE',
  });
}

export async function deactivateMember(memberId) {
  // Try action endpoint; if unsupported (405), fall back to updating isActive=false
  try {
    return await fetchJson(`/api/members/${encodeURIComponent(memberId)}/deactivate`, { method: 'POST' });
  } catch (err) {
    if (err && (err.status === 404 || err.status === 405 || err.status === 501)) {
      // Fallback: PUT with isActive=false
      return fetchJson(`/api/members/${encodeURIComponent(memberId)}`, {
        method: 'PUT',
        body: JSON.stringify({ isActive: false }),
      });
    }
    throw err;
  }
}

export async function activateMember(memberId) {
  // Symmetric helper in case activation is needed elsewhere
  try {
    return await fetchJson(`/api/members/${encodeURIComponent(memberId)}/activate`, { method: 'POST' });
  } catch (err) {
    if (err && (err.status === 404 || err.status === 405 || err.status === 501)) {
      return fetchJson(`/api/members/${encodeURIComponent(memberId)}`, {
        method: 'PUT',
        body: JSON.stringify({ isActive: true }),
      });
    }
    throw err;
  }
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

export function uploadAvatar(file) {
  const form = new FormData();
  form.append('file', file);
  return fetchJson('/api/users/me/avatar', {
    method: 'POST',
    body: form,
  });
}

export async function getMyAvatarBlob() {
  const headers = {};
  if (accessToken) headers['Authorization'] = `${tokenType} ${accessToken}`;
  const res = await fetch(`${API_BASE}/api/users/me/avatar`, {
    method: 'GET',
    headers,
    credentials: 'include',
  });
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    const err = new Error(text || res.statusText);
    err.status = res.status;
    throw err;
  }
  return res.blob();
}

export function getPreferences() {
  return fetchJson('/api/user/preferences');
}

export async function refreshTokens() {
  if (refreshInFlight) return refreshInFlight;
  if (!refreshToken) throw new Error('No refresh token');

  const refreshUrl = isAbsoluteUrl(REFRESH_PATH) ? REFRESH_PATH : `${API_BASE}${REFRESH_PATH}`;
  const urlWithParam = `${refreshUrl}?refreshToken=${encodeURIComponent(refreshToken)}`;

  refreshInFlight = fetch(urlWithParam, {
    method: 'POST',
    credentials: 'include',
    headers: {'Content-Type': 'application/json'},
  })
    .then(async (res) => {
      const text = await res.text();
      let data = null;
      try {
        data = text ? JSON.parse(text) : null;
      } catch (_) {
        data = text || null;
      }
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

// Users listing for linking members
export async function listUsers({page = 0, size = 50, q} = {}) {
  const params = new URLSearchParams();
  params.set('page', String(page));
  params.set('size', String(size));
  if (q) params.set('q', q);
  const qs = `?${params.toString()}`;
  // Try common endpoints; normalize response to an array
  const tryPaths = [
    `/api/users${qs}`,
    `/api/admin/users${qs}`,
    q ? `/api/users/search?${new URLSearchParams({ q }).toString()}` : null,
  ].filter(Boolean);
  let lastErr;
  for (const path of tryPaths) {
    try {
      const data = await fetchJson(path);
      if (data && Array.isArray(data.content)) return data.content;
      if (Array.isArray(data)) return data;
      // Some APIs return { items: [] }
      if (data && Array.isArray(data.items)) return data.items;
      // Fallback: unknown shape, return empty to avoid breaking UI
      return [];
    } catch (e) {
      lastErr = e;
      if (!(e && (e.status === 404 || e.status === 405 || e.status === 501))) throw e;
      // Otherwise, try the next path
    }
  }
  // If all attempts failed, throw the last error
  throw lastErr || new Error('User listing endpoint not available');
}
