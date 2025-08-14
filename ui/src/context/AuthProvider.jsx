import React, { useEffect, useState, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { AuthContext } from '../hooks/useAuth.js';
import { me, logout, clearAuthTokens } from '../api/client.js';

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const navigate = useNavigate();
  const idleTimerRef = useRef(null);
  const warnIntervalRef = useRef(null);
  const AUTO_LOGOUT_MINUTES = Number(import.meta.env.VITE_AUTO_LOGOUT_MINUTES || 0);
  const AUTO_LOGOUT_MS = AUTO_LOGOUT_MINUTES > 0 ? AUTO_LOGOUT_MINUTES * 60 * 1000 : 0;
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
    loadMe();
  }, [loadMe]);

  // Idle auto-logout with confirmation modal
  useEffect(() => {
    if (!AUTO_LOGOUT_MS || !user) return; // only when enabled and authenticated

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
              // Time's up - perform logout
              (async () => {
                try { await logout(); } catch (_) {}
                try { clearAuthTokens(); } catch (_) {}
                setUser(null);
                setIdleWarning(false);
                navigate('/signin?autoLoggedOut=1', { replace: true });
              })();
              return 0;
            }
            return s - 1;
          });
        }, 1000);
      }, AUTO_LOGOUT_MS);
    };

    const events = ['mousemove', 'keydown', 'scroll', 'click', 'touchstart', 'visibilitychange'];
    events.forEach((evt) => window.addEventListener(evt, resetTimer, { passive: true }));
    resetTimer();

    return () => {
      if (idleTimerRef.current) clearTimeout(idleTimerRef.current);
      if (warnIntervalRef.current) clearInterval(warnIntervalRef.current);
      events.forEach((evt) => window.removeEventListener(evt, resetTimer));
    };
  }, [user, AUTO_LOGOUT_MS, navigate]);

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
              try { await logout(); } catch (_) {}
              try { clearAuthTokens(); } catch (_) {}
              setUser(null);
              setIdleWarning(false);
              navigate('/signin?autoLoggedOut=1', { replace: true });
            })();
            return 0;
          }
          return s - 1;
        });
      }, 1000);
    }, AUTO_LOGOUT_MS);
  }, [AUTO_LOGOUT_MS, IDLE_WARNING_SECONDS, navigate]);

  const confirmLogout = useCallback(async () => {
    try { await logout(); } catch (_) {}
    try { clearAuthTokens(); } catch (_) {}
    setUser(null);
    setIdleWarning(false);
    if (warnIntervalRef.current) clearInterval(warnIntervalRef.current);
    if (idleTimerRef.current) clearTimeout(idleTimerRef.current);
    navigate('/signin?autoLoggedOut=1', { replace: true });
  }, [navigate]);

  const ctx = {
    user,
    loading,
    error,
    refreshMe: loadMe,
    idleWarning,
    idleCountdown,
    extendSession,
    confirmLogout,
  };

  return <AuthContext.Provider value={ctx}>{children}</AuthContext.Provider>;
}
