import React from 'react';
import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth.js';
import { REGISTER_PATH } from '../api/client.js';

export default function Navbar() {
  const { user, loading } = useAuth();

  return (
    <nav className="navbar navbar-expand-lg navbar-dark bg-dark sticky-top">
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
          <ul className="navbar-nav me-auto mb-2 mb-lg-0">
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

          <ul className="navbar-nav ms-auto">
            {loading ? (
              <li className="nav-item">
                <span className="navbar-text text-muted">Loading…</span>
              </li>
            ) : user ? (
              <>
                <li className="nav-item">
                  <span className="navbar-text me-3">Hello, {user.name || user.email}</span>
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
          </ul>
        </div>
      </div>
    </nav>
  );
}
