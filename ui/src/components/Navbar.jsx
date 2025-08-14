import React from 'react';
import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth.js';
import { useTheme } from '../hooks/useTheme.js';
import { REGISTER_PATH } from '../api/client.js';

export default function Navbar() {
  const { user, loading } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const rawName = user?.firstName || user?.username || (user?.email ? user.email.split('@')[0] : '');
  const firstName = (rawName || '').split(/[\s._-]/)[0] || 'there';
  const hour = new Date().getHours();
  const greeting = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';

  const navClass = theme === 'dark' ? 'navbar navbar-expand-lg navbar-dark bg-dark sticky-top' : 'navbar navbar-expand-lg navbar-light bg-light border-bottom sticky-top';

  return (
    <nav className={navClass}>
      <div className="container">
        <Link className="navbar-brand" to="/">MMS</Link>
        <button
          className="navbar-toggler"
          type="button"
          data-bs-toggle="collapse"
          data-bs-target="#navbarSupportedContent"
          aria-controls="navbarSupportedContent"
          aria-expanded="false"
          aria-label="Toggle navigation"
        >
          <span className="navbar-toggler-icon"></span>
        </button>
        <div className="collapse navbar-collapse" id="navbarSupportedContent">
          <ul className="navbar-nav me-auto mb-2 mb-lg-0 align-items-center">
            <li className="nav-item">
              <NavLink className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')} to="/">
                Home
              </NavLink>
            </li>
            {user && (
              <li className="nav-item">
                <NavLink className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')} to="/members">
                  Members
                </NavLink>
              </li>
            )}
          </ul>

          <ul className="navbar-nav ms-auto align-items-center gap-2">
            {loading ? (
              <li className="nav-item">
                <span className="navbar-text text-muted">Loading…</span>
              </li>
            ) : user ? (
              <>
                <li className="nav-item">
                  <NavLink className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')} to="/profile">
                    Profile
                  </NavLink>
                </li>
                <li className="nav-item d-flex align-items-center">
                  <span className="navbar-text user-greeting">{greeting}, {firstName}</span>
                </li>
                <li className="nav-item">
                  <NavLink className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')} to="/signout">
                    Sign Out
                  </NavLink>
                </li>
              </>
            ) : (
              <>
                <li className="nav-item">
                  <NavLink className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')} to="/signin">
                    Sign In
                  </NavLink>
                </li>
                {Boolean(REGISTER_PATH) && (
                  <li className="nav-item">
                    <NavLink className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')} to="/signup">
                      Sign Up
                    </NavLink>
                  </li>
                )}
              </>
            )}
            <li className="nav-item ms-2">
              <button type="button" className="btn btn-sm btn-outline-secondary" onClick={toggleTheme} aria-label="Toggle theme">
                {theme === 'dark' ? 'Light' : 'Dark'}
              </button>
            </li>
          </ul>
        </div>
      </div>
    </nav>
  );
}
