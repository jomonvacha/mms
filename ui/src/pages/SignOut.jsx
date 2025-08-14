import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { logout } from '../api/client.js';
import { useAuth } from '../hooks/useAuth.js';

export default function SignOut() {
  const navigate = useNavigate();
  const { refreshMe } = useAuth();

  useEffect(() => {
    let cancelled = false;
    async function doLogout() {
      try {
        await logout();
      } catch (_) {
        // If logout endpoint is not configured, continue to refresh state
      } finally {
        if (!cancelled) {
          await refreshMe();
          navigate('/signin', { replace: true });
        }
      }
    }
    doLogout();
    return () => { cancelled = true; };
  }, [navigate, refreshMe]);

  return (
    <div className="text-center text-muted py-5">Signing you out…</div>
  );
}
