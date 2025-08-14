const TOKEN_KEY = 'mms.token'
const REFRESH_KEY = 'mms.refresh'
const USER_KEY = 'mms.user'

export function saveAuth(token, refreshToken, user) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken)
  if (user) localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_KEY)
}

export function getUser() {
  const s = localStorage.getItem(USER_KEY)
  if (!s) return null
  try { return JSON.parse(s) } catch { return null }
}

export function isAuthenticated() {
  return !!getToken()
}

export function signOut() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(USER_KEY)
}

export function decodeJwt(token) {
  try {
    const payload = token.split('.')[1]
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    return JSON.parse(json)
  } catch {
    return null
  }
}

