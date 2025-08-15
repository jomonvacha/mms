import React, {useEffect, useState} from 'react';
import {Link, useNavigate} from 'react-router-dom';
import {register, REGISTER_PATH, signin, validateEndpoint} from '../api/client.js';
import {useAuth} from '../hooks/useAuth.js';

export default function SignUp() {
  const navigate = useNavigate();
  const {refreshMe} = useAuth();

  const [username, setUsername] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [endpointOk, setEndpointOk] = useState(true);
  // Auto-dismiss error alerts
  useEffect(() => {
    if (!error) return;
    const t = setTimeout(() => setError(null), 4000);
    return () => clearTimeout(t);
  }, [error]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    const phoneRegex = /^\+?[0-9.\-\s()]{7,20}$/;
    if (!username || !firstName || !lastName || !email || !password) {
      setError('All required fields must be filled.');
      return;
    }
    if (!emailRegex.test(email)) {
      setError('A valid email is required.');
      return;
    }
    if (phoneNumber && !phoneRegex.test(phoneNumber)) {
      setError('Invalid phone number format.');
      return;
    }
    if (password.length < 8) {
      setError('Password must be at least 8 characters.');
      return;
    }
    if (password !== confirm) {
      setError('Passwords do not match');
      return;
    }
    setLoading(true);
    try {
      await register({username, email, password, firstName, lastName, phoneNumber});
      // Auto sign-in after successful registration
      await signin({username, password});
      await refreshMe();
      navigate('/', {replace: true});
    } catch (err) {
      setError(err?.message || 'Sign up failed');
    } finally {
      setLoading(false);
    }
  };

  const localRegistrationEnabled = Boolean(REGISTER_PATH);

  // Validate register endpoint if configured
  useEffect(() => {
    let cancelled = false;
    if (!localRegistrationEnabled) return;
    (async () => {
      const ok = await validateEndpoint(REGISTER_PATH, 'POST');
      if (!cancelled) setEndpointOk(ok);
    })();
    return () => {
      cancelled = true;
    };
  }, [/* eslint-disable-line react-hooks/exhaustive-deps */]);

  return (
    <div className="row justify-content-center">
      <div className="col-12 col-sm-10 col-md-8 col-lg-6 col-xl-5">
        <div className="card shadow-sm">
          <div className="card-body p-4">
            <h1 className="h4 mb-3">Create your account</h1>
            {error && (
              <div className="alert alert-danger d-flex justify-content-between align-items-center" role="alert" aria-live="polite">
                <div>{error}</div>
                <button type="button" className="btn-close" aria-label="Close" onClick={() => setError(null)}></button>
              </div>
            )}
            {localRegistrationEnabled && endpointOk ? (
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
                  <div className="row g-3">
                    <div className="col-sm-6">
                      <label htmlFor="firstName" className="form-label">First name</label>
                      <input
                        id="firstName"
                        type="text"
                        className="form-control"
                        value={firstName}
                        onChange={(e) => setFirstName(e.target.value)}
                        required
                      />
                    </div>
                    <div className="col-sm-6">
                      <label htmlFor="lastName" className="form-label">Last name</label>
                      <input
                        id="lastName"
                        type="text"
                        className="form-control"
                        value={lastName}
                        onChange={(e) => setLastName(e.target.value)}
                        required
                      />
                    </div>
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
                    <label htmlFor="phoneNumber" className="form-label">Phone (optional)</label>
                    <input
                      id="phoneNumber"
                      type="tel"
                      className="form-control"
                      value={phoneNumber}
                      onChange={(e) => setPhoneNumber(e.target.value)}
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
                {localRegistrationEnabled && !endpointOk
                  ? 'Local sign-up endpoint is not available.'
                  : 'Local sign-up is not available.'}
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
