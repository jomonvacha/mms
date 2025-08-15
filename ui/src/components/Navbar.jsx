import React from 'react';
import {Link, NavLink} from 'react-router-dom';
import {useAuth} from '../hooks/useAuth.js';
import {getMyAvatarBlob} from '../api/client.js';
import AccountModal from './AccountModal.jsx';

export default function Navbar() {
  const {user, loading} = useAuth();
  const rawName = user?.firstName || user?.username || (user?.email ? user.email.split('@')[0] : '');
  const firstName = (rawName || '').split(/[\s._-]/)[0] || 'there';
  const hour = new Date().getHours();
  const greeting = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
  const [modalOpen, setModalOpen] = React.useState(false);
  const [initialTab, setInitialTab] = React.useState('profile');
  const canChangePassword = Boolean(user?.hasPassword === true);
  const [avatarUrl, setAvatarUrl] = React.useState('');
  const initials = React.useMemo(() => {
    const fn = (user?.firstName || '').trim();
    const ln = (user?.lastName || '').trim();
    const a = fn ? fn[0] : '';
    const b = ln ? ln[0] : '';
    if (a || b) return (a + b).toUpperCase();
    const un = (user?.username || user?.email || '').trim();
    return un ? un[0].toUpperCase() : '?';
  }, [user]);

  // Load avatar blob for navbar display
  React.useEffect(() => {
    let revoke;
    async function load() {
      if (!user) { setAvatarUrl(''); return; }
      try {
        const blob = await getMyAvatarBlob();
        const url = URL.createObjectURL(blob);
        revoke = url;
        setAvatarUrl(url);
      } catch (_) {
        setAvatarUrl('');
      }
    }
    load();
    return () => { if (revoke && revoke.startsWith('blob:')) URL.revokeObjectURL(revoke); };
  }, [user]);

  // Load navbar display preference
  const [navbarDisplay, setNavbarDisplay] = React.useState(() => {
    try { return localStorage.getItem('mms_navbarDisplay') || 'avatar'; } catch (_) { return 'avatar'; }
  });
  React.useEffect(() => {
    let cancelled = false;
    async function loadPrefs() {
      try {
        const res = await import('../api/client.js');
        const prefs = await res.getPreferences();
        if (!cancelled && prefs && (prefs.navbarDisplay === 'name' || prefs.navbarDisplay === 'avatar')) {
          setNavbarDisplay(prefs.navbarDisplay);
          try { localStorage.setItem('mms_navbarDisplay', prefs.navbarDisplay); } catch (_) {}
        }
      } catch (_) {}
    }
    if (user) loadPrefs();
    return () => { cancelled = true; };
  }, [user]);

  // Listen for preference updates to apply instantly
  React.useEffect(() => {
    const handler = (e) => {
      const prefs = e.detail || {};
      if (prefs.navbarDisplay === 'name' || prefs.navbarDisplay === 'avatar') {
        setNavbarDisplay(prefs.navbarDisplay);
      }
    };
    window.addEventListener('mms:prefsUpdated', handler);
    return () => window.removeEventListener('mms:prefsUpdated', handler);
  }, []);

  const navClass = 'navbar navbar-expand-lg sticky-top bg-body-tertiary border-bottom';

  return (
    <nav className={navClass}>
      <div className="container">
        <Link className="navbar-brand d-flex align-items-center" to="/" aria-label="Member Management System">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            {/* Group icon to represent members */}
            <path d="M12 12a4 4 0 1 0-4-4 4 4 0 0 0 4 4z"/>
            <path d="M4 20a8 8 0 0 1 16 0v1H4z"/>
            <path d="M6 10a3 3 0 1 0-3-3 3 3 0 0 0 3 3z"/>
            <path d="M21 17.5a5.5 5.5 0 0 0-6.5-4.4 8.5 8.5 0 0 1 6.5 8.4V21z"/>
          </svg>
        </Link>
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
              <NavLink className={({isActive}) => 'nav-link d-inline-flex align-items-center gap-1' + (isActive ? ' active' : '')} to="/">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                  <path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"/>
                </svg>
                <span>Home</span>
              </NavLink>
            </li>
            {user && (
              <li className="nav-item">
                <NavLink className={({isActive}) => 'nav-link d-inline-flex align-items-center gap-1' + (isActive ? ' active' : '')} to="/members">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                    <path d="M12 12a4 4 0 1 0-4-4 4 4 0 0 0 4 4zm-7 8a7 7 0 0 1 14 0v1H5z"/>
                  </svg>
                  <span>Members</span>
                </NavLink>
              </li>
            )}
          </ul>

          <ul className="navbar-nav ms-auto align-items-center gap-1">
            {loading ? (
              <li className="nav-item">
                <span className="navbar-text text-body-secondary">Loading…</span>
              </li>
            ) : user ? (
              <>
                <li className="nav-item dropdown d-flex align-items-center">
                  {navbarDisplay === 'name' ? (
                    <a
                      className="nav-link dropdown-toggle"
                      href="#"
                      id="userMenuDropdown"
                      role="button"
                      data-bs-toggle="dropdown"
                      aria-expanded="false"
                    >
                      {firstName}
                    </a>
                  ) : (
                    <a
                      href="#"
                      className="nav-link p-0"
                      role="button"
                      data-bs-toggle="dropdown"
                      aria-expanded="false"
                      id="userMenuDropdown"
                      aria-label="Open user menu"
                    >
                      {avatarUrl ? (
                        <img src={avatarUrl} alt="avatar" style={{ width: 32, height: 32, borderRadius: '50%', objectFit: 'cover' }} />
                      ) : (
                        <span className="d-inline-flex align-items-center justify-content-center rounded-circle bg-secondary text-white" style={{ width: 32, height: 32, fontSize: 12, fontWeight: 600 }}>
                          {initials}
                        </span>
                      )}
                    </a>
                  )}
                  <ul className="dropdown-menu dropdown-menu-end shadow" aria-labelledby="userMenuDropdown"
                      style={{minWidth: '16rem'}}>
                    <li>
                      <span className="dropdown-item-text">
                        <strong>{user.firstName || user.username || user.email}</strong>
                        <br/>
                        <small className="text-muted">{user.email}</small>
                      </span>
                    </li>
                    <li>
                      <hr className="dropdown-divider"/>
                    </li>
                    <li>
                      <button className="dropdown-item d-flex align-items-center gap-1" onClick={() => {
                        setInitialTab('profile');
                        setModalOpen(true);
                      }}>
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 12a5 5 0 1 0-5-5 5 5 0 0 0 5 5zm0 2c-4.33 0-8 2.17-8 5v1h16v-1c0-2.83-3.67-5-8-5z"/></svg>
                        <span>Profile</span>
                      </button>
                    </li>
                    {canChangePassword && (
                      <li>
                        <button className="dropdown-item d-flex align-items-center gap-1" onClick={() => {
                          setInitialTab('account');
                          setModalOpen(true);
                        }}>
                          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 17a2 2 0 0 0 2-2V9a2 2 0 1 0-4 0v6a2 2 0 0 0 2 2zm7-7h-1.18A6 6 0 0 0 6.18 10H5a3 3 0 0 0-3 3v4a3 3 0 0 0 3 3h14a3 3 0 0 0 3-3v-4a3 3 0 0 0-3-3z"/></svg>
                          <span>Account</span>
                        </button>
                      </li>
                    )}
                    <li>
                      <button className="dropdown-item d-flex align-items-center gap-1" onClick={() => {
                        setInitialTab('preferences');
                        setModalOpen(true);
                      }}>
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M4 10h4v2H4zm0 5h7v2H4zM4 5h10v2H4zm14.5 6a2.5 2.5 0 1 0-2.45-3h-5.1a2.5 2.5 0 1 0 0 2h5.1a2.49 2.49 0 0 0 2.45 1z"/></svg>
                        <span>Preferences</span>
                      </button>
                    </li>
                    <li>
                      <hr className="dropdown-divider"/>
                    </li>
                    <li>
                      <NavLink className={({isActive}) => 'dropdown-item d-flex align-items-center gap-1' + (isActive ? ' active' : '')} to="/signout">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                          <path d="M14 7v-2h6v14h-6v-2h4V7z"/>
                          <path d="M3 12l5-5v3h6v4H8v3z"/>
                        </svg>
                        <span>Sign out</span>
                      </NavLink>
                    </li>
                  </ul>
                </li>
              </>
            ) : (
              <>
                <li className="nav-item">
                  <NavLink className={({isActive}) => 'nav-link d-inline-flex align-items-center gap-1' + (isActive ? ' active' : '')} to="/signin">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                      <path d="M10 17l5-5-5-5v3H3v4h7z"/>
                      <path d="M19 3h-6v2h6v14h-6v2h6a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2z"/>
                    </svg>
                    <span>Sign In</span>
                  </NavLink>
                </li>
                {/* Sign Up removed from navbar as requested */}
              </>
            )}

          </ul>
        </div>
      </div>
      <AccountModal isOpen={modalOpen} initialTab={initialTab} onClose={() => setModalOpen(false)}/>
    </nav>
  );
}
