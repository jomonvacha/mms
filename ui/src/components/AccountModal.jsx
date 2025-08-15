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
        <div className="mb-3">
          <label className="form-label">First name</label>
          <input className="form-control" value={firstName} onChange={(e) => setFirstName(e.target.value)}/>
        </div>
        <div className="mb-3">
          <label className="form-label">Last name</label>
          <input className="form-control" value={lastName} onChange={(e) => setLastName(e.target.value)}/>
        </div>
        <div className="mb-3">
          <label className="form-label">Email</label>
          <input className="form-control" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required/>
        </div>
        <div className="mb-3">
          <label className="form-label">Phone</label>
          <input className="form-control" type="tel" value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} placeholder="Optional"/>
        </div>
        <div className="mb-3">
          <label className="form-label">Profile picture</label>
          <div
            className="border rounded p-3 text-center"
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
            <div className="text-muted small">Drag & drop an image here to upload (PNG/JPG/GIF, max 5MB)</div>
            {uploadingAvatar && <div className="text-info small mt-2">Uploading…</div>}
            {avatarError && <div className="text-danger small mt-2">{avatarError}</div>}
          </div>
        </div>
          <div className="d-flex gap-2">
            <button type="submit" className="btn btn-primary"
                    disabled={submitting}>{submitting ? 'Saving…' : 'Save'}</button>
            <button type="button" className="btn btn-outline-secondary" disabled={submitting}
                    onClick={closeIfAllowed}>Cancel
            </button>
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
            <label className="form-label">Current password</label>
            <input type="password" className="form-control" value={currentPassword}
                   onChange={(e) => setCurrentPassword(e.target.value)} required/>
          </div>
          <div className="mb-3">
            <label className="form-label">New password</label>
            <input type="password" className="form-control" value={newPassword}
                   onChange={(e) => setNewPassword(e.target.value)} required/>
          </div>
          <div className="mb-3">
            <label className="form-label">Confirm new password</label>
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
        <fieldset className="mb-3">
          <legend className="form-label">Theme</legend>
          {['system', 'light', 'dark'].map((opt) => (
            <div className="form-check" key={opt}>
              <input className="form-check-input" type="radio" name="theme" id={`theme-${opt}`}
                     checked={prefs.theme === opt} onChange={() => setPrefs({...prefs, theme: opt})}/>
              <label className="form-check-label" htmlFor={`theme-${opt}`}>{opt}</label>
            </div>
          ))}
        </fieldset>
        <fieldset className="mb-3">
          <legend className="form-label">Navbar display</legend>
          {['avatar','name'].map((opt) => (
            <div className="form-check" key={opt}>
              <input className="form-check-input" type="radio" name="navbarDisplay" id={`nav-${opt}`}
                     checked={prefs.navbarDisplay === opt} onChange={() => setPrefs({...prefs, navbarDisplay: opt})}/>
              <label className="form-check-label" htmlFor={`nav-${opt}`}>{opt.charAt(0).toUpperCase()+opt.slice(1)}</label>
            </div>
          ))}
        </fieldset>
        <div className="mb-3">
          <label className="form-label">Language</label>
          <select className="form-select" value={prefs.language}
                  onChange={(e) => setPrefs({...prefs, language: e.target.value})}>
            <option value="en">English</option>
            <option value="es">Español</option>
            <option value="fr">Français</option>
          </select>
        </div>
        <div className="form-check form-switch mb-3">
          <input className="form-check-input" type="checkbox" id="emailNotifications" checked={prefs.emailNotifications}
                 onChange={(e) => setPrefs({...prefs, emailNotifications: e.target.checked})}/>
          <label className="form-check-label" htmlFor="emailNotifications">Email notifications</label>
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
              <h2 id="accountSettingsTitle" className="h5 mb-0">Account settings</h2>
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
