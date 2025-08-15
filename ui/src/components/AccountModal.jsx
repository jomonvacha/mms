import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {useAuth} from '../hooks/useAuth.js';
import {changePassword, SIGNIN_PATH, updateMe, updatePreferences as apiUpdatePreferences, uploadAvatar, getMyAvatarBlob as fetchAvatarBlob, getPreferences} from '../api/client.js';
import { useTheme } from '../hooks/useTheme.js';

export default function AccountModal({isOpen, initialTab = 'profile', onClose}) {
  const {user, refreshMe} = useAuth();
  const { setTheme } = useTheme();
  const [activeTab, setActiveTab] = useState(initialTab);
  const [submitting, setSubmitting] = useState(false);
  const [alert, setAlert] = useState(null);
  // Auto-dismiss inline alerts
  useEffect(() => {
    if (!alert) return;
    const t = setTimeout(() => setAlert(null), 4000);
    return () => clearTimeout(t);
  }, [alert]);
  const panelRef = useRef(null);
  const lastFocused = useRef(null);

  useEffect(() => {
    if (isOpen) setActiveTab(initialTab);
  }, [isOpen, initialTab]);

  useEffect(() => {
    if (!isOpen) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = prev;
    };
  }, [isOpen]);

  useEffect(() => {
    if (isOpen) {
      lastFocused.current = document.activeElement;
      const tabbables = getTabbables(panelRef.current);
      (tabbables[0] || panelRef.current)?.focus();
    } else if (lastFocused.current) {
      try {
        lastFocused.current.focus();
      } catch (_) {
      }
      lastFocused.current = null;
    }
  }, [isOpen]);

  const onKeyDown = useCallback((e) => {
    if (!isOpen) return;
    if (e.key === 'Escape' && !submitting) {
      e.stopPropagation();
      onClose();
    }
    if (e.key === 'Tab') {
      const tabbables = getTabbables(panelRef.current);
      if (tabbables.length === 0) return;
      const first = tabbables[0];
      const last = tabbables[tabbables.length - 1];
      if (e.shiftKey) {
        if (document.activeElement === first) {
          e.preventDefault();
          last.focus();
        }
      } else {
        if (document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    }
  }, [isOpen, submitting, onClose]);

  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [avatarUrl, setAvatarUrl] = useState('');
  const [uploadingAvatar, setUploadingAvatar] = useState(false);
  const [avatarError, setAvatarError] = useState(null);
  const initials = React.useMemo(() => {
    const fn = (user?.firstName || '').trim();
    const ln = (user?.lastName || '').trim();
    const a = fn ? fn[0] : '';
    const b = ln ? ln[0] : '';
    if (a || b) return (a + b).toUpperCase();
    const un = (user?.username || user?.email || '').trim();
    return un ? un[0].toUpperCase() : '?';
  }, [user]);
  useEffect(() => {
    let revokeUrl;
    if (user && isOpen) {
      setFirstName(user.firstName || '');
      setLastName(user.lastName || '');
      setEmail(user.email || '');
      setPhoneNumber(user.phoneNumber || '');
      (async () => {
        try {
          const blob = await fetchAvatarBlob();
          const url = URL.createObjectURL(blob);
          revokeUrl = url;
          setAvatarUrl(url);
        } catch (_) {
          setAvatarUrl('');
        }
      })();
    }
    return () => {
      if (revokeUrl && revokeUrl.startsWith('blob:')) URL.revokeObjectURL(revokeUrl);
    };
  }, [user, isOpen]);

  // Load preferences into controls when modal opens or when switching to Preferences tab
  useEffect(() => {
    let cancelled = false;
    async function loadPrefs() {
      if (!isOpen) return;
      try {
        const p = await getPreferences();
        if (cancelled || !p) return;
        setPrefs({
          theme: p.theme || 'system',
          language: p.language || 'en',
          emailNotifications: Boolean(p.emailNotifications),
          navbarDisplay: p.navbarDisplay || 'avatar',
        });
      } catch (_) {
        // keep defaults
      }
    }
    loadPrefs();
    return () => { cancelled = true; };
  }, [isOpen, activeTab]);

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [prefs, setPrefs] = useState({theme: 'system', language: 'en', emailNotifications: true, navbarDisplay: 'avatar'});

  const canClose = !submitting;
  const closeIfAllowed = useCallback(() => {
    if (canClose) onClose();
  }, [canClose, onClose]);

  const submitProfile = async (e) => {
    e.preventDefault();
    setAlert(null);
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    const phoneRegex = /^\+?[0-9.\-\s()]{7,20}$/;
    if (!firstName || !lastName) {
      setAlert({type: 'danger', text: 'First and last name are required.'});
      return;
    }
    if (!email || !emailRegex.test(email)) {
      setAlert({type: 'danger', text: 'A valid email is required.'});
      return;
    }
    if (phoneNumber && !phoneRegex.test(phoneNumber)) {
      setAlert({type: 'danger', text: 'Invalid phone number format.'});
      return;
    }
    setSubmitting(true);
    try {
      await updateMe({firstName, lastName, email, phoneNumber});
      await refreshMe();
      setAlert({type: 'success', text: 'Profile updated.'});
    } catch (err) {
      // Attempt to show a specific backend error when available
      const detail = (err && (err.data && (err.data.message || err.data.error || err.data.detail))) || err?.message || '';
      const text = detail ? `Profile update failed. ${detail}` : 'Profile update failed.';
      setAlert({type: 'danger', text});
    } finally {
      setSubmitting(false);
    }
  };

  const submitPassword = async (e) => {
    e.preventDefault();
    setAlert(null);
    if (!currentPassword || !newPassword) {
      setAlert({type: 'danger', text: 'Current and new password are required.'});
      return;
    }
    if (newPassword !== confirmPassword) {
      setAlert({type: 'danger', text: 'Passwords do not match.'});
      return;
    }
    setSubmitting(true);
    try {
      await changePassword({currentPassword, newPassword});
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setAlert({type: 'success', text: 'Password updated.'});
    } catch (err) {
      const detail = (err && (err.data && (err.data.message || err.data.error || err.data.detail))) || err?.message || '';
      const text = detail ? `Password update failed. ${detail}` : 'Password update failed.';
      setAlert({type: 'danger', text});
    } finally {
      setSubmitting(false);
    }
  };

  const submitPreferences = async (e) => {
    e.preventDefault();
    setAlert(null);
    setSubmitting(true);
    try {
      const saved = await apiUpdatePreferences(prefs);
      if (saved) {
        setPrefs({
          theme: saved.theme || 'system',
          language: saved.language || 'en',
          emailNotifications: Boolean(saved.emailNotifications),
          navbarDisplay: saved.navbarDisplay || 'avatar',
        });
        // Persist navbar display locally and notify listeners for instant UI update
        try { localStorage.setItem('mms_navbarDisplay', saved.navbarDisplay || 'avatar'); } catch (_) {}
        try { window.dispatchEvent(new CustomEvent('mms:prefsUpdated', { detail: saved })); } catch (_) {}
      }
      // Apply theme immediately
      if (saved && saved.theme) {
        let effective = 'light';
        if (saved.theme === 'dark') effective = 'dark';
        else if (saved.theme === 'system') {
          const isDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
          effective = isDark ? 'dark' : 'light';
        }
        setTheme(effective);
      }
      if (saved && saved.language) {
        try { localStorage.setItem('mms_lang', saved.language); } catch (_) {}
      }
      setAlert({type: 'success', text: 'Preferences updated.'});
    } catch (err) {
      setAlert({type: 'danger', text: 'Preferences update failed.'});
    } finally {
      setSubmitting(false);
    }
  };

  const SidebarItem = ({tab, label}) => (
    <button type="button"
            className={'btn w-100 text-start mb-1 ' + (activeTab === tab ? 'btn-light' : 'btn-outline-secondary')}
            onClick={() => setActiveTab(tab)}>{label}</button>
  );

  const rightPanel = useMemo(() => {
    if (activeTab === 'profile') return (
      <form onSubmit={submitProfile}>
        <div className="mb-3 border rounded p-3">
          <div className="fw-semibold mb-2">Profile</div>
          <div className="row g-3">
            <div className="col-12 col-md-6">
              <label className="form-label">First Name</label>
              <input className="form-control" value={firstName} onChange={(e) => setFirstName(e.target.value)} />
            </div>
            <div className="col-12 col-md-6">
              <label className="form-label">Last Name</label>
              <input className="form-control" value={lastName} onChange={(e) => setLastName(e.target.value)} />
            </div>
            <div className="col-12 col-md-6">
              <label className="form-label">Email</label>
              <input className="form-control" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
            </div>
            <div className="col-12 col-md-6">
              <label className="form-label">Phone</label>
              <input className="form-control" type="tel" value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} placeholder="Optional" />
            </div>
          </div>
        </div>
        <div className="mb-3 border rounded p-3">
          <div className="fw-semibold mb-2">Profile Picture</div>
          <div
            className="rounded p-3 text-center border"
            style={{ borderStyle: 'dashed' }}
            onDragOver={(e) => { e.preventDefault(); e.stopPropagation(); }}
            onDrop={async (e) => {
              e.preventDefault();
              const file = e.dataTransfer.files && e.dataTransfer.files[0];
              if (!file) return;
              setAvatarError(null);
              if (!file.type.startsWith('image/')) { setAvatarError('Only image files are allowed.'); return; }
              if (file.size > 5 * 1024 * 1024) { setAvatarError('Max size is 5MB.'); return; }
              setUploadingAvatar(true);
              try {
                await uploadAvatar(file);
                const blob = await fetchAvatarBlob();
                const url = URL.createObjectURL(blob);
                setAvatarUrl((old) => {
                  if (old && old.startsWith('blob:')) URL.revokeObjectURL(old);
                  return url;
                });
              } catch (err) {
                setAvatarError('Avatar update failed.');
              } finally {
                setUploadingAvatar(false);
              }
            }}
          >
            {avatarUrl ? (
              <img src={avatarUrl} alt="avatar" className="rounded-circle mb-2" style={{ width: 96, height: 96, objectFit: 'cover' }} />
            ) : (
              <div className="mb-2 d-inline-flex align-items-center justify-content-center rounded-circle bg-secondary text-white" style={{ width: 96, height: 96, fontWeight: 600, fontSize: 24 }}>
                {initials}
              </div>
            )}
            <div className="text-body-secondary small">Drag and drop an image here to upload (PNG/JPG/GIF, max 5 MB).</div>
            {uploadingAvatar && <div className="text-info small mt-2">Uploading…</div>}
            {avatarError && <div className="text-danger small mt-2">{avatarError}</div>}
          </div>
        </div>
        <div className="d-flex gap-2 justify-content-end">
          <button type="button" className="btn btn-outline-secondary" disabled={submitting} onClick={closeIfAllowed}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={submitting}>{submitting ? 'Saving…' : 'Save'}</button>
        </div>
      </form>
    );
    if (activeTab === 'account') {
      const localEnabled = Boolean(SIGNIN_PATH);
      // Only show change-password form when backend explicitly reports hasPassword=true
      const canChange = localEnabled && user?.hasPassword === true;
      if (!canChange) {
        return (
          <div className="alert alert-info" role="alert">
            {localEnabled
              ? 'This account is linked via Google/Apple and does not have a local password. Password changes are managed by your provider.'
              : 'Local password is not enabled. Password changes are managed by your provider.'}
          </div>
        );
      }
      return (
        <form onSubmit={submitPassword}>
          <div className="mb-3">
            <label className="form-label">Current Password</label>
            <input type="password" className="form-control" value={currentPassword}
                   onChange={(e) => setCurrentPassword(e.target.value)} required/>
          </div>
          <div className="mb-3">
            <label className="form-label">New Password</label>
            <input type="password" className="form-control" value={newPassword}
                   onChange={(e) => setNewPassword(e.target.value)} required/>
          </div>
          <div className="mb-3">
            <label className="form-label">Confirm New Password</label>
            <input type="password" className="form-control" value={confirmPassword}
                   onChange={(e) => setConfirmPassword(e.target.value)} required/>
          </div>
          <div className="d-flex gap-2">
            <button type="submit" className="btn btn-warning" disabled={submitting}>
              {submitting && <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>}
              {submitting ? 'Updating…' : 'Update Password'}
            </button>
            <button type="button" className="btn btn-outline-secondary" disabled={submitting}
                    onClick={closeIfAllowed}>Cancel
            </button>
          </div>
        </form>
      );
    }
    return (
      <form onSubmit={submitPreferences}>
        
        <div className="mb-3">
          <div className="border rounded p-3">
            <div className="d-flex justify-content-between align-items-center mb-2">
              <span className="fw-semibold d-inline-flex align-items-center gap-2">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M4 6h16a1 1 0 1 0 0-2H4a1 1 0 1 0 0 2zm0 6h10a1 1 0 1 0 0-2H4a1 1 0 1 0 0 2zm0 6h7a1 1 0 1 0 0-2H4a1 1 0 1 0 0 2z"/></svg>
                <span>Display Preference</span>
              </span>
            </div>
            <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
              <div className="btn-group" role="group" aria-label="Theme selection">
                <input type="radio" className="btn-check" name="theme" id="theme-system"
                       checked={prefs.theme === 'system'} onChange={() => setPrefs({...prefs, theme: 'system'})}/>
                <label className="btn btn-outline-secondary d-flex align-items-center gap-1" htmlFor="theme-system">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M3 5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-6v2h3a1 1 0 1 1 0 2H8a1 1 0 1 1 0-2h3v-2H5a2 2 0 0 1-2-2V5zm2 0v9h14V5H5z"/></svg>
                  <span>System</span>
                </label>

                <input type="radio" className="btn-check" name="theme" id="theme-light"
                       checked={prefs.theme === 'light'} onChange={() => setPrefs({...prefs, theme: 'light'})}/>
                <label className="btn btn-outline-secondary d-flex align-items-center gap-1" htmlFor="theme-light">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 18a6 6 0 1 0 0-12 6 6 0 0 0 0 12zm0-16a1 1 0 0 1 1 1v1a1 1 0 1 1-2 0V3a1 1 0 0 1 1-1zm0 18a1 1 0 0 1 1 1v1a1 1 0 1 1-2 0v-1a1 1 0 0 1 1-1zM3 11h1a1 1 0 1 1 0 2H3a1 1 0 1 1 0-2zm17 0h1a1 1 0 1 1 0 2h-1a1 1 0 1 1 0-2zM5.05 5.05a1 1 0 0 1 1.41 0l.7.7a1 1 0 0 1-1.41 1.41l-.7-.7a1 1 0 0 1 0-1.41zm11.79 11.79a1 1 0 0 1 1.41 0l.7.7a1 1 0 1 1-1.41 1.41l-.7-.7a1 1 0 0 1 0-1.41zM18.95 5.05a1 1 0 0 1 0 1.41l-.7.7a1 1 0 1 1-1.41-1.41l.7-.7a1 1 0 0 1 1.41 0zM5.76 17.24a1 1 0 0 1 0 1.41l-.7.7a1 1 0 1 1-1.41-1.41l.7-.7a1 1 0 0 1 1.41 0z"/></svg>
                  <span>Light</span>
                </label>

                <input type="radio" className="btn-check" name="theme" id="theme-dark"
                       checked={prefs.theme === 'dark'} onChange={() => setPrefs({...prefs, theme: 'dark'})}/>
                <label className="btn btn-outline-secondary d-flex align-items-center gap-1" htmlFor="theme-dark">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M21 12.79A9 9 0 1 1 11.21 3a7 7 0 1 0 9.79 9.79z"/></svg>
                  <span>Dark</span>
                </label>
              </div>
              <div className="btn-group" role="group" aria-label="Display preference">
                <input type="radio" className="btn-check" name="navbarDisplay" id="nav-avatar"
                       checked={prefs.navbarDisplay === 'avatar'} onChange={() => setPrefs({...prefs, navbarDisplay: 'avatar'})}/>
                <label className="btn btn-outline-secondary d-flex align-items-center gap-1" htmlFor="nav-avatar">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 12a5 5 0 1 0-5-5 5 5 0 0 0 5 5zm0 2c-4.33 0-8 2.17-8 5v1a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-1c0-2.83-3.67-5-8-5z"/></svg>
                  <span>Avatar</span>
                </label>
                <input type="radio" className="btn-check" name="navbarDisplay" id="nav-name"
                       checked={prefs.navbarDisplay === 'name'} onChange={() => setPrefs({...prefs, navbarDisplay: 'name'})}/>
                <label className="btn btn-outline-secondary d-flex align-items-center gap-1" htmlFor="nav-name">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M4 6h16a1 1 0 1 0 0-2H4a1 1 0 1 0 0 2zm0 6h10a1 1 0 1 0 0-2H4a1 1 0 1 0 0 2zm0 6h7a1 1 0 1 0 0-2H4a1 1 0 1 0 0 2z"/></svg>
                  <span>Name</span>
                </label>
              </div>
            </div>
            {(() => {
              // Resolve preview theme without complexity and guard for SSR
              let resolved = prefs.theme;
              if (prefs.theme === 'system') {
                try {
                  const isDark = typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
                  resolved = isDark ? 'dark' : 'light';
                } catch (_) {
                  resolved = 'light';
                }
              }
              return (
                <div className="border rounded-3 p-3 bg-body" data-bs-theme={resolved}>
                  <h6 className="mb-1">Preview</h6>
                  <p className="text-body-secondary mb-3">Colors and contrast reflect the theme.</p>
                  <button type="button" className="btn btn-primary btn-sm mb-3">Primary action</button>
                  <div className="card">
                    <div className="card-body">
                      <div className="fw-semibold mb-1">Card title</div>
                      <p className="text-body-secondary mb-0">Example text to show contrast.</p>
                    </div>
                  </div>
                </div>
              );
            })()}
          </div>
        </div>
        <div className="mb-3">
          <div className="border rounded p-3">
            <div className="d-flex justify-content-between align-items-center mb-2">
              <span className="fw-semibold d-inline-flex align-items-center gap-2">
                {/* Globe icon */}
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 2a10 10 0 1 0 10 10A10.011 10.011 0 0 0 12 2zm7.93 9h-3.09a15.26 15.26 0 0 0-1.26-5.01A8.014 8.014 0 0 1 19.93 11zM12 4a13.28 13.28 0 0 1 2.06 7H9.94A13.28 13.28 0 0 1 12 4zM6.42 5.99A15.26 15.26 0 0 0 5.16 11H2.07a8.014 8.014 0 0 1 4.35-5.01zM2.07 13h3.09a15.26 15.26 0 0 0 1.26 5.01A8.014 8.014 0 0 1 2.07 13zM12 20a13.28 13.28 0 0 1-2.06-7h4.12A13.28 13.28 0 0 1 12 20zm5.58-1.99A15.26 15.26 0 0 0 18.84 13h3.09a8.014 8.014 0 0 1-4.35 5.01z"/></svg>
                <span>Language</span>
              </span>
              <span className="text-body-secondary small">Applies to labels and messages</span>
            </div>
            <select className="form-select w-auto" value={prefs.language}
                    onChange={(e) => setPrefs({...prefs, language: e.target.value})} aria-label="Language">
              <option value="en">English</option>
              <option value="es">Español</option>
              <option value="fr">Français</option>
            </select>
          </div>
        </div>
        <div className="mb-3">
          <div className="border rounded p-3">
            <div className="d-flex justify-content-between align-items-center">
              <div>
                <div className="fw-semibold d-inline-flex align-items-center gap-2">
                  {/* Bell icon */}
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 22a2.5 2.5 0 0 0 2.45-2h-4.9A2.5 2.5 0 0 0 12 22zm6-6V11a6 6 0 1 0-12 0v5l-2 2v1h16v-1z"/></svg>
                  <span>Email Notifications</span>
                </div>
                <div className="text-body-secondary small">Receive account and activity updates</div>
              </div>
              <div className="form-check form-switch m-0">
                <input className="form-check-input" type="checkbox" id="emailNotifications" checked={prefs.emailNotifications}
                       onChange={(e) => setPrefs({...prefs, emailNotifications: e.target.checked})} aria-label="Email Notifications"/>
              </div>
            </div>
          </div>
        </div>
        <div className="d-flex gap-2">
          <button type="submit" className="btn btn-primary"
                  disabled={submitting}>{submitting ? 'Saving…' : 'Save Preferences'}</button>
          <button type="button" className="btn btn-outline-secondary" disabled={submitting}
                  onClick={closeIfAllowed}>Cancel
          </button>
        </div>
      </form>
    );
  }, [activeTab, firstName, lastName, email, phoneNumber, avatarUrl, currentPassword, newPassword, confirmPassword, prefs, submitting]);

  if (!isOpen) return null;

  return (
    <div className="position-fixed top-0 start-0 w-100 h-100" role="dialog" aria-modal="true"
         aria-labelledby="accountSettingsTitle" onKeyDown={onKeyDown} style={{zIndex: 1055}}>
      <div className="position-absolute top-0 start-0 w-100 h-100 bg-dark opacity-50" onClick={() => {
        if (canClose) onClose();
      }}></div>
      <div
        className="position-absolute top-0 start-0 w-100 h-100 overflow-auto d-flex align-items-start align-items-md-center justify-content-center p-0 p-md-4">
        <div ref={panelRef} className="bg-body rounded-0 rounded-md-3 shadow w-100" style={{maxWidth: '64rem'}}
             tabIndex={-1}>
          <div className="d-flex align-items-start gap-3 p-4 border-bottom">
            <div className="flex-grow-1">
              <h2 id="accountSettingsTitle" className="h5 mb-0">Account Settings</h2>
              <div className="text-muted small">{user?.firstName} {user?.lastName}</div>
            </div>
            <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => {
              if (canClose) onClose();
            }} disabled={!canClose} aria-label="Close">×
            </button>
          </div>
          <div className="d-flex flex-column flex-md-row">
            <aside className="border-bottom border-md-end p-3" style={{minWidth: '14rem'}}>
              <SidebarItem tab="profile" label="Profile"/>
              {user?.hasPassword === true && <SidebarItem tab="account" label="Account"/>}
              <SidebarItem tab="preferences" label="Preferences"/>
            </aside>
            <section className="flex-grow-1 p-4">
              {alert && (
                <div className={`alert alert-${alert.type} d-flex justify-content-between align-items-center`} role="alert" aria-live="polite">
                  <div>{alert.text}</div>
                  <button type="button" className="btn-close" aria-label="Close" onClick={() => setAlert(null)}></button>
                </div>
              )}
              {rightPanel}
            </section>
          </div>
        </div>
      </div>
      {/* Toast disabled as requested */}
    </div>
  );
}

function getTabbables(container) {
  if (!container) return [];
  const sel = [
    'a[href]', 'area[href]', 'input:not([disabled])', 'select:not([disabled])',
    'textarea:not([disabled])', 'button:not([disabled])', '[tabindex]:not([tabindex="-1"])'
  ].join(',');
  return Array.from(container.querySelectorAll(sel)).filter((el) => !el.hasAttribute('disabled'));
}
