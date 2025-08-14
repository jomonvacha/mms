import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { updatePassword, updatePreferences, updateProfile, type User, type UserPreferences } from '../../services/api';

type TabKey = 'profile' | 'account' | 'preferences';

type Props = {
  isOpen: boolean;
  initialTab?: TabKey;
  onClose: () => void;
  user: Pick<User, 'id' | 'firstName' | 'lastName' | 'email' | 'avatarUrl'>;
};

// Utility to find tabbable elements for focus trapping
function getTabbables(container: HTMLElement | null): HTMLElement[] {
  if (!container) return [];
  const sel = [
    'a[href]', 'area[href]', 'input:not([disabled])', 'select:not([disabled])',
    'textarea:not([disabled])', 'button:not([disabled])', 'iframe', 'object', 'embed',
    '[tabindex]:not([tabindex="-1"])', '[contenteditable]'
  ].join(',');
  return Array.from(container.querySelectorAll<HTMLElement>(sel)).filter(el => !el.hasAttribute('disabled') && !el.getAttribute('aria-hidden'));
}

export default function AccountModal({ isOpen, initialTab = 'profile', onClose, user }: Props) {
  const [activeTab, setActiveTab] = useState<TabKey>(initialTab);
  const [submitting, setSubmitting] = useState(false);
  const [alert, setAlert] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const lastFocused = useRef<HTMLElement | null>(null);

  // Sync tab when the modal opens with a new intent
  useEffect(() => { if (isOpen) setActiveTab(initialTab); }, [isOpen, initialTab]);

  // Body scroll lock
  useEffect(() => {
    if (!isOpen) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = prev; };
  }, [isOpen]);

  // Focus trap setup
  useEffect(() => {
    if (isOpen) {
      lastFocused.current = document.activeElement as HTMLElement | null;
      // focus close button or first tabbable
      const tabbables = getTabbables(panelRef.current);
      (tabbables[0] || panelRef.current)?.focus();
    } else if (lastFocused.current) {
      lastFocused.current.focus();
      lastFocused.current = null;
    }
  }, [isOpen]);

  const onKeyDown = useCallback((e: React.KeyboardEvent) => {
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

  const closeIfAllowed = useCallback(() => { if (!submitting) onClose(); }, [submitting, onClose]);

  // Handlers for forms
  const [firstName, setFirstName] = useState(user.firstName || '');
  const [lastName, setLastName] = useState(user.lastName || '');
  const [displayName, setDisplayName] = useState('');
  const [avatarUrl, setAvatarUrl] = useState(user.avatarUrl || '');

  useEffect(() => {
    if (!isOpen) return;
    setFirstName(user.firstName || '');
    setLastName(user.lastName || '');
    setDisplayName([user.firstName, user.lastName].filter(Boolean).join(' '));
    setAvatarUrl(user.avatarUrl || '');
    setAlert(null);
  }, [isOpen, user]);

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [prefs, setPrefs] = useState<UserPreferences>({ theme: 'system', language: 'en', emailNotifications: true });

  const canClose = !submitting;

  const submitProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setAlert(null);
    if (!firstName || !lastName) { setAlert({ type: 'error', text: 'First and last name are required.' }); return; }
    setSubmitting(true);
    try {
      await updateProfile({ firstName, lastName, displayName, avatarUrl: avatarUrl || undefined });
      setAlert({ type: 'success', text: 'Profile updated.' });
    } catch (err: any) {
      setAlert({ type: 'error', text: err?.message || 'Failed to update profile.' });
    } finally { setSubmitting(false); }
  };

  const submitPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setAlert(null);
    if (!currentPassword || !newPassword) { setAlert({ type: 'error', text: 'Current and new password are required.' }); return; }
    if (newPassword !== confirmPassword) { setAlert({ type: 'error', text: 'Passwords do not match.' }); return; }
    setSubmitting(true);
    try {
      await updatePassword({ currentPassword, newPassword });
      setAlert({ type: 'success', text: 'Password updated.' });
      setCurrentPassword(''); setNewPassword(''); setConfirmPassword('');
    } catch (err: any) {
      setAlert({ type: 'error', text: err?.message || 'Failed to update password.' });
    } finally { setSubmitting(false); }
  };

  const submitPreferences = async (e: React.FormEvent) => {
    e.preventDefault();
    setAlert(null);
    setSubmitting(true);
    try {
      const saved = await updatePreferences(prefs);
      setPrefs(saved);
      setAlert({ type: 'success', text: 'Preferences saved.' });
    } catch (err: any) {
      setAlert({ type: 'error', text: err?.message || 'Failed to save preferences.' });
    } finally { setSubmitting(false); }
  };

  const SidebarItem = ({ tab, label }: { tab: TabKey; label: string }) => (
    <button
      type="button"
      className={
        'w-full text-left px-3 py-2 rounded-lg transition ' +
        (activeTab === tab ? 'bg-neutral-100 dark:bg-neutral-800 font-medium' : 'hover:bg-neutral-100 dark:hover:bg-neutral-800')
      }
      onClick={() => setActiveTab(tab)}
    >{label}</button>
  );

  const rightPanel = useMemo(() => {
    if (activeTab === 'profile') return (
      <form onSubmit={submitProfile} className="space-y-6">
        <div>
          <label className="block text-sm font-medium mb-1">First name</label>
          <input value={firstName} onChange={e => setFirstName(e.target.value)} className="w-full rounded-xl border dark:border-neutral-700 px-3 py-2 focus:outline-none focus:ring-2" />
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">Last name</label>
          <input value={lastName} onChange={e => setLastName(e.target.value)} className="w-full rounded-xl border dark:border-neutral-700 px-3 py-2 focus:outline-none focus:ring-2" />
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">Display name</label>
          <input value={displayName} onChange={e => setDisplayName(e.target.value)} className="w-full rounded-xl border dark:border-neutral-700 px-3 py-2 focus:outline-none focus:ring-2" />
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">Avatar</label>
          <div className="flex items-center gap-3">
            <img src={avatarUrl || 'https://via.placeholder.com/48'} alt="avatar" className="w-12 h-12 rounded-full object-cover" />
            <input type="url" placeholder="Image URL" value={avatarUrl} onChange={e => setAvatarUrl(e.target.value)} className="flex-1 rounded-xl border dark:border-neutral-700 px-3 py-2 focus:outline-none focus:ring-2" />
          </div>
        </div>
        <div className="flex gap-2">
          <button type="submit" disabled={submitting} className="px-4 py-2 rounded-xl bg-blue-600 text-white disabled:opacity-50">{submitting ? 'Saving…' : 'Save'}</button>
          <button type="button" disabled={submitting} onClick={closeIfAllowed} className="px-4 py-2 rounded-xl border dark:border-neutral-700">Cancel</button>
        </div>
      </form>
    );
    if (activeTab === 'account') return (
      <form onSubmit={submitPassword} className="space-y-6">
        <div>
          <label className="block text-sm font-medium mb-1">Current password</label>
          <input type="password" value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} className="w-full rounded-xl border dark:border-neutral-700 px-3 py-2 focus:outline-none focus:ring-2" required />
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">New password</label>
          <input type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} className="w-full rounded-xl border dark:border-neutral-700 px-3 py-2 focus:outline-none focus:ring-2" required />
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">Confirm new password</label>
          <input type="password" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} className="w-full rounded-xl border dark:border-neutral-700 px-3 py-2 focus:outline-none focus:ring-2" required />
        </div>
        <div className="flex gap-2">
          <button type="submit" disabled={submitting} className="px-4 py-2 rounded-xl bg-amber-600 text-white disabled:opacity-50">{submitting ? 'Updating…' : 'Update Password'}</button>
          <button type="button" disabled={submitting} onClick={closeIfAllowed} className="px-4 py-2 rounded-xl border dark:border-neutral-700">Cancel</button>
        </div>
      </form>
    );
    return (
      <form onSubmit={submitPreferences} className="space-y-6">
        <fieldset>
          <legend className="block text-sm font-medium mb-2">Theme</legend>
          <div className="space-y-2">
            {(['system', 'light', 'dark'] as const).map(opt => (
              <label key={opt} className="flex items-center gap-2">
                <input type="radio" name="theme" value={opt} checked={prefs.theme === opt} onChange={() => setPrefs({ ...prefs, theme: opt })} />
                <span className="capitalize">{opt}</span>
              </label>
            ))}
          </div>
        </fieldset>
        <div>
          <label className="block text-sm font-medium mb-1">Language</label>
          <select value={prefs.language} onChange={e => setPrefs({ ...prefs, language: e.target.value })} className="w-full rounded-xl border dark:border-neutral-700 px-3 py-2 focus:outline-none focus:ring-2">
            <option value="en">English</option>
            <option value="es">Español</option>
            <option value="fr">Français</option>
          </select>
        </div>
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={prefs.emailNotifications} onChange={e => setPrefs({ ...prefs, emailNotifications: e.target.checked })} />
          <span>Email notifications</span>
        </label>
        <div className="flex gap-2">
          <button type="submit" disabled={submitting} className="px-4 py-2 rounded-xl bg-blue-600 text-white disabled:opacity-50">{submitting ? 'Saving…' : 'Save Preferences'}</button>
          <button type="button" disabled={submitting} onClick={closeIfAllowed} className="px-4 py-2 rounded-xl border dark:border-neutral-700">Cancel</button>
        </div>
      </form>
    );
  }, [activeTab, avatarUrl, canClose, closeIfAllowed, confirmPassword, currentPassword, displayName, firstName, lastName, newPassword, prefs, submitPassword]);

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50"
      onKeyDown={onKeyDown}
      role="dialog"
      aria-modal="true"
      aria-labelledby="account-settings-title"
    >
      <div
        className="absolute inset-0 bg-black/40"
        onClick={() => { if (canClose) onClose(); }}
      />
      <div className="absolute inset-0 overflow-y-auto">
        <div className="min-h-full flex md:items-center items-stretch justify-center p-0 md:p-4">
          <div
            ref={panelRef}
            className="w-full max-w-4xl md:rounded-2xl rounded-none md:h-auto h-screen bg-white dark:bg-neutral-900 shadow-2xl outline-none focus:outline-none"
          >
            <div className="flex items-start gap-4 p-6 border-b dark:border-neutral-800">
              <div className="flex-1">
                <h2 id="account-settings-title" className="text-xl font-semibold">Account settings</h2>
                <p className="text-sm text-neutral-500 dark:text-neutral-400">{user.firstName} {user.lastName}</p>
              </div>
              <button
                type="button"
                aria-label="Close"
                className="p-2 rounded-lg hover:bg-neutral-100 dark:hover:bg-neutral-800"
                onClick={() => { if (canClose) onClose(); }}
                disabled={!canClose}
              >
                <span aria-hidden>✕</span>
              </button>
            </div>
            <div className="flex flex-col md:flex-row">
              <aside className="w-full md:w-56 border-b md:border-b-0 md:border-r dark:border-neutral-800 p-4 space-y-1 sticky top-0">
                <SidebarItem tab="profile" label="Profile" />
                <SidebarItem tab="account" label="Account" />
                <SidebarItem tab="preferences" label="Preferences" />
              </aside>
              <section className="flex-1 p-6 space-y-6">
                {alert && (
                  <div className={`rounded-xl px-3 py-2 ${alert.type === 'success' ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300' : 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300'}`}>{alert.text}</div>
                )}
                {rightPanel}
              </section>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
