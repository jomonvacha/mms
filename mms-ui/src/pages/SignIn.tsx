import { useState, useEffect } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { LogIn, LogOut, Loader2, AlertCircle, UserCircle2, ArrowUpRight } from 'lucide-react'
import { useAuth } from '../hooks/useAuth'
import { API_BASE, signin, SIGNIN_PATH } from '../api/client'
import { IDFY_HOME_URL, MMS_ADMIN_ROLES } from './Members'

export default function SignIn() {
  const navigate = useNavigate()
  const location = useLocation()
  const { user, loading: authLoading, refreshMe } = useAuth()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [totpCode, setTotpCode] = useState('')
  const [needsTwoFactor, setNeedsTwoFactor] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const params = new URLSearchParams(location.search)
  const timedOut = params.get('autoSignedOut') === '1'
  const externalRedirect = params.get('redirect') || ''
  const stateFrom = (location.state as { from?: { pathname?: string } })?.from?.pathname
  const from = stateFrom || '/'
  // ProtectedRoute sets location.state.from when bouncing an unauth'd user
  // here; direct /signin visits have no such state. We only auto-navigate
  // away when there's an actual intended destination — otherwise a user who
  // types /signin to switch accounts gets trapped on the home route (which,
  // for zero-feature users, is itself a redirect out to IDFY).
  const hasIntendedDestination = Boolean(stateFrom) || Boolean(externalRedirect)
  const localAuthEnabled = Boolean(SIGNIN_PATH)

  // SSO: if user is already authenticated and there's an external redirect, send tokens back
  useEffect(() => {
    if (authLoading || !user) return
    if (externalRedirect) {
      // Get tokens from localStorage to pass back
      try {
        const saved = JSON.parse(localStorage.getItem('platform_auth') || '{}')
        if (saved.accessToken) {
          const sep = externalRedirect.includes('?') ? '&' : '?'
          window.location.href = `${externalRedirect}${sep}token=${encodeURIComponent(saved.accessToken)}&refreshToken=${encodeURIComponent(saved.refreshToken || '')}`
          return
        }
      } catch (_) {}
    }
    if (hasIntendedDestination) {
      navigate(from === '/signin' ? '/' : from, { replace: true })
    }
  }, [user, authLoading, externalRedirect, from, hasIntendedDestination, navigate])

  useEffect(() => {
    if (!error) return
    const t = setTimeout(() => setError(null), 4000)
    return () => clearTimeout(t)
  }, [error])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!username || !password) { setError('Username and password are required.'); return }
    if (needsTwoFactor && !totpCode) { setError('Enter your 6-digit authentication code.'); return }
    setLoading(true)
    try {
      const res = await signin({ username, password, totpCode: needsTwoFactor ? totpCode : undefined })
      await refreshMe()
      // SSO redirect: if another platform UI sent us here with ?redirect=, send tokens back
      if (externalRedirect && res?.accessToken) {
        const sep = externalRedirect.includes('?') ? '&' : '?'
        window.location.href = `${externalRedirect}${sep}token=${encodeURIComponent(res.accessToken)}&refreshToken=${encodeURIComponent(res.refreshToken || '')}`
        return
      }
      navigate(from === '/signin' ? '/' : from, { replace: true })
    } catch (err) {
      const e = err as { message?: string; data?: { details?: string; message?: string; errorCode?: string } }
      // The backend signals 2FA required via details="TWO_FACTOR_REQUIRED" on a 401
      const details = e?.data?.details || ''
      if (details.includes('TWO_FACTOR_REQUIRED')) {
        setNeedsTwoFactor(true)
        setError(null)
      } else {
        setError(e?.message || 'Sign in failed')
      }
    } finally {
      setLoading(false)
    }
  }

  const oauthBase = API_BASE || import.meta.env.VITE_PROXY_TARGET || ''
  const handleOAuth = (provider: string) => {
    window.location.href = `${oauthBase}/oauth2/authorization/${provider}`
  }

  // Direct /signin visit while already authenticated — show an explicit
  // "switch account" card instead of silently bouncing to /. Without this a
  // non-admin user has no in-app way to sign out (their landing card on / is
  // itself a non-interactive bounce to IDFY).
  if (user && !authLoading && !hasIntendedDestination && !externalRedirect) {
    const displayName =
      [user.firstName, user.lastName].filter(Boolean).join(' ').trim() ||
      user.username ||
      user.email
    // Non-admins have no reason to enter the MMS dashboard — skip the Members
    // landing card and take them straight to their actual workspace.
    const isMmsAdmin = (user.roles || []).some((r) => MMS_ADMIN_ROLES.includes(r))
    return (
      <div className="flex items-center justify-center py-8">
        <div className="w-full max-w-md">
          <div className="card p-8 space-y-6">
            <div className="text-center">
              <div className="w-12 h-12 rounded-full bg-brand-100 dark:bg-brand-900/50 flex items-center justify-center mx-auto mb-3">
                <UserCircle2 size={24} className="text-brand-600 dark:text-brand-400" />
              </div>
              <h1 className="text-2xl font-bold text-[rgb(var(--text-primary))]">You're signed in</h1>
              <p className="mt-2 text-sm text-[rgb(var(--text-muted))]">
                as <span className="font-medium text-[rgb(var(--text-primary))]">{displayName}</span>
                {user.email && displayName !== user.email && (
                  <span className="text-[rgb(var(--text-muted))]"> · {user.email}</span>
                )}
              </p>
            </div>

            <div className="flex flex-col gap-2">
              {isMmsAdmin ? (
                <button type="button" onClick={() => navigate('/', { replace: true })}
                  className="btn-primary w-full justify-center">
                  <LogIn size={16} /> Continue to app
                </button>
              ) : (
                <a href={IDFY_HOME_URL} className="btn-primary w-full justify-center">
                  <ArrowUpRight size={16} /> Continue to app
                </a>
              )}
              <Link to="/signout" className="btn-secondary w-full justify-center" replace>
                <LogOut size={16} /> Sign out and use a different account
              </Link>
            </div>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="flex items-center justify-center py-8">
      <div className="w-full max-w-md">
        <div className="card p-8 space-y-6">
          <div className="text-center">
            <div className="w-12 h-12 rounded-full bg-brand-100 dark:bg-brand-900/50 flex items-center justify-center mx-auto mb-3">
              <LogIn size={24} className="text-brand-600 dark:text-brand-400" />
            </div>
            <h1 className="text-2xl font-bold text-[rgb(var(--text-primary))]">Sign in</h1>
          </div>

          {timedOut && (
            <div className="flex items-center gap-2 p-3 rounded-lg bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-400 text-sm">
              <AlertCircle size={16} />
              You were signed out due to inactivity.
            </div>
          )}

          {error && (
            <div className="flex items-center gap-2 p-3 rounded-lg bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-400 text-sm">
              <AlertCircle size={16} />
              {error}
            </div>
          )}

          {localAuthEnabled ? (
            <form onSubmit={handleSubmit} className="space-y-4" noValidate>
              <div>
                <label htmlFor="username" className="label">Username</label>
                <input
                  id="username"
                  type="text"
                  className="input"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  required
                  autoFocus
                />
              </div>
              <div>
                <div className="flex items-center justify-between">
                  <label htmlFor="password" className="label">Password</label>
                  <Link to="/forgot-password" className="text-xs text-brand-600 hover:text-brand-700 dark:text-brand-400">
                    Forgot password?
                  </Link>
                </div>
                <input
                  id="password"
                  type="password"
                  className="input"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </div>
              {needsTwoFactor && (
                <div>
                  <label htmlFor="totpCode" className="label">
                    Authentication code
                  </label>
                  <input
                    id="totpCode"
                    type="text"
                    inputMode="numeric"
                    pattern="[0-9]{6}|[A-Z0-9-]+"
                    className="input tracking-widest text-center font-mono"
                    placeholder="123456"
                    value={totpCode}
                    onChange={(e) => setTotpCode(e.target.value.trim())}
                    autoFocus
                    autoComplete="one-time-code"
                    required
                  />
                  <p className="mt-1 text-xs text-[rgb(var(--text-muted))]">
                    Enter the 6-digit code from your authenticator app, or a recovery code.
                  </p>
                </div>
              )}
              <button type="submit" className="btn-primary w-full justify-center" disabled={loading}>
                {loading ? <Loader2 size={16} className="animate-spin" /> : <LogIn size={16} />}
                {loading ? 'Signing in…' : needsTwoFactor ? 'Verify code' : 'Sign in'}
              </button>
            </form>
          ) : (
            <div className="text-sm text-center text-[rgb(var(--text-muted))]">
              Local sign-in is not available. Use a social provider below.
            </div>
          )}

          {localAuthEnabled && (
            <div className="relative flex items-center gap-3">
              <div className="flex-1 border-t border-[rgb(var(--border-subtle))]" />
              <span className="text-xs text-gray-400">or</span>
              <div className="flex-1 border-t border-[rgb(var(--border-subtle))]" />
            </div>
          )}

          <div className="space-y-2">
            <button
              type="button"
              onClick={() => handleOAuth('google')}
              className="btn-secondary w-full justify-center text-sm"
            >
              Continue with Google
            </button>
            <button
              type="button"
              onClick={() => handleOAuth('apple')}
              className="btn-secondary w-full justify-center text-sm"
            >
              Continue with Apple
            </button>
          </div>

          <p className="text-center text-xs text-[rgb(var(--text-muted))]">
            Admin access only. End users sign up in the IDFY app.
          </p>
        </div>
      </div>
    </div>
  )
}
