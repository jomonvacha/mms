import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { register, REGISTER_PATH } from '../api/client.js';
import { useAuth } from '../hooks/useAuth.js';

export default function SignUp() {
  const navigate = useNavigate();
  const { refreshMe } = useAuth();

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    if (password !== confirm) {
      setError('Passwords do not match');
      return;
    }
    setLoading(true);
    try {
      await register({ name, email, password });
      await refreshMe();
      navigate('/', { replace: true });
    } catch (err) {
      setError(err?.message || 'Sign up failed');
    } finally {
      setLoading(false);
    }
  };

  const localRegistrationEnabled = Boolean(REGISTER_PATH);

  return (
    <div className="row justify-content-center">
      <div className="col-12 col-sm-10 col-md-8 col-lg-6 col-xl-5">
        <div className="card shadow-sm">
          <div className="card-body p-4">
            <h1 className="h4 mb-3">Create your account</h1>
            {error && (
              <div className="alert alert-danger" role="alert">
                {error}
              </div>
            )}
            {localRegistrationEnabled ? (
              <>
                <form onSubmit={handleSubmit} noValidate>
                  <div className="mb-3">
                    <label htmlFor="name" className="form-label">Name</label>
                    <input
                      id="name"
                      type="text"
                      className="form-control"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      required
                    />
                  </div>
                  <div className="mb-3">
                    <label htmlFor="email" className="form-label">Email</label>
                    <input
                      id="email"
                      type="email"
                      className="form-control"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
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
                  <div className="mb-3">
                    <label htmlFor="confirm" className="form-label">Confirm Password</label>
                    <input
                      id="confirm"
                      type="password"
                      className="form-control"
                      value={confirm}
                      onChange={(e) => setConfirm(e.target.value)}
                      required
                    />
                  </div>
                  <button type="submit" className="btn btn-primary w-100" disabled={loading}>
                    {loading ? 'Creating account…' : 'Sign Up'}
                  </button>
                </form>
                <div className="text-center mt-3">
                  <span className="text-muted">Already have an account?</span>{' '}
                  <Link to="/signin">Sign in</Link>
                </div>
              </>
            ) : (
              <div className="alert alert-info">
                Local sign-up is not available. Please contact an administrator or sign in with a social provider.
                <div className="mt-2">
                  <Link to="/signin" className="btn btn-outline-primary btn-sm">Go to Sign In</Link>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
