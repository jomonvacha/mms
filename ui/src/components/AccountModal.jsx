import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from '../hooks/useAuth.js';
import { updateMe, changePassword, updatePreferences as apiUpdatePreferences } from '../api/client.js';

export default function AccountModal({ isOpen, initialTab = 'profile', onClose }) {
  const { user, refreshMe } = useAuth();
  const [activeTab, setActiveTab] = useState(initialTab);
  const [submitting, setSubmitting] = useState(false);
  const [alert, setAlert] = useState(null);
  const panelRef = useRef(null);
  const lastFocused = useRef(null);

  useEffect(() => { if (isOpen) setActiveTab(initialTab); }, [isOpen, initialTab]);

  useEffect(() => {
    if (!isOpen) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = prev; };
  }, [isOpen]);

  useEffect(() => {
    if (isOpen) {
      lastFocused.current = document.activeElement;
      const tabbables = getTabbables(panelRef.current);
      (tabbables[0] || panelRef.current)?.focus();
    } else if (lastFocused.current) {
      try { lastFocused.current.focus(); } catch (_) {}
      lastFocused.current = null;
    }
  }, [isOpen]);

  const onKeyDown = useCallback((e) => {
    if (!isOpen) return;
    if (e.key === 'Escape' && !submitting) { e.stopPropagation(); onClose(); }
    if (e.key === 'Tab') {
      const tabbables = getTabbables(panelRef.current);
      if (tabbables.length === 0) return;
      const first = tabbables[0];
      const last = tabbables[tabbables.length - 1];
      if (e.shiftKey) {
        if (document.activeElement === first) { e.preventDefault(); last.focus(); }
      } else {
        if (document.activeElement === last) { e.preventDefault(); first.focus(); }
      }
    }
  }, [isOpen, submitting, onClose]);

  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [avatarUrl, setAvatarUrl] = useState('');
  useEffect(() => {
    if (user && isOpen) {
      setFirstName(user.firstName || '');
      setLastName(user.lastName || '');
      setDisplayName([user.firstName, user.lastName].filter(Boolean).join(' '));
      setAvatarUrl(user.avatarUrl || '');
      setAlert(null);
    }
  }, [user, isOpen]);

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [prefs, setPrefs] = useState({ theme: 'system', language: 'en', emailNotifications: true });

  const canClose = !submitting;
  const closeIfAllowed = useCallback(() => { if (canClose) onClose(); }, [canClose, onClose]);

  const submitProfile = async (e) => {
    e.preventDefault();
    setAlert(null);
    if (!firstName || !lastName) { setAlert({ type: 'danger', text: 'First and last name are required.' }); return; }
    setSubmitting(true);
    try {
      await updateMe({ firstName, lastName, phoneNumber: user?.phoneNumber });
      await refreshMe();
      setAlert({ type: 'success', text: 'Profile updated.' });
    } catch (err) {
      setAlert({ type: 'danger', text: err?.message || 'Failed to update profile.' });
    } finally { setSubmitting(false); }
  };

  const submitPassword = async (e) => {
    e.preventDefault();
    setAlert(null);
    if (!currentPassword || !newPassword) { setAlert({ type: 'danger', text: 'Current and new password are required.' }); return; }
    if (newPassword !== confirmPassword) { setAlert({ type: 'danger', text: 'Passwords do not match.' }); return; }
    setSubmitting(true);
    try {
      await changePassword({ currentPassword, newPassword });
      setCurrentPassword(''); setNewPassword(''); setConfirmPassword('');
      setAlert({ type: 'success', text: 'Password updated.' });
    } catch (err) {
      setAlert({ type: 'danger', text: err?.message || 'Failed to update password.' });
    } finally { setSubmitting(false); }
  };

  const submitPreferences = async (e) => {
    e.preventDefault();
    setAlert(null);
    setSubmitting(true);
    try {
      await apiUpdatePreferences(prefs);
      setAlert({ type: 'success', text: 'Preferences saved.' });
    } catch (err) {
      setAlert({ type: 'danger', text: err?.message || 'Failed to save preferences.' });
    } finally { setSubmitting(false); }
  };

  const SidebarItem = ({ tab, label }) => (
    <button type="button" className={'btn w-100 text-start mb-1 ' + (activeTab === tab ? 'btn-light' : 'btn-outline-secondary')}
      onClick={() => setActiveTab(tab)}>{label}</button>
  );

  const rightPanel = useMemo(() => {
    if (activeTab === 'profile') return (
      <form onSubmit={submitProfile}>
        <div className="mb-3">
          <label className="form-label">First name</label>
          <input className="form-control" value={firstName} onChange={(e) => setFirstName(e.target.value)} />
        </div>
        <div className="mb-3">
          <label className="form-label">Last name</label>
          <input className="form-control" value={lastName} onChange={(e) => setLastName(e.target.value)} />
        </div>
        <div className="mb-3">
          <label className="form-label">Display name</label>
          <input className="form-control" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
        </div>
        <div className="mb-3">
          <label className="form-label">Avatar URL (optional)</label>
          <input className="form-control" value={avatarUrl} onChange={(e) => setAvatarUrl(e.target.value)} placeholder="https://…" />
        </div>
        <div className="d-flex gap-2">
          <button type="submit" className="btn btn-primary" disabled={submitting}>{submitting ? 'Saving…' : 'Save'}</button>
          <button type="button" className="btn btn-outline-secondary" disabled={submitting} onClick={closeIfAllowed}>Cancel</button>
        </div>
      </form>
    );
    if (activeTab === 'account') return (
      <form onSubmit={submitPassword}>
        <div className="mb-3">
          <label className="form-label">Current password</label>
          <input type="password" className="form-control" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} required />
        </div>
        <div className="mb-3">
          <label className="form-label">New password</label>
          <input type="password" className="form-control" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} required />
        </div>
        <div className="mb-3">
          <label className="form-label">Confirm new password</label>
          <input type="password" className="form-control" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} required />
        </div>
        <div className="d-flex gap-2">
          <button type="submit" className="btn btn-warning" disabled={submitting}>{submitting ? 'Updating…' : 'Update Password'}</button>
          <button type="button" className="btn btn-outline-secondary" disabled={submitting} onClick={closeIfAllowed}>Cancel</button>
        </div>
      </form>
    );
    return (
      <form onSubmit={submitPreferences}>
        <fieldset className="mb-3">
          <legend className="form-label">Theme</legend>
          {['system','light','dark'].map((opt) => (
            <div className="form-check" key={opt}>
              <input className="form-check-input" type="radio" name="theme" id={`theme-${opt}`} checked={prefs.theme === opt} onChange={() => setPrefs({ ...prefs, theme: opt })} />
              <label className="form-check-label" htmlFor={`theme-${opt}`}>{opt}</label>
            </div>
          ))}
        </fieldset>
        <div className="mb-3">
          <label className="form-label">Language</label>
          <select className="form-select" value={prefs.language} onChange={(e) => setPrefs({ ...prefs, language: e.target.value })}>
            <option value="en">English</option>
            <option value="es">Español</option>
            <option value="fr">Français</option>
          </select>
        </div>
        <div className="form-check form-switch mb-3">
          <input className="form-check-input" type="checkbox" id="emailNotifications" checked={prefs.emailNotifications} onChange={(e) => setPrefs({ ...prefs, emailNotifications: e.target.checked })} />
          <label className="form-check-label" htmlFor="emailNotifications">Email notifications</label>
        </div>
        <div className="d-flex gap-2">
          <button type="submit" className="btn btn-primary" disabled={submitting}>{submitting ? 'Saving…' : 'Save Preferences'}</button>
          <button type="button" className="btn btn-outline-secondary" disabled={submitting} onClick={closeIfAllowed}>Cancel</button>
        </div>
      </form>
    );
  }, [activeTab, firstName, lastName, displayName, avatarUrl, currentPassword, newPassword, confirmPassword, prefs, submitting]);

  if (!isOpen) return null;

  return (
    <div className="position-fixed top-0 start-0 w-100 h-100" role="dialog" aria-modal="true" aria-labelledby="accountSettingsTitle" onKeyDown={onKeyDown} style={{ zIndex: 1055 }}>
      <div className="position-absolute top-0 start-0 w-100 h-100 bg-dark opacity-50" onClick={() => { if (canClose) onClose(); }}></div>
      <div className="position-absolute top-0 start-0 w-100 h-100 overflow-auto d-flex align-items-start align-items-md-center justify-content-center p-0 p-md-4">
        <div ref={panelRef} className="bg-white rounded-0 rounded-md-3 shadow w-100" style={{ maxWidth: '64rem' }} tabIndex={-1}>
          <div className="d-flex align-items-start gap-3 p-4 border-bottom">
            <div className="flex-grow-1">
              <h2 id="accountSettingsTitle" className="h5 mb-0">Account settings</h2>
              <div className="text-muted small">{user?.firstName} {user?.lastName}</div>
            </div>
            <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => { if (canClose) onClose(); }} disabled={!canClose} aria-label="Close">×</button>
          </div>
          <div className="d-flex flex-column flex-md-row">
            <aside className="border-bottom border-md-end p-3" style={{ minWidth: '14rem' }}>
              <SidebarItem tab="profile" label="Profile" />
              <SidebarItem tab="account" label="Account" />
              <SidebarItem tab="preferences" label="Preferences" />
            </aside>
            <section className="flex-grow-1 p-4">
              {alert && (
                <div className={`alert alert-${alert.type}`} role="alert">{alert.text}</div>
              )}
              {rightPanel}
            </section>
          </div>
        </div>
      </div>
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
