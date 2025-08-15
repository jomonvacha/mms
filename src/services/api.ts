// Minimal fetch helpers for the UI flows
// - Uses credentials: 'include' to send cookies if present
// - Throws on non-2xx with a friendly Error containing message and status

export type User = {
  id: string;
  firstName: string;
  lastName: string;
  email?: string;
  avatarUrl?: string;
  displayName?: string;
};

export type UserPreferences = {
  theme: 'system' | 'light' | 'dark';
  language: string;
  emailNotifications: boolean;
};

async function request<T = unknown>(input: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers || {});
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  const res = await fetch(input, {...init, headers, credentials: 'include'});
  let data: any = null;
  const text = await res.text().catch(() => '');
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }
  if (!res.ok) {
    const msg = (data && (data.message || data.error || data.detail)) || res.statusText || 'Request failed';
    const err = new Error(msg) as Error & { status?: number; data?: any };
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return data as T;
}

export async function logout(): Promise<void> {
  await request('/api/auth/logout', {method: 'POST'});
}

export async function updateProfile(input: {
  firstName: string;
  lastName: string;
  displayName?: string;
  avatarUrl?: string;
}): Promise<User> {
  return request<User>('/api/user/profile', {method: 'POST', body: JSON.stringify(input)});
}

export async function updatePassword(input: { currentPassword: string; newPassword: string; }): Promise<void> {
  await request('/api/user/password', {method: 'POST', body: JSON.stringify(input)});
}

export async function updatePreferences(input: UserPreferences): Promise<UserPreferences> {
  return request<UserPreferences>('/api/user/preferences', {method: 'POST', body: JSON.stringify(input)});
}

