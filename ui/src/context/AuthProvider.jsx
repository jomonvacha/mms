import React, {useCallback, useEffect, useRef, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {AuthContext} from '../hooks/useAuth.js';
import {clearAuthTokens, me, setAuthTokens, signout, getPreferences} from '../api/client.js';
import { useTheme } from '../hooks/useTheme.js';

export function AuthProvider({children}) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const navigate = useNavigate();
  const { setTheme } = useTheme();
  const idleTimerRef = useRef(null);
  const warnIntervalRef = useRef(null);
  const [justAuthed, setJustAuthed] = useState(false);
  const AUTO_SIGNOUT_MINUTES = Number(import.meta.env.VITE_AUTO_SIGNOUT_MINUTES || 0);
  const AUTO_SIGNOUT_MS = AUTO_SIGNOUT_MINUTES > 0 ? AUTO_SIGNOUT_MINUTES * 60 * 1000 : 0;
  const IDLE_WARNING_SECONDS = Number(import.meta.env.VITE_IDLE_WARNING_SECONDS || 60);
  const [idleWarning, setIdleWarning] = useState(false);
  const [idleCountdown, setIdleCountdown] = useState(IDLE_WARNING_SECONDS);

  const loadMe = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await me();
      setUser(data || null);
    } catch (err) {
      setUser(null);
      if (err?.status && err.status !== 401) {
        setError(err.message || 'Failed to load session');
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // If OAuth2 success handler redirected with tokens (?token=&refreshToken=), capture them once
    try {
      const params = new URLSearchParams(window.location.search);
      const token = params.get('token');
      const refresh = params.get('refreshToken');
      if (token || refresh) {
        setAuthTokens({accessToken: token, refreshToken: refresh, tokenType: 'Bearer'});
        const url = new URL(window.location.href);
        url.searchParams.delete('token');
        url.searchParams.delete('refreshToken');
        window.history.replaceState({}, '', url.toString());
        setJustAuthed(true);
      }
    } catch (_) {
    }

    loadMe();
  }, [loadMe]);

  // After successful OAuth token capture and user load, redirect to /members
  useEffect(() => {
    if (justAuthed && user && !loading) {
      setJustAuthed(false);
      navigate('/members', {replace: true});
    }
  }, [justAuthed, user, loading, navigate]);

  // Apply user preferences (theme) on login
  useEffect(() => {
    let cancelled = false;
    async function loadPrefs() {
      if (!user) return;
      try {
        const prefs = await getPreferences();
        if (cancelled || !prefs) return;
        // Compute effective theme for 'system'
        let effective = 'light';
        if (prefs.theme === 'dark') effective = 'dark';
        else if (prefs.theme === 'system') {
          const isDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
          effective = isDark ? 'dark' : 'light';
        }
        setTheme(effective);
        try { localStorage.setItem('mms_lang', prefs.language || 'en'); } catch (_) {}
      } catch (_) {
        // ignore
      }
    }
    loadPrefs();
    return () => { cancelled = true; };
  }, [user, setTheme]);

  // Idle auto-signout with confirmation modal
  useEffect(() => {
    if (!AUTO_SIGNOUT_MS || !user) return; // only when enabled and authenticated

    const resetTimer = () => {
      if (idleTimerRef.current) clearTimeout(idleTimerRef.current);
      // Schedule showing the warning modal after idle period
      idleTimerRef.current = setTimeout(() => {
        setIdleCountdown(IDLE_WARNING_SECONDS);
        setIdleWarning(true);
        if (warnIntervalRef.current) clearInterval(warnIntervalRef.current);
        warnIntervalRef.current = setInterval(() => {
          setIdleCountdown((s) => {
            if (s <= 1) {
              clearInterval(warnIntervalRef.current);
              // Time's up - perform signout
              (async () => {
                try {
                  await signout();
                } catch (_) {
                }
                try {
                  clearAuthTokens();
                } catch (_) {
                }
                setUser(null);
                setIdleWarning(false);
                navigate('/signin?autoSignedOut=1', {replace: true});
              })();
              return 0;
            }
            return s - 1;
          });
        }, 1000);
      }, AUTO_SIGNOUT_MS);
    };

    const events = ['mousemove', 'keydown', 'scroll', 'click', 'touchstart', 'visibilitychange'];
    events.forEach((evt) => window.addEventListener(evt, resetTimer, {passive: true}));
    resetTimer();

    return () => {
      if (idleTimerRef.current) clearTimeout(idleTimerRef.current);
      if (warnIntervalRef.current) clearInterval(warnIntervalRef.current);
      events.forEach((evt) => window.removeEventListener(evt, resetTimer));
    };
  }, [user, AUTO_SIGNOUT_MS, navigate]);

  const extendSession = useCallback(() => {
    // Hide warning and reset timers
    setIdleWarning(false);
    setIdleCountdown(IDLE_WARNING_SECONDS);
    if (warnIntervalRef.current) clearInterval(warnIntervalRef.current);
    if (idleTimerRef.current) clearTimeout(idleTimerRef.current);
    // Re-arm idle timer by pretending an activity event occurred
    // The effect's resetTimer is bound to events, so simulate by starting a new timeout directly
    idleTimerRef.current = setTimeout(() => {
      setIdleCountdown(IDLE_WARNING_SECONDS);
      setIdleWarning(true);
      if (warnIntervalRef.current) clearInterval(warnIntervalRef.current);
      warnIntervalRef.current = setInterval(() => {
        setIdleCountdown((s) => {
          if (s <= 1) {
            clearInterval(warnIntervalRef.current);
            (async () => {
              try {
                await signout();
              } catch (_) {
              }
              try {
                clearAuthTokens();
              } catch (_) {
              }
              setUser(null);
              setIdleWarning(false);
              navigate('/signin?autoSignedOut=1', {replace: true});
            })();
            return 0;
          }
          return s - 1;
        });
      }, 1000);
    }, AUTO_SIGNOUT_MS);
  }, [AUTO_SIGNOUT_MS, IDLE_WARNING_SECONDS, navigate]);

  const confirmSignout = useCallback(async () => {
    try {
      await signout();
    } catch (_) {
    }
    try {
      clearAuthTokens();
    } catch (_) {
    }
    setUser(null);
    setIdleWarning(false);
    if (warnIntervalRef.current) clearInterval(warnIntervalRef.current);
    if (idleTimerRef.current) clearTimeout(idleTimerRef.current);
    navigate('/signin?autoSignedOut=1', {replace: true});
  }, [navigate]);

  const ctx = {
    user,
    loading,
    error,
    refreshMe: loadMe,
    idleWarning,
    idleCountdown,
    extendSession,
    confirmSignout,
  };

  return <AuthContext.Provider value={ctx}>{children}</AuthContext.Provider>;
}
