import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AuthContext, type MmsUser } from '../hooks/useAuth'
import { clearAuthTokens, me, setAuthTokens, getPreferences } from '../api/client'
import { useTheme } from '../hooks/useTheme'
import type { Theme } from '../hooks/useTheme'

const AUTH_KEY = 'platform_auth'
const AUTO_SIGNOUT_MINUTES = Number(import.meta.env.VITE_AUTO_SIGNOUT_MINUTES || 0)
const AUTO_SIGNOUT_MS = AUTO_SIGNOUT_MINUTES > 0 ? AUTO_SIGNOUT_MINUTES * 60 * 1000 : 0
const IDLE_WARNING_SECONDS = Number(import.meta.env.VITE_IDLE_WARNING_SECONDS || 60)

function getStoredToken(): string | null {
  try { return JSON.parse(localStorage.getItem(AUTH_KEY) || 'null')?.accessToken } catch { return null }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<MmsUser | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const navigate = useNavigate()
  const { setTheme } = useTheme()
  const idleTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const warnIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const [justAuthed, setJustAuthed] = useState(false)
  const [idleWarning, setIdleWarning] = useState(false)
  const [idleCountdown, setIdleCountdown] = useState(IDLE_WARNING_SECONDS)

  const loadMe = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await me()
      setUser((data as MmsUser) || null)
    } catch (err) {
      setUser(null)
      const e = err as { status?: number; message?: string }
      if (e?.status && e.status !== 401) setError(e.message || 'Failed to load session')
    } finally {
      setLoading(false)
    }
  }, [])

  // Pick up tokens or signedOut flag from URL
  useEffect(() => {
    try {
      const params = new URLSearchParams(window.location.search)
      const token = params.get('token')
      const refresh = params.get('refreshToken')
      const signedOut = params.get('signedOut')
      const url = new URL(window.location.href)
      if (token || refresh) {
        setAuthTokens({ accessToken: token ?? undefined, refreshToken: refresh ?? undefined, tokenType: 'Bearer' })
        url.searchParams.delete('token')
        url.searchParams.delete('refreshToken')
        setJustAuthed(true)
      }
      if (signedOut) {
        url.searchParams.delete('signedOut')
      }
      if (token || refresh || signedOut) {
        window.history.replaceState({}, '', url.toString())
      }
    } catch (_) {}
    loadMe()
  }, [loadMe])

  useEffect(() => {
    if (justAuthed && user && !loading) {
      setJustAuthed(false)
      navigate('/', { replace: true })
    }
  }, [justAuthed, user, loading, navigate])

  useEffect(() => {
    let cancelled = false
    async function loadPrefs() {
      if (!user) return
      try {
        const prefs = await getPreferences()
        if (cancelled || !prefs) return
        let effective: Theme = 'light'
        if (prefs.theme === 'dark') effective = 'dark'
        else if (prefs.theme === 'system') {
          effective = window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
        }
        setTheme(effective)
        try { localStorage.setItem('mms_lang', prefs.language || 'en') } catch (_) {}
      } catch (_) {}
    }
    loadPrefs()
    return () => { cancelled = true }
  }, [user, setTheme])

  // Sign-out: redirect to /signout page for platform-wide cleanup
  const doSignoutAndRedirect = useCallback(async () => {
    setIdleWarning(false)
    if (warnIntervalRef.current) clearInterval(warnIntervalRef.current)
    if (forceSignoutRef.current) clearTimeout(forceSignoutRef.current)
    window.location.href = '/signout'
  }, [])

  // Sync auth state when tab becomes visible (detects sign-out from other services)
  useEffect(() => {
    const onVisibilityChange = () => {
      if (document.hidden) return
      const lsToken = getStoredToken()

      if (user && !lsToken) {
        // Token was cleared by another service's sign-out iframe
        clearAuthTokens()
        setUser(null)
        window.location.href = '/signin'
      } else if (user && lsToken) {
        // Revalidate token
        loadMe()
      }
    }
    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => document.removeEventListener('visibilitychange', onVisibilityChange)
  }, [user, loadMe])

  const idleWarningRef = useRef(false)
  useEffect(() => { idleWarningRef.current = idleWarning }, [idleWarning])

  const deadlineRef = useRef<number>(0)
  const forceSignoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    if (!AUTO_SIGNOUT_MS || !user) return

    const startWarnCountdown = () => {
      const deadline = Date.now() + IDLE_WARNING_SECONDS * 1000
      deadlineRef.current = deadline
      setIdleCountdown(IDLE_WARNING_SECONDS)
      setIdleWarning(true)

      // Hard deadline — guaranteed signout even if tab is in background
      if (forceSignoutRef.current) clearTimeout(forceSignoutRef.current)
      forceSignoutRef.current = setTimeout(() => {
        doSignoutAndRedirect()
      }, IDLE_WARNING_SECONDS * 1000)

      // UI countdown — uses Date.now() so it catches up after background throttling
      if (warnIntervalRef.current) clearInterval(warnIntervalRef.current)
      warnIntervalRef.current = setInterval(() => {
        const remaining = Math.max(0, Math.ceil((deadlineRef.current - Date.now()) / 1000))
        setIdleCountdown(remaining)
        if (remaining <= 0) {
          clearInterval(warnIntervalRef.current!)
        }
      }, 500)
    }

    const resetTimer = () => {
      if (idleWarningRef.current) return
      if (idleTimerRef.current) clearTimeout(idleTimerRef.current)
      idleTimerRef.current = setTimeout(startWarnCountdown, AUTO_SIGNOUT_MS)
    }

    const events = ['mousemove', 'keydown', 'scroll', 'click', 'touchstart']
    events.forEach((evt) => window.addEventListener(evt, resetTimer, { passive: true }))
    resetTimer()

    return () => {
      if (idleTimerRef.current) clearTimeout(idleTimerRef.current)
      if (warnIntervalRef.current) clearInterval(warnIntervalRef.current)
      if (forceSignoutRef.current) clearTimeout(forceSignoutRef.current)
      events.forEach((evt) => window.removeEventListener(evt, resetTimer))
    }
  }, [user, doSignoutAndRedirect])

  const extendSession = useCallback(() => {
    setIdleWarning(false)
    setIdleCountdown(IDLE_WARNING_SECONDS)
    if (warnIntervalRef.current) clearInterval(warnIntervalRef.current)
    if (idleTimerRef.current) clearTimeout(idleTimerRef.current)
    if (forceSignoutRef.current) clearTimeout(forceSignoutRef.current)
    if (AUTO_SIGNOUT_MS) {
      idleTimerRef.current = setTimeout(() => {
        setIdleCountdown(IDLE_WARNING_SECONDS)
        setIdleWarning(true)
      }, AUTO_SIGNOUT_MS)
    }
  }, [])

  const confirmSignout = useCallback(async () => {
    if (warnIntervalRef.current) clearInterval(warnIntervalRef.current)
    if (idleTimerRef.current) clearTimeout(idleTimerRef.current)
    await doSignoutAndRedirect()
  }, [doSignoutAndRedirect])

  return (
    <AuthContext.Provider value={{ user, loading, error, refreshMe: loadMe, idleWarning, idleCountdown, extendSession, confirmSignout }}>
      {children}
    </AuthContext.Provider>
  )
}
