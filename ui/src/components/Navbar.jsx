import React from 'react';
import {Link, NavLink} from 'react-router-dom';
import {useAuth} from '../hooks/useAuth.js';
import {REGISTER_PATH, getMyAvatarBlob} from '../api/client.js';
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
  const [navbarDisplay, setNavbarDisplay] = React.useState('avatar');
  React.useEffect(() => {
    let cancelled = false;
    async function loadPrefs() {
      try {
        const res = await import('../api/client.js');
        const prefs = await res.getPreferences();
        if (!cancelled && prefs && (prefs.navbarDisplay === 'name' || prefs.navbarDisplay === 'avatar')) {
          setNavbarDisplay(prefs.navbarDisplay);
        }
      } catch (_) {}
    }
    if (user) loadPrefs();
    return () => { cancelled = true; };
  }, [user]);

  const navClass = 'navbar navbar-expand-lg sticky-top bg-body-tertiary border-bottom';

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
              <NavLink className={({isActive}) => 'nav-link' + (isActive ? ' active' : '')} to="/">
                Home
              </NavLink>
            </li>
            {user && (
              <li className="nav-item">
                <NavLink className={({isActive}) => 'nav-link' + (isActive ? ' active' : '')} to="/members">
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
                      <button className="dropdown-item" onClick={() => {
                        setInitialTab('profile');
                        setModalOpen(true);
                      }}>Profile
                      </button>
                    </li>
                    {canChangePassword && (
                      <li>
                        <button className="dropdown-item" onClick={() => {
                          setInitialTab('account');
                          setModalOpen(true);
                        }}>Account
                        </button>
                      </li>
                    )}
                    <li>
                      <button className="dropdown-item" onClick={() => {
                        setInitialTab('preferences');
                        setModalOpen(true);
                      }}>Preferences
                      </button>
                    </li>
                    <li>
                      <hr className="dropdown-divider"/>
                    </li>
                    <li>
                      <NavLink className={({isActive}) => 'dropdown-item' + (isActive ? ' active' : '')} to="/signout">
                        Sign out
                      </NavLink>
                    </li>
                  </ul>
                </li>
              </>
            ) : (
              <>
                <li className="nav-item">
                  <NavLink className={({isActive}) => 'nav-link' + (isActive ? ' active' : '')} to="/signin">
                    Sign In
                  </NavLink>
                </li>
                {Boolean(REGISTER_PATH) && (
                  <li className="nav-item">
                    <NavLink className={({isActive}) => 'nav-link' + (isActive ? ' active' : '')} to="/signup">
                      Sign Up
                    </NavLink>
                  </li>
                )}
              </>
            )}

          </ul>
        </div>
      </div>
      <AccountModal isOpen={modalOpen} initialTab={initialTab} onClose={() => setModalOpen(false)}/>
    </nav>
  );
}
