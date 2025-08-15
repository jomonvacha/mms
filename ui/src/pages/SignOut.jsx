import React, {useEffect} from 'react';
import {useNavigate} from 'react-router-dom';
import {clearAuthTokens, signout} from '../api/client.js';
import {useAuth} from '../hooks/useAuth.js';

export default function SignOut() {
  const navigate = useNavigate();
  const {refreshMe} = useAuth();

  useEffect(() => {
    let cancelled = false;

    async function doSignout() {
      try {
        await signout();
      } catch (_) {
        // If signout endpoint is not configured, continue to refresh state
      } finally {
        if (!cancelled) {
          // Ensure tokens are cleared
          try {
            clearAuthTokens();
          } catch (_) {
          }
          await refreshMe();
          navigate('/signin', {replace: true});
        }
      }
    }

    doSignout();
    return () => {
      cancelled = true;
    };
  }, [navigate, refreshMe]);

  return (
    <div className="text-center text-muted py-5">Signing you out…</div>
  );
}
