// API client for MMS backend
export const API_BASE = import.meta.env.VITE_API_BASE_URL || ''
export const SIGNIN_PATH = import.meta.env.VITE_SIGNIN_PATH || ''
export const REGISTER_PATH = import.meta.env.VITE_REGISTER_PATH || ''
export const SIGNOUT_PATH = import.meta.env.VITE_SIGNOUT_PATH || ''
export const REFRESH_PATH = import.meta.env.VITE_REFRESH_PATH || '/api/auth/refresh'

function isAbsoluteUrl(u: string) {
  return /^https?:\/\//i.test(u)
}

// Token store — memory + localStorage
let accessToken: string | null = null
let refreshToken: string | null = null
let tokenType = 'Bearer'

const AUTH_KEY = 'platform_auth'

try {
  const saved = JSON.parse(localStorage.getItem(AUTH_KEY) || 'null')
  if (saved) {
    accessToken = saved.accessToken || null
    refreshToken = saved.refreshToken || null
    tokenType = saved.tokenType || 'Bearer'
  }
} catch (_) {}

export function setAuthTokens(tokens: { accessToken?: string; token?: string; refreshToken?: string; tokenType?: string }) {
  accessToken = tokens?.accessToken || tokens?.token || null
  refreshToken = tokens?.refreshToken || null
  tokenType = tokens?.tokenType || 'Bearer'
  try { localStorage.setItem(AUTH_KEY, JSON.stringify({ accessToken, refreshToken, tokenType })) } catch (_) {}
}

export function clearAuthTokens() {
  accessToken = null
  refreshToken = null
  tokenType = 'Bearer'
  try { localStorage.removeItem(AUTH_KEY) } catch (_) {}
}

interface FetchOptions extends RequestInit {
  noAuth?: boolean
  _retry?: boolean
}

export interface ApiError extends Error {
  status?: number
  data?: unknown
  /** Structured field-level errors extracted from the GlobalExceptionHandler's validationErrors array. */
  fieldErrors?: Record<string, string>
}

let refreshInFlight: Promise<unknown> | null = null

async function fetchJson<T = unknown>(path: string, options: FetchOptions = {}): Promise<T> {
  const url = isAbsoluteUrl(path) ? path : `${API_BASE}${path}`
  const headers: Record<string, string> = options.headers ? { ...(options.headers as Record<string, string>) } : {}
  const opts: RequestInit = { ...options, credentials: 'include', headers }

  if (opts.body && !headers['Content-Type'] && !(opts.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
  }
  if (!options.noAuth && accessToken && !headers['Authorization']) {
    headers['Authorization'] = `${tokenType} ${accessToken}`
  }
  if (!headers['X-Trace-Id']) {
    headers['X-Trace-Id'] = 'mms-' + Math.random().toString(36).slice(2, 10) + '-' + Date.now().toString(36)
  }

  const res = await fetch(url, opts)
  const text = await res.text()
  let data: unknown = null
  try { data = text ? JSON.parse(text) : null } catch (_) { data = text || null }

  if (!res.ok) {
    const d = data as Record<string, unknown> | null
    const message = (d && typeof d['message'] === 'string' ? (d['message'] as string) : null)
      || (d && typeof d['error'] === 'string' ? (d['error'] as string) : null)
      || (d && typeof d['detail'] === 'string' ? (d['detail'] as string) : null)
      || res.statusText
      || 'Request failed'
    const err = new Error(message) as ApiError
    err.status = res.status
    err.data = data

    // Extract GlobalExceptionHandler's validationErrors array into a flat
    // { field: message } map so forms can surface per-field errors inline.
    if (d && Array.isArray(d['validationErrors'])) {
      const map: Record<string, string> = {}
      for (const v of d['validationErrors'] as Array<{ field?: string; message?: string }>) {
        if (v?.field && v?.message) map[v.field] = v.message
      }
      if (Object.keys(map).length) err.fieldErrors = map
    }

    if (err.status === 401 && refreshToken && !options._retry) {
      try {
        await refreshTokens()
        return fetchJson<T>(path, { ...options, _retry: true })
      } catch (_) {
        clearAuthTokens()
      }
    }
    throw err
  }
  return data as T
}

export async function validateEndpoint(path: string, method = 'POST'): Promise<boolean> {
  try {
    const url = isAbsoluteUrl(path) ? path : `${API_BASE}${path}`
    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), 4000)
    const res = await fetch(url, { method: 'OPTIONS', credentials: 'include', signal: controller.signal })
    clearTimeout(timeout)
    if (!res.ok) return false
    const allow = res.headers.get('Allow')
    if (!allow) return true
    return allow.toUpperCase().includes(method.toUpperCase())
  } catch (_) {
    return false
  }
}

export function signin({ username, password, totpCode }: { username: string; password: string; totpCode?: string }) {
  if (!SIGNIN_PATH) {
    const err = new Error('Local signin is not configured') as ApiError
    err.status = 501
    throw err
  }
  const body: Record<string, string> = { username, password }
  if (totpCode) body.totpCode = totpCode
  return fetchJson<{ accessToken?: string; token?: string; refreshToken?: string; tokenType?: string }>(SIGNIN_PATH, {
    method: 'POST',
    body: JSON.stringify(body),
  }).then((res) => {
    if (res && (res.accessToken || res.token)) {
      setAuthTokens({ accessToken: res.accessToken || res.token, refreshToken: res.refreshToken, tokenType: res.tokenType || 'Bearer' })
    }
    return res
  })
}

/** Starts a forgot-password flow. Always resolves — backend never leaks whether the email is registered. */
export function forgotPassword(email: string) {
  return fetchJson('/api/auth/forgot-password', { method: 'POST', body: JSON.stringify({ email }), noAuth: true })
}

/** Redeems a password-reset token and sets the new password. Throws on invalid/expired/weak. */
export function resetPassword(token: string, newPassword: string) {
  return fetchJson('/api/auth/reset-password', { method: 'POST', body: JSON.stringify({ token, newPassword }), noAuth: true })
}

/** Redeems an email-verification token (GET with ?token=...). */
export function verifyEmail(token: string) {
  return fetchJson(`/api/auth/verify-email?token=${encodeURIComponent(token)}`, { method: 'GET', noAuth: true })
}

// ── 2FA ────────────────────────────────────────────────────────────────────

export interface TwoFactorSetup {
  secret: string
  otpauthUri: string
  qrDataUri?: string
}

export function twoFactorSetup() {
  return fetchJson<TwoFactorSetup>('/api/users/me/2fa/setup', { method: 'POST' })
}

export function twoFactorEnable(code: string) {
  return fetchJson<{ recoveryCodes: string[] }>('/api/users/me/2fa/enable', {
    method: 'POST',
    body: JSON.stringify({ code }),
  })
}

export function twoFactorDisable(currentPasswordOrCode: string) {
  return fetchJson('/api/users/me/2fa/disable', {
    method: 'POST',
    body: JSON.stringify({ currentPasswordOrCode }),
  })
}

export function register(data: Record<string, unknown>) {
  if (!REGISTER_PATH) {
    const err = new Error('Local registration is not configured') as ApiError
    err.status = 501
    throw err
  }
  return fetchJson(REGISTER_PATH, { method: 'POST', body: JSON.stringify(data) })
}

export function signout() {
  if (!SIGNOUT_PATH) {
    clearAuthTokens()
    return Promise.resolve({ ok: true })
  }
  // Pass the refresh token so the backend can blacklist both tokens until expiry.
  // Use a fire-and-forget POST: we still clear local tokens even if the server is down.
  const url = refreshToken
    ? `${SIGNOUT_PATH}?refreshToken=${encodeURIComponent(refreshToken)}`
    : SIGNOUT_PATH
  return fetchJson(url, { method: 'POST' }).finally(() => clearAuthTokens())
}

export function me() {
  return fetchJson('/api/users/me')
}

export function listMembers({ page = 0, size = 25 } = {}) {
  return fetchJson<{ content: MemberRecord[] }>(`/api/members?page=${page}&size=${size}`).then((data) => {
    if (data && Array.isArray(data.content)) return data.content
    return Array.isArray(data) ? (data as MemberRecord[]) : []
  })
}

export function getMemberByUserId(userId: string) {
  return fetchJson(`/api/members/user/${encodeURIComponent(userId)}`)
}

export function createMember(payload: Record<string, unknown>) {
  return fetchJson('/api/members', { method: 'POST', body: JSON.stringify(payload) })
}

export function updateMember(memberId: string, payload: Record<string, unknown>) {
  return fetchJson(`/api/members/${encodeURIComponent(memberId)}`, { method: 'PUT', body: JSON.stringify(payload) })
}

export function deleteMember(memberId: string) {
  return fetchJson(`/api/members/${encodeURIComponent(memberId)}`, { method: 'DELETE' })
}

export async function deactivateMember(memberId: string) {
  try {
    return await fetchJson(`/api/members/${encodeURIComponent(memberId)}/deactivate`, { method: 'POST' })
  } catch (err) {
    const e = err as ApiError
    if (e && (e.status === 404 || e.status === 405 || e.status === 501)) {
      return fetchJson(`/api/members/${encodeURIComponent(memberId)}`, { method: 'PUT', body: JSON.stringify({ isActive: false }) })
    }
    throw err
  }
}

export function updateMe(payload: Record<string, unknown>) {
  return fetchJson('/api/users/me', { method: 'PUT', body: JSON.stringify(payload) })
}

export function changePassword(payload: { currentPassword: string; newPassword: string }) {
  return fetchJson('/api/users/me/password', { method: 'PUT', body: JSON.stringify(payload) })
}

/**
 * Disconnect from Google/Apple and convert to a local account. The user must
 * set a local password as part of this call. Returns the updated user profile
 * (provider will now be LOCAL).
 */
export function disconnectProvider(newPassword: string) {
  return fetchJson('/api/users/me/disconnect-provider', { method: 'POST', body: JSON.stringify({ newPassword }) })
}

export function updatePreferences(payload: Record<string, unknown>) {
  return fetchJson('/api/user/preferences', { method: 'POST', body: JSON.stringify(payload) })
}

export function uploadAvatar(file: File) {
  const form = new FormData()
  form.append('file', file)
  return fetchJson('/api/users/me/avatar', { method: 'POST', body: form })
}

export async function getMyAvatarBlob(): Promise<Blob> {
  const headers: Record<string, string> = {}
  if (accessToken) headers['Authorization'] = `${tokenType} ${accessToken}`
  const res = await fetch(`${API_BASE}/api/users/me/avatar`, { method: 'GET', headers, credentials: 'include' })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    const err = new Error(text || res.statusText) as ApiError
    err.status = res.status
    throw err
  }
  return res.blob()
}

export function getPreferences() {
  return fetchJson<PrefsRecord>('/api/user/preferences')
}

export async function refreshTokens() {
  if (refreshInFlight) return refreshInFlight
  if (!refreshToken) throw new Error('No refresh token')

  const refreshUrl = isAbsoluteUrl(REFRESH_PATH) ? REFRESH_PATH : `${API_BASE}${REFRESH_PATH}`
  const urlWithParam = `${refreshUrl}?refreshToken=${encodeURIComponent(refreshToken)}`

  refreshInFlight = fetch(urlWithParam, { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' } })
    .then(async (res) => {
      const text = await res.text()
      let data: { accessToken?: string; token?: string; refreshToken?: string; tokenType?: string } | null = null
      try { data = text ? JSON.parse(text) : null } catch (_) {}
      if (!res.ok) throw new Error((data as Record<string, string> | null)?.[`message`] || res.statusText)
      if (!data || !(data.accessToken || data.token)) throw new Error('Invalid refresh response')
      setAuthTokens({ accessToken: data.accessToken || data.token, refreshToken: data.refreshToken, tokenType: data.tokenType || 'Bearer' })
      return data
    })
    .finally(() => { refreshInFlight = null })
  return refreshInFlight
}

// ── Admin user management ─────────────────────────────────────────────────────

export interface AdminUserResponse {
  id: string
  username: string
  email: string
  firstName?: string
  lastName?: string
  phoneNumber?: string | null
  active?: boolean
  roles?: string[]
  hasPassword?: boolean
  provider?: 'LOCAL' | 'GOOGLE' | 'APPLE'
  createdAt?: string
  updatedAt?: string
}

export interface SpringPage<T> {
  content: T[]
  // Spring Boot 3.5 nests these under a `page` key
  page?: { totalElements: number; totalPages: number; number: number; size: number }
  // Flattened versions (populated by normalizeSpringPage)
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

/** Normalizes Spring Boot 3.x nested page response into flat fields. */
function normalizeSpringPage<T>(raw: SpringPage<T>): SpringPage<T> {
  if (raw.page) {
    raw.totalElements = raw.page.totalElements
    raw.totalPages = raw.page.totalPages
    raw.number = raw.page.number
    raw.size = raw.page.size
  }
  if (raw.first === undefined) raw.first = (raw.number ?? 0) === 0
  if (raw.last === undefined) raw.last = (raw.number ?? 0) >= (raw.totalPages ?? 1) - 1
  return raw
}

export function listAdminUsers(
  { page = 0, size = 20, sortBy = 'username', sortDir = 'asc' }:
    { page?: number; size?: number; sortBy?: string; sortDir?: string } = {},
) {
  const params = new URLSearchParams({ page: String(page), size: String(size), sortBy, sortDir })
  return fetchJson<SpringPage<AdminUserResponse>>(`/api/admin/users?${params}`).then(normalizeSpringPage)
}

export function getAdminUser(id: string) {
  return fetchJson<AdminUserResponse>(`/api/admin/users/${encodeURIComponent(id)}`)
}

export function setAdminUserPassword(id: string, newPassword: string) {
  return fetchJson(`/api/admin/users/${encodeURIComponent(id)}/password`, {
    method: 'PUT',
    body: JSON.stringify({ newPassword }),
  })
}

export function sendAdminUserResetLink(id: string) {
  return fetchJson(`/api/admin/users/${encodeURIComponent(id)}/send-reset-link`, {
    method: 'POST',
  })
}

export function setAdminUserMembership(id: string, categoryCode: string, tierCode: string) {
  return fetchJson<MemberRecord>(`/api/admin/users/${encodeURIComponent(id)}/membership`, {
    method: 'PUT',
    body: JSON.stringify({ categoryCode, tierCode }),
  })
}

export function updateAdminUserProfile(
  id: string,
  data: { firstName?: string; lastName?: string; email?: string; phoneNumber?: string },
) {
  return fetchJson<AdminUserResponse>(`/api/admin/users/${encodeURIComponent(id)}/profile`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

export function updateAdminUserRoles(id: string, roles: string[]) {
  return fetchJson<AdminUserResponse>(`/api/admin/users/${encodeURIComponent(id)}/roles`, {
    method: 'PUT',
    body: JSON.stringify({ roles }),
  })
}

export function updateAdminUserStatus(id: string, active: boolean) {
  return fetchJson<AdminUserResponse>(`/api/admin/users/${encodeURIComponent(id)}/status`, {
    method: 'PUT',
    body: JSON.stringify({ active }),
  })
}

export function updateAdminUserProvider(id: string, provider: string) {
  return fetchJson<AdminUserResponse>(`/api/admin/users/${encodeURIComponent(id)}/provider`, {
    method: 'PUT',
    body: JSON.stringify({ provider }),
  })
}

export function deleteAdminUser(id: string) {
  return fetchJson(`/api/admin/users/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export async function listUsers({ page = 0, size = 50, q }: { page?: number; size?: number; q?: string } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (q) params.set('q', q)
  const tryPaths = [`/api/users?${params}`, `/api/admin/users?${params}`].filter(Boolean)
  let lastErr: unknown
  for (const path of tryPaths) {
    try {
      const data = await fetchJson<{ content?: UserRecord[]; items?: UserRecord[] } | UserRecord[]>(path)
      if (data && !Array.isArray(data) && Array.isArray(data.content)) return data.content
      if (Array.isArray(data)) return data
      if (data && !Array.isArray(data) && Array.isArray(data.items)) return data.items
      return []
    } catch (e) {
      lastErr = e
      const err = e as ApiError
      if (!(err && (err.status === 404 || err.status === 405 || err.status === 501))) throw e
    }
  }
  throw lastErr || new Error('User listing endpoint not available')
}

// Shared record types
export interface UserSummary {
  id?: string
  username?: string
  email?: string
  firstName?: string
  lastName?: string
  phoneNumber?: string
  active?: boolean
}

export interface MemberRecord {
  id: string
  membershipId?: string
  userId?: string
  user?: UserSummary
  membershipType?: string
  /** Governance-managed category (e.g. PERSONAL, EDUCATION, ENTERPRISE). */
  categoryCode?: string
  /** Category-scoped tier (e.g. FREE, PRO, MAX). */
  tierCode?: string
  status?: string
  isActive?: boolean
}

export interface UserRecord {
  id: string
  username?: string
  email?: string
  firstName?: string
  lastName?: string
}

// ── Features & role-feature matrix ─────────────────────────────────────────

export interface FeatureRecord {
  id: string
  code: string
  name: string
  description?: string
  category?: string
  icon?: string
  enabled?: boolean
}

export function listFeatures() {
  return fetchJson<FeatureRecord[]>('/api/features')
}

export function getMyFeatures() {
  return fetchJson<FeatureRecord[]>('/api/features/mine')
}

export function createFeature(f: Partial<FeatureRecord>) {
  return fetchJson<FeatureRecord>('/api/features', { method: 'POST', body: JSON.stringify(f) })
}

export function updateFeature(id: string, f: Partial<FeatureRecord>) {
  return fetchJson<FeatureRecord>(`/api/features/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(f) })
}

export function deleteFeature(id: string) {
  return fetchJson(`/api/features/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export function getFeatureMatrix() {
  return fetchJson<Record<string, string[]>>('/api/features/matrix')
}

export function setRoleFeatures(role: string, featureCodes: string[]) {
  return fetchJson(`/api/features/matrix/${encodeURIComponent(role)}`, { method: 'PUT', body: JSON.stringify(featureCodes) })
}

// ── Role management ───────────────────────────────────────────────────────

export interface RoleRecord {
  id: string
  name: string
  displayName?: string
  description?: string
  system?: boolean
  category?: string
}

export function listRoles() {
  return fetchJson<RoleRecord[]>('/api/roles')
}

export function createRole(data: { name: string; displayName?: string; description?: string }) {
  return fetchJson<RoleRecord>('/api/roles', { method: 'POST', body: JSON.stringify(data) })
}

export function updateRole(id: string, data: { displayName?: string; description?: string }) {
  return fetchJson<RoleRecord>(`/api/roles/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(data) })
}

export function deleteRole(id: string) {
  return fetchJson(`/api/roles/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

// ── Membership type management ────────────────────────────────────────

export interface MembershipTypeRecord {
  id: string
  code: string
  displayName?: string
  description?: string
  system?: boolean
}

export function listMembershipTypes() {
  return fetchJson<MembershipTypeRecord[]>('/api/membership-types')
}

export function createMembershipType(data: { code: string; displayName?: string; description?: string }) {
  return fetchJson<MembershipTypeRecord>('/api/membership-types', { method: 'POST', body: JSON.stringify(data) })
}

export function updateMembershipType(id: string, data: { displayName?: string; description?: string }) {
  return fetchJson<MembershipTypeRecord>(`/api/membership-types/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(data) })
}

export function deleteMembershipType(id: string) {
  return fetchJson(`/api/membership-types/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

// ── Locale management ────────────────────────────────────────────────
export interface LocaleOption {
  id: string; type: string; code: string; label: string; sortOrder: number; enabled: boolean
}

export function listLocaleOptions(type: string) {
  return fetchJson<LocaleOption[]>(`/api/admin/locale/${type}`)
}

export function createLocaleOption(data: { type: string; code: string; label: string; sortOrder?: number }) {
  return fetchJson<LocaleOption>('/api/admin/locale', { method: 'POST', body: JSON.stringify(data) })
}

export function updateLocaleOption(id: string, data: { label?: string; sortOrder?: number; enabled?: boolean }) {
  return fetchJson<LocaleOption>(`/api/admin/locale/${id}`, { method: 'PUT', body: JSON.stringify(data) })
}

export function deleteLocaleOption(id: string) {
  return fetchJson(`/api/admin/locale/${id}`, { method: 'DELETE' })
}

export interface PrefsRecord {
  theme?: string
  language?: string
  emailNotifications?: boolean
  navbarDisplay?: string
}

// ── Enforcement Rules management ────────────────────────────────────
export interface EnforcementRule {
  id: string; text: string; tier: string; category: string
  enabledByDefault: boolean; sortOrder: number; active: boolean
}

export function listEnforcementRules() {
  return fetchJson<EnforcementRule[]>('/api/admin/enforcement-rules')
}

export function createEnforcementRule(data: { text: string; tier: string; category: string; enabledByDefault?: boolean }) {
  return fetchJson<EnforcementRule>('/api/admin/enforcement-rules', { method: 'POST', body: JSON.stringify(data) })
}

export function updateEnforcementRule(id: string, data: Record<string, unknown>) {
  return fetchJson<EnforcementRule>('/api/admin/enforcement-rules/' + id, { method: 'PUT', body: JSON.stringify(data) })
}

export function deleteEnforcementRule(id: string) {
  return fetchJson('/api/admin/enforcement-rules/' + id, { method: 'DELETE' })
}

// ── Membership governance (categories + tiers) ────────────────────────────

export interface TierRecord {
  id: string
  categoryCode: string
  tierCode: string
  displayName?: string
  description?: string
  enabled: boolean
  sortOrder: number
  system: boolean
}

export interface CategoryRecord {
  id: string
  code: string
  displayName?: string
  description?: string
  enabled: boolean
  sortOrder: number
  system: boolean
  tiers?: TierRecord[]
}

export function listCategories() {
  return fetchJson<CategoryRecord[]>('/api/governance/categories')
}

export function createCategory(data: { code: string; displayName?: string; description?: string; enabled?: boolean; sortOrder?: number }) {
  return fetchJson<CategoryRecord>('/api/governance/categories', { method: 'POST', body: JSON.stringify(data) })
}

export function updateCategory(code: string, data: { displayName?: string; description?: string; enabled?: boolean; sortOrder?: number }) {
  return fetchJson<CategoryRecord>(`/api/governance/categories/${encodeURIComponent(code)}`, { method: 'PUT', body: JSON.stringify(data) })
}

export function deleteCategory(code: string) {
  return fetchJson(`/api/governance/categories/${encodeURIComponent(code)}`, { method: 'DELETE' })
}

export function createTier(categoryCode: string, data: { tierCode: string; displayName?: string; description?: string; enabled?: boolean; sortOrder?: number }) {
  return fetchJson<TierRecord>(`/api/governance/categories/${encodeURIComponent(categoryCode)}/tiers`, { method: 'POST', body: JSON.stringify(data) })
}

export function updateTier(categoryCode: string, tierCode: string, data: { displayName?: string; description?: string; enabled?: boolean; sortOrder?: number }) {
  return fetchJson<TierRecord>(`/api/governance/categories/${encodeURIComponent(categoryCode)}/tiers/${encodeURIComponent(tierCode)}`, { method: 'PUT', body: JSON.stringify(data) })
}

export function deleteTier(categoryCode: string, tierCode: string) {
  return fetchJson(`/api/governance/categories/${encodeURIComponent(categoryCode)}/tiers/${encodeURIComponent(tierCode)}`, { method: 'DELETE' })
}

// ── Entitlements ──────────────────────────────────────────────────────────

export interface EntitlementRecord {
  id: string
  key: string
  displayName?: string
  description?: string
  valueType: 'BOOLEAN' | 'INTEGER' | 'STRING'
  category?: string
  defaultValue: string
  system: boolean
}

export interface ResolvedEntitlements {
  userId: string
  categoryCode?: string
  tierCode?: string
  categoryEnabled?: boolean
  tierEnabled?: boolean
  entitlements: Record<string, string>
}

export function listEntitlementDefs() {
  return fetchJson<EntitlementRecord[]>('/api/governance/entitlements')
}

export function getTierEntitlements(categoryCode: string, tierCode: string) {
  return fetchJson<Record<string, string>>(`/api/governance/categories/${encodeURIComponent(categoryCode)}/tiers/${encodeURIComponent(tierCode)}/entitlements`)
}

export function setTierEntitlement(categoryCode: string, tierCode: string, data: { entitlementKey: string; value: string }) {
  return fetchJson(`/api/governance/categories/${encodeURIComponent(categoryCode)}/tiers/${encodeURIComponent(tierCode)}/entitlements`, { method: 'PUT', body: JSON.stringify(data) })
}

export function removeTierEntitlement(categoryCode: string, tierCode: string, entitlementKey: string) {
  return fetchJson(`/api/governance/categories/${encodeURIComponent(categoryCode)}/tiers/${encodeURIComponent(tierCode)}/entitlements/${encodeURIComponent(entitlementKey)}`, { method: 'DELETE' })
}

export function getMyEntitlements() {
  return fetchJson<ResolvedEntitlements>('/api/entitlements/mine')
}

export function getEntitlementsForUser(userId: string) {
  return fetchJson<ResolvedEntitlements>(`/api/entitlements/user/${encodeURIComponent(userId)}`)
}

// ── Platform settings (admin) ────────────────────────────────────────────

export interface AppSetting {
  id: string
  key: string
  value: string
  description?: string
  updatedBy?: string
  updatedAt?: string
}

export function listAppSettings() {
  return fetchJson<AppSetting[]>('/api/admin/settings')
}

export function updateAppSetting(key: string, value: string) {
  return fetchJson<AppSetting>(`/api/admin/settings/${encodeURIComponent(key)}`, {
    method: 'PUT',
    body: JSON.stringify({ value }),
  })
}

// ── Signup invite codes (admin) ──────────────────────────────────────────

export interface SignupInviteCode {
  id: string
  code: string
  description?: string
  maxUses?: number | null
  usedCount: number
  expiresAt?: string | null
  active: boolean
  createdBy?: string
  createdAt?: string
}

export function listSignupInvites() {
  return fetchJson<SignupInviteCode[]>('/api/admin/signup-invites')
}

export function createSignupInvite(data: {
  code: string; description?: string; maxUses?: number | null; expiresAt?: string | null
}) {
  return fetchJson<SignupInviteCode>('/api/admin/signup-invites', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function updateSignupInvite(id: string, data: {
  active?: boolean; maxUses?: number | null; expiresAt?: string | null; description?: string
}) {
  return fetchJson<SignupInviteCode>(`/api/admin/signup-invites/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

export function deleteSignupInvite(id: string) {
  return fetchJson(`/api/admin/signup-invites/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

// ── AI Models registry (phase 2) ──────────────────────────────────

export interface AiModelProviderRecord {
  id: string
  code: string
  displayName?: string
  baseUrl?: string
  enabled: boolean
}

export interface AiModelRecord {
  id: string
  code: string
  providerCode: string
  displayName: string
  description?: string
  qualityLevel?: number
  costLevel?: number
  status: 'ENABLED' | 'DISABLED' | 'DEPRECATED'
  lockStyle?: 'HIDE' | 'LOCK'
  deprecatedAt?: string
  replacesModelCode?: string
  sortOrder?: number
  createdBy?: string
  updatedBy?: string
}

export interface AiModelBindingRecord {
  id: string
  modelCode: string
  categoryCode: string
  tierCode: string
  isDefault: boolean
  labelOverride?: string
}

export interface AiModelAuditRecord {
  id: string
  actorId?: string
  action: string
  modelCode: string
  binding?: { categoryCode: string; tierCode: string }
  beforeJson?: string
  afterJson?: string
  ip?: string
  userAgent?: string
  at: string
}

export interface CreateAiModelPayload {
  code: string
  providerCode: string
  displayName: string
  description?: string
  qualityLevel?: number
  costLevel?: number
  sortOrder?: number
  lockStyle?: 'HIDE' | 'LOCK'
  replacesModelCode?: string
}

export type UpdateAiModelPayload = Partial<Omit<CreateAiModelPayload, 'code'>>

export function listAiProviders() {
  return fetchJson<AiModelProviderRecord[]>('/api/admin/models/providers')
}

export function upsertAiProvider(code: string, data: { displayName?: string; baseUrl?: string; enabled?: boolean }) {
  return fetchJson<AiModelProviderRecord>(`/api/admin/models/providers/${encodeURIComponent(code)}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

export function listAiModels() {
  return fetchJson<AiModelRecord[]>('/api/admin/models')
}

export function getAiModel(code: string) {
  return fetchJson<AiModelRecord>(`/api/admin/models/${encodeURIComponent(code)}`)
}

export function createAiModel(data: CreateAiModelPayload) {
  return fetchJson<AiModelRecord>('/api/admin/models', { method: 'POST', body: JSON.stringify(data) })
}

export function updateAiModel(code: string, data: UpdateAiModelPayload) {
  return fetchJson<AiModelRecord>(`/api/admin/models/${encodeURIComponent(code)}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

export function setAiModelStatus(code: string, status: 'ENABLED' | 'DISABLED' | 'DEPRECATED') {
  return fetchJson<AiModelRecord>(`/api/admin/models/${encodeURIComponent(code)}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}

export function deleteAiModel(code: string) {
  return fetchJson(`/api/admin/models/${encodeURIComponent(code)}`, { method: 'DELETE' })
}

export function listAiModelBindings(code: string) {
  return fetchJson<AiModelBindingRecord[]>(`/api/admin/models/${encodeURIComponent(code)}/bindings`)
}

export function setAiModelBinding(code: string, category: string, tier: string, data: { isDefault?: boolean; labelOverride?: string }) {
  return fetchJson<AiModelBindingRecord>(
    `/api/admin/models/${encodeURIComponent(code)}/bindings/${encodeURIComponent(category)}/${encodeURIComponent(tier)}`,
    { method: 'PUT', body: JSON.stringify(data) }
  )
}

export function removeAiModelBinding(code: string, category: string, tier: string) {
  return fetchJson(
    `/api/admin/models/${encodeURIComponent(code)}/bindings/${encodeURIComponent(category)}/${encodeURIComponent(tier)}`,
    { method: 'DELETE' }
  )
}

export interface AiModelAuditPage {
  content: AiModelAuditRecord[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export function listAiModelAudit(params: { modelCode?: string; actorId?: string; page?: number; size?: number } = {}) {
  const q = new URLSearchParams()
  if (params.modelCode) q.set('modelCode', params.modelCode)
  if (params.actorId) q.set('actorId', params.actorId)
  q.set('page', String(params.page ?? 0))
  q.set('size', String(params.size ?? 50))
  return fetchJson<AiModelAuditPage>(`/api/admin/models/audit?${q.toString()}`)
}
