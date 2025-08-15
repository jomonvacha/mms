import React, {useEffect, useState} from 'react';
import {Link, useLocation, useNavigate} from 'react-router-dom';
import {useAuth} from '../hooks/useAuth.js';
import {API_BASE, REGISTER_PATH, signin, SIGNIN_PATH, validateEndpoint} from '../api/client.js';

export default function SignIn() {
  const navigate = useNavigate();
  const location = useLocation();
  const {refreshMe} = useAuth();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [endpointOk, setEndpointOk] = useState(true);
  const timedOut = new URLSearchParams(location.search).get('autoSignedOut') === '1';

  const from = location.state?.from?.pathname || '/';

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await signin({username, password});
      await refreshMe();
      navigate(from === '/signin' ? '/' : from, {replace: true});
    } catch (err) {
      setError(err?.message || 'Sign in failed');
    } finally {
      setLoading(false);
    }
  };

  const oauthBase = API_BASE || import.meta.env.VITE_PROXY_TARGET || '';
  const handleOAuth = (provider) => {
    window.location.href = `${oauthBase}/oauth2/authorization/${provider}`;
  };

  const localAuthEnabled = Boolean(SIGNIN_PATH);

  // Validate signin endpoint if configured
  useEffect(() => {
    let cancelled = false;
    if (!localAuthEnabled) return;
    (async () => {
      const ok = await validateEndpoint(SIGNIN_PATH, 'POST');
      if (!cancelled) setEndpointOk(ok);
    })();
    return () => {
      cancelled = true;
    };
  }, [localAuthEnabled]);

  return (
    <div className="row justify-content-center">
      <div className="col-12 col-sm-10 col-md-8 col-lg-6 col-xl-5">
        <div className="card shadow-sm">
          <div className="card-body p-4">
            <h1 className="h4 mb-3">Sign In</h1>
            {timedOut && (
              <div className="alert alert-warning" role="alert">
                You were signed out due to inactivity.
              </div>
            )}
            {error && (
              <div className="alert alert-danger" role="alert">
                {error}
              </div>
            )}
            {localAuthEnabled && endpointOk ? (
              <>
                <form onSubmit={handleSubmit} noValidate>
                  <div className="mb-3">
                    <label htmlFor="username" className="form-label">Username</label>
                    <input
                      id="username"
                      type="text"
                      className="form-control"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      required
                    />
                  </div>
                  <div className="mb-3">
                    <label htmlFor="password" className="form-label">Password</label>
                    <input
                      id="password"
                      type="password"
                      className="form-control"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      required
                    />
                  </div>
                  <button type="submit" className="btn btn-primary w-100" disabled={loading}>
                    {loading ? 'Signing in…' : 'Sign In'}
                  </button>
                </form>
                <div className="text-center text-muted my-3">or</div>
              </>
            ) : (
              <div className="alert alert-info">
                {localAuthEnabled && !endpointOk
                  ? 'Local sign-in endpoint is not available. Use a social provider below.'
                  : 'Local sign-in is not available. Use a social provider below.'}
              </div>
            )}

            <div className="d-grid gap-2">
              <button
                type="button"
                className="btn btn-outline-danger"
                onClick={() => handleOAuth('google')}
              >
                Continue with Google
              </button>
              <button
                type="button"
                className="btn btn-outline-dark"
                onClick={() => handleOAuth('apple')}
              >
                Continue with Apple
              </button>
            </div>

            {Boolean(REGISTER_PATH) && (
              <div className="text-center mt-3">
                <span className="text-muted">No account?</span>{' '}
                <Link to="/signup">Sign up</Link>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
