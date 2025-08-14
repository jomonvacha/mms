import React, { useEffect, useState, useCallback } from 'react';
import { AuthContext } from '../hooks/useAuth.js';
import { me } from '../api/client.js';

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

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

  const ctx = {
    user,
    loading,
    error,
    refreshMe: loadMe,
  };

  return <AuthContext.Provider value={ctx}>{children}</AuthContext.Provider>;
}

