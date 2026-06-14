import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { X, User, KeyRound, Settings, Sun, Moon, Monitor, Globe, Bell, Upload, Loader2, ShieldCheck, Lock, Smartphone, Copy, Check, AlertCircle, Camera, Trash2 } from 'lucide-react'
import { notify } from './Toast'
import AvatarCropper from './AvatarCropper'
import { useAuth } from '../hooks/useAuth'
import type { AuthProvider } from '../hooks/useAuth'
import { useTheme } from '../hooks/useTheme'
import type { Theme } from '../hooks/useTheme'
import {
  changePassword, disconnectProvider, updateMe, updatePreferences as apiUpdatePreferences,
  uploadAvatar, getMyAvatarBlob as fetchAvatarBlob, getPreferences, type PrefsRecord,
  twoFactorSetup, twoFactorEnable, twoFactorDisable,
} from '../api/client'

type Tab = 'profile' | 'account' | 'preferences' | 'security'

interface Props {
  isOpen: boolean
  initialTab?: Tab
  onClose: () => void
}

function providerLabel(p?: AuthProvider): string {
  if (p === 'GOOGLE') return 'Google'
  if (p === 'APPLE') return 'Apple'
  return 'your identity provider'
}

function isFederated(p?: AuthProvider): boolean {
  return p === 'GOOGLE' || p === 'APPLE'
}

function getTabbables(container: HTMLElement | null): HTMLElement[] {
  if (!container) return []
  const sel = [
    'a[href]', 'area[href]', 'input:not([disabled])', 'select:not([disabled])',
    'textarea:not([disabled])', 'button:not([disabled])', '[tabindex]:not([tabindex="-1"])',
  ].join(',')
  return Array.from(container.querySelectorAll<HTMLElement>(sel))
}

export default function AccountModal({ isOpen, initialTab = 'profile', onClose }: Props) {
  const { user, refreshMe } = useAuth()
  const { setTheme } = useTheme()
  const [activeTab, setActiveTab] = useState<Tab>(initialTab)
  const [submitting, setSubmitting] = useState(false)
  const panelRef = useRef<HTMLDivElement>(null)
  const lastFocused = useRef<Element | null>(null)

  useEffect(() => { if (isOpen) setActiveTab(initialTab) }, [isOpen, initialTab])

  useEffect(() => {
    if (!isOpen) return
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = prev }
  }, [isOpen])

  useEffect(() => {
    if (isOpen) {
      lastFocused.current = document.activeElement
      const tabbables = getTabbables(panelRef.current)
      ;(tabbables[0] || panelRef.current)?.focus()
    } else if (lastFocused.current) {
      try { (lastFocused.current as HTMLElement).focus() } catch (_) {}
      lastFocused.current = null
    }
  }, [isOpen])

  const onKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (!isOpen) return
    if (e.key === 'Escape' && !submitting) { e.stopPropagation(); onClose() }
    if (e.key === 'Tab') {
      const tabbables = getTabbables(panelRef.current)
      if (!tabbables.length) return
      const first = tabbables[0], last = tabbables[tabbables.length - 1]
      if (e.shiftKey) { if (document.activeElement === first) { e.preventDefault(); last.focus() } }
      else { if (document.activeElement === last) { e.preventDefault(); first.focus() } }
    }
  }, [isOpen, submitting, onClose])

  // Profile state
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [avatarUrl, setAvatarUrl] = useState('')
  const [uploadingAvatar, setUploadingAvatar] = useState(false)
  const [cropSrc, setCropSrc] = useState<string | null>(null) // image to crop before upload

  const initials = useMemo(() => {
    const fn = (user?.firstName || '').trim()
    const ln = (user?.lastName || '').trim()
    const a = fn ? fn[0] : '', b = ln ? ln[0] : ''
    if (a || b) return (a + b).toUpperCase()
    const un = (user?.username || user?.email || '').trim()
    return un ? un[0].toUpperCase() : '?'
  }, [user])

  useEffect(() => {
    let revokeUrl: string | undefined
    if (user && isOpen) {
      setFirstName(user.firstName || '')
      setLastName(user.lastName || '')
      setEmail(user.email || '')
      setPhoneNumber(user.phoneNumber || '')
      fetchAvatarBlob()
        .then((blob) => {
          const url = URL.createObjectURL(blob)
          revokeUrl = url
          setAvatarUrl(url)
        })
        .catch(() => setAvatarUrl(''))
    }
    return () => { if (revokeUrl) URL.revokeObjectURL(revokeUrl) }
  }, [user, isOpen])

  // Password state
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  // 2FA state (Security tab)
  const [twoFaSetup, setTwoFaSetup] = useState<{ secret: string; otpauthUri: string } | null>(null)
  const [twoFaCode, setTwoFaCode] = useState('')
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null)
  const [copiedRecovery, setCopiedRecovery] = useState(false)
  const [disableConfirm, setDisableConfirm] = useState('')

  // Prefs state
  const [prefs, setPrefs] = useState<PrefsRecord>({ theme: 'system', language: 'en', emailNotifications: true, navbarDisplay: 'avatar' })

  useEffect(() => {
    let cancelled = false
    async function loadPrefs() {
      if (!isOpen) return
      try {
        const p = await getPreferences()
        if (cancelled || !p) return
        setPrefs({ theme: p.theme || 'system', language: p.language || 'en', emailNotifications: Boolean(p.emailNotifications), navbarDisplay: p.navbarDisplay || 'avatar' })
      } catch (_) {}
    }
    loadPrefs()
    return () => { cancelled = true }
  }, [isOpen, activeTab])

  // Open the cropper when a file is selected
  const handleAvatarFileSelect = (file: File) => {
    if (!file.type.startsWith('image/')) { notify.error('Only image files.'); return }
    if (file.size > 10 * 1024 * 1024) { notify.error('Max 10 MB.'); return }
    const reader = new FileReader()
    reader.onload = () => { if (typeof reader.result === 'string') setCropSrc(reader.result) }
    reader.readAsDataURL(file)
  }

  // Called by AvatarCropper after crop is confirmed
  const handleCroppedAvatar = async (blob: Blob) => {
    setUploadingAvatar(true)
    setCropSrc(null)
    try {
      const file = new File([blob], 'avatar.jpg', { type: 'image/jpeg' })
      await uploadAvatar(file)
      const fresh = await fetchAvatarBlob()
      const url = URL.createObjectURL(fresh)
      setAvatarUrl((old) => { if (old?.startsWith('blob:')) URL.revokeObjectURL(old); return url })
      try { window.dispatchEvent(new CustomEvent('mms:avatarUpdated')) } catch (_) {}
      if (prefs.navbarDisplay !== 'avatar') {
        const updated = { ...prefs, navbarDisplay: 'avatar' }
        setPrefs(updated as PrefsRecord)
        try {
          await apiUpdatePreferences(updated as Record<string, unknown>)
          window.dispatchEvent(new CustomEvent('mms:prefsUpdated', { detail: updated }))
        } catch (_) {}
      }
      notify.success('Avatar updated.')
    } catch (_) { notify.error('Upload failed.') }
    finally { setUploadingAvatar(false) }
  }

  const submitProfile = async (e: React.FormEvent) => {
    e.preventDefault()
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!firstName || !lastName) { notify.error('First and last name are required.'); return }
    if (!email || !emailRegex.test(email)) { notify.error('A valid email is required.'); return }
    setSubmitting(true)
    try {
      // Federated accounts: don't send email — it's owned by the identity provider
      // and the backend rejects local changes anyway. Sending the unchanged value
      // would still trigger an unnecessary equality check on the server.
      const payload: { firstName: string; lastName: string; phoneNumber: string; email?: string } =
        { firstName, lastName, phoneNumber }
      if (!isFederated(user?.provider)) payload.email = email
      await updateMe(payload)
      await refreshMe()
      notify.success('Profile updated.')
    } catch (err) {
      const e = err as { data?: { message?: string }; message?: string }
      notify.error(e?.data?.message || e?.message || 'Profile update failed.')
    } finally {
      setSubmitting(false)
    }
  }

  const submitPassword = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!currentPassword || !newPassword) { notify.error('Current and new password are required.'); return }
    if (newPassword !== confirmPassword) { notify.error('Passwords do not match.'); return }
    setSubmitting(true)
    try {
      await changePassword({ currentPassword, newPassword })
      setCurrentPassword(''); setNewPassword(''); setConfirmPassword('')
      notify.success('Password updated.')
    } catch (err) {
      const e = err as { data?: { message?: string }; message?: string }
      notify.error(e?.data?.message || e?.message || 'Password update failed.')
    } finally {
      setSubmitting(false)
    }
  }

  const submitPreferences = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    try {
      const payload = {
        theme: prefs.theme || 'system',
        language: prefs.language || 'en',
        emailNotifications: Boolean(prefs.emailNotifications),
        navbarDisplay: prefs.navbarDisplay || 'avatar',
      }
      const saved = await apiUpdatePreferences(payload as Record<string, unknown>) as PrefsRecord
      if (saved) {
        setPrefs({ theme: saved.theme || 'system', language: saved.language || 'en', emailNotifications: Boolean(saved.emailNotifications), navbarDisplay: saved.navbarDisplay || 'avatar' })
        try { window.dispatchEvent(new CustomEvent('mms:prefsUpdated', { detail: saved })) } catch (_) {}
      }
      if (saved?.theme) {
        let effective: Theme = 'light'
        if (saved.theme === 'dark') effective = 'dark'
        else if (saved.theme === 'system') effective = window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
        setTheme(effective)
      }
      if (saved?.language) try { localStorage.setItem('mms_lang', saved.language) } catch (_) {}
      notify.success('Preferences saved.')
    } catch (err) {
      const e = err as { data?: { message?: string }; message?: string }
      notify.error(e?.data?.message || e?.message || 'Preferences update failed.')
    } finally {
      setSubmitting(false)
    }
  }

  const SidebarItem = ({ tab, label, icon: Icon }: { tab: Tab; label: string; icon: React.ElementType }) => (
    <button
      type="button"
      onClick={() => setActiveTab(tab)}
      className={`w-full flex items-center gap-2.5 px-3 py-2.5 rounded-lg text-sm font-medium transition-all text-left ${
        activeTab === tab
          ? 'bg-brand-600 text-white shadow-sm dark:bg-brand-600'
          : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-gray-200'
      }`}
    >
      <Icon size={16} />
      {label}
    </button>
  )

  const rightPanel = useMemo(() => {
    if (activeTab === 'profile') return (
      <form onSubmit={submitProfile} className="space-y-5">
        <div>
          <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">Profile Picture</h3>
          <div className="flex items-center gap-5">
            {/* Avatar with hover overlay */}
            <div className="relative group flex-shrink-0">
              {avatarUrl ? (
                <img src={avatarUrl} alt="avatar" className="w-20 h-20 rounded-full object-cover" />
              ) : (
                <div className="w-20 h-20 rounded-full bg-brand-100 dark:bg-brand-900/50 flex items-center justify-center text-2xl font-semibold text-brand-600 dark:text-brand-400">
                  {initials}
                </div>
              )}
              {/* Hover overlay */}
              <div className="absolute inset-0 rounded-full bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-1">
                <label className="p-1.5 rounded-full bg-white/20 hover:bg-white/30 cursor-pointer transition-colors" title="Change photo">
                  <Camera size={14} className="text-white" />
                  <input type="file" accept="image/*" className="hidden" onChange={(e) => {
                    const file = e.target.files?.[0]
                    if (file) handleAvatarFileSelect(file)
                    e.target.value = ''
                  }} />
                </label>
                {avatarUrl && (
                  <button type="button" onClick={() => { setAvatarUrl(''); notify.success('Avatar removed. Save to apply.') }}
                    className="p-1.5 rounded-full bg-white/20 hover:bg-red-500/60 transition-colors" title="Remove photo">
                    <Trash2 size={14} className="text-white" />
                  </button>
                )}
              </div>
              {uploadingAvatar && (
                <div className="absolute inset-0 rounded-full bg-black/40 flex items-center justify-center">
                  <Loader2 size={20} className="animate-spin text-white" />
                </div>
              )}
            </div>
            {/* Upload instructions */}
            <div className="space-y-2">
              <p className="text-sm text-gray-700 dark:text-gray-300 font-medium">
                {avatarUrl ? 'Hover to change or remove' : 'Upload a profile photo'}
              </p>
              <p className="text-xs text-gray-500 dark:text-gray-400">PNG, JPG or GIF. Max 5 MB.</p>
              {!avatarUrl && (
                <label className="btn-secondary text-xs cursor-pointer inline-flex items-center gap-1">
                  <Upload size={13} /> Choose file
                  <input type="file" accept="image/*" className="hidden" onChange={(e) => {
                    const file = e.target.files?.[0]
                    if (file) handleAvatarFileSelect(file)
                    e.target.value = ''
                  }} />
                </label>
              )}
            </div>
          </div>
        </div>

        <div>
          <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">Personal Info</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="label">First Name</label>
              <input className="input" value={firstName} onChange={(e) => setFirstName(e.target.value)} />
            </div>
            <div>
              <label className="label">Last Name</label>
              <input className="input" value={lastName} onChange={(e) => setLastName(e.target.value)} />
            </div>
            <div>
              <label className="label">Email</label>
              <input
                className="input disabled:opacity-60 disabled:cursor-not-allowed"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={isFederated(user?.provider)}
                required
              />
              {isFederated(user?.provider) && (
                <p className="mt-1 text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1">
                  <Lock size={11} /> Managed by {providerLabel(user?.provider)} — change it in your {providerLabel(user?.provider)} account.
                </p>
              )}
              {!isFederated(user?.provider) && user?.emailVerified === true && (
                <p className="mt-1 text-xs text-emerald-600 dark:text-emerald-400 flex items-center gap-1">
                  <ShieldCheck size={11} /> Verified
                </p>
              )}
              {!isFederated(user?.provider) && user?.emailVerified === false && (
                <p className="mt-1 text-xs text-amber-600 dark:text-amber-400 flex items-center gap-1">
                  <AlertCircle size={11} /> Not verified — check your inbox for a verification link.
                </p>
              )}
            </div>
            <div>
              <label className="label">Phone (optional)</label>
              <input className="input" type="tel" value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} />
            </div>
          </div>
        </div>

        <div className="flex gap-2 justify-end pt-2">
          <button type="button" className="btn-secondary text-sm" disabled={submitting} onClick={onClose}>Cancel</button>
          <button type="submit" className="btn-primary text-sm" disabled={submitting}>
            {submitting ? <><Loader2 size={14} className="animate-spin" />Saving…</> : 'Save Profile'}
          </button>
        </div>
      </form>
    )

    if (activeTab === 'account') {
      const federated = isFederated(user?.provider)

      const hasExistingPw = user?.hasPassword === true

      // ── Disconnect from provider flow (federated users only) ──────────
      const submitDisconnect = async (e: React.FormEvent) => {
        e.preventDefault()
        // If user already has a local password, just flip the provider — no new pw needed
        if (!hasExistingPw) {
          if (!newPassword || newPassword.length < 8) { notify.error('Password must be at least 8 characters.'); return }
          if (!/[A-Za-z]/.test(newPassword) || !/\d/.test(newPassword)) { notify.error('Must contain a letter and a number.'); return }
          if (newPassword !== confirmPassword) { notify.error('Passwords do not match.'); return }
        }
        setSubmitting(true)
        try {
          await disconnectProvider(hasExistingPw ? '' : newPassword)
          setNewPassword(''); setConfirmPassword('')
          await refreshMe()
          notify.success('Account disconnected. You are now a local user.')
        } catch (err) {
          const e = err as { data?: { message?: string }; message?: string }
          notify.error(e?.data?.message || e?.message || 'Disconnect failed.')
        } finally {
          setSubmitting(false)
        }
      }

      // ── Federated user: show provider info + disconnect option ────────
      if (federated) {
        const label = providerLabel(user?.provider)
        return (
          <div className="space-y-5">
            <div className="card p-5">
              <div className="flex items-start gap-3">
                <div className="w-10 h-10 rounded-full bg-emerald-100 dark:bg-emerald-900/40 flex items-center justify-center flex-shrink-0">
                  <ShieldCheck size={20} className="text-emerald-600 dark:text-emerald-400" />
                </div>
                <div className="flex-1 min-w-0">
                  <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Connected to {label}</h3>
                  <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">
                    Your account is managed by {label}. Password changes and email updates
                    are handled through your {label} account.
                  </p>
                  <ul className="mt-3 space-y-1.5 text-sm text-gray-600 dark:text-gray-300">
                    <li className="flex items-center gap-2"><Lock size={13} className="text-gray-400" /> Password managed by {label}</li>
                    <li className="flex items-center gap-2"><Lock size={13} className="text-gray-400" /> Email managed by {label}</li>
                  </ul>
                  {user?.provider === 'GOOGLE' && (
                    <a href="https://myaccount.google.com/security" target="_blank" rel="noreferrer"
                      className="mt-3 inline-flex items-center gap-1.5 text-sm font-medium text-brand-600 hover:text-brand-700 dark:text-brand-400 dark:hover:text-brand-300">
                      Open Google Account security &rarr;
                    </a>
                  )}
                  {user?.provider === 'APPLE' && (
                    <a href="https://appleid.apple.com/account/manage" target="_blank" rel="noreferrer"
                      className="mt-3 inline-flex items-center gap-1.5 text-sm font-medium text-brand-600 hover:text-brand-700 dark:text-brand-400 dark:hover:text-brand-300">
                      Open Apple ID settings &rarr;
                    </a>
                  )}
                </div>
              </div>
            </div>

            <div className="border-t border-gray-200 dark:border-gray-700 pt-5">
              <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-1">Disconnect from {label}</h3>
              <p className="text-xs text-gray-500 dark:text-gray-400 mb-4">
                {hasExistingPw
                  ? `Convert to a local account. You already have a local password, so you'll sign in with your username and password going forward. You won't be able to sign in with ${label} anymore.`
                  : `Convert to a local account. You'll need to set a password and will sign in with your username and password going forward. You won't be able to sign in with ${label} anymore.`}
              </p>
              <form onSubmit={submitDisconnect} className="space-y-3">
                {!hasExistingPw && (
                  <>
                    <div>
                      <label className="label">New local password</label>
                      <input type="password" className="input" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} required />
                      <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">At least 8 characters with one letter and one number.</p>
                    </div>
                    <div>
                      <label className="label">Confirm password</label>
                      <input type="password" className="input" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} required />
                    </div>
                  </>
                )}
                <div className="flex gap-2 justify-end pt-1">
                  <button type="button" className="btn-secondary text-sm" disabled={submitting} onClick={onClose}>Cancel</button>
                  <button type="submit" className="btn-danger text-sm" disabled={submitting}>
                    {submitting ? <><Loader2 size={14} className="animate-spin" />Disconnecting…</> : hasExistingPw ? 'Disconnect from ' + label : 'Disconnect and set password'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )
      }

      // ── Local user without a password (edge case) ─────────────────────
      if (!user?.hasPassword) return (
        <div className="card p-4 text-sm text-gray-600 dark:text-gray-300">
          No local password is set on this account.
        </div>
      )

      // ── Local user with password: normal change-password form ─────────
      return (
        <form onSubmit={submitPassword} className="space-y-4">
          <div>
            <label className="label">Current Password</label>
            <input type="password" className="input" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} required />
          </div>
          <div>
            <label className="label">New Password</label>
            <input type="password" className="input" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} required />
          </div>
          <div>
            <label className="label">Confirm New Password</label>
            <input type="password" className="input" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} required />
          </div>
          <div className="flex gap-2 justify-end pt-2">
            <button type="button" className="btn-secondary text-sm" disabled={submitting} onClick={onClose}>Cancel</button>
            <button type="submit" className="btn-primary text-sm" disabled={submitting}>
              {submitting ? <><Loader2 size={14} className="animate-spin" />Updating…</> : 'Update Password'}
            </button>
          </div>
        </form>
      )
    }

    if (activeTab === 'security') {
      // Federated accounts don't have a local password — 2FA protects the
      // local password-based signin flow, which federated users don't use.
      if (isFederated(user?.provider)) {
        const label = providerLabel(user?.provider)
        return (
          <div className="card p-5">
            <div className="flex items-start gap-3">
              <div className="w-10 h-10 rounded-full bg-emerald-100 dark:bg-emerald-900/40 flex items-center justify-center flex-shrink-0">
                <ShieldCheck size={20} className="text-emerald-600 dark:text-emerald-400" />
              </div>
              <div className="flex-1 min-w-0">
                <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Security managed by {label}</h3>
                <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">
                  Your account uses {label} sign-in, so two-factor authentication and password
                  security are handled by your {label} account. To enable 2FA, go to your {label} security settings.
                </p>
                {user?.provider === 'GOOGLE' && (
                  <a href="https://myaccount.google.com/security" target="_blank" rel="noreferrer"
                    className="mt-3 inline-flex items-center gap-1.5 text-sm font-medium text-brand-600 hover:text-brand-700 dark:text-brand-400 dark:hover:text-brand-300">
                    Open Google security settings &rarr;
                  </a>
                )}
                {user?.provider === 'APPLE' && (
                  <a href="https://appleid.apple.com/account/manage" target="_blank" rel="noreferrer"
                    className="mt-3 inline-flex items-center gap-1.5 text-sm font-medium text-brand-600 hover:text-brand-700 dark:text-brand-400 dark:hover:text-brand-300">
                    Open Apple ID settings &rarr;
                  </a>
                )}
              </div>
            </div>
          </div>
        )
      }

      const twoFaOn = Boolean(user?.twoFactorEnabled)

      const startSetup = async () => {
        setSubmitting(true)
        try {
          const s = await twoFactorSetup()
          setTwoFaSetup({ secret: s.secret, otpauthUri: s.otpauthUri })
          setTwoFaCode('')
          setRecoveryCodes(null)
        } catch (err) {
          const e = err as { message?: string }
          notify.error(e?.message || 'Failed to start 2FA setup.')
        } finally { setSubmitting(false) }
      }

      const confirmEnable = async (e: React.FormEvent) => {
        e.preventDefault()
        if (!twoFaCode || twoFaCode.length < 6) { notify.error('Enter the 6-digit code.'); return }
        setSubmitting(true)
        try {
          const r = await twoFactorEnable(twoFaCode)
          setRecoveryCodes(r.recoveryCodes || [])
          setTwoFaSetup(null)
          setTwoFaCode('')
          await refreshMe()
          notify.success('Two-factor authentication enabled.')
        } catch (err) {
          const e = err as { message?: string }
          notify.error(e?.message || 'Invalid code. Try again.')
        } finally { setSubmitting(false) }
      }

      const doDisable = async (e: React.FormEvent) => {
        e.preventDefault()
        if (!disableConfirm) { notify.error('Enter your password or a TOTP code to confirm.'); return }
        setSubmitting(true)
        try {
          await twoFactorDisable(disableConfirm)
          setDisableConfirm('')
          setRecoveryCodes(null)
          await refreshMe()
          notify.success('Two-factor authentication disabled.')
        } catch (err) {
          const e = err as { message?: string }
          notify.error(e?.message || 'Could not disable 2FA.')
        } finally { setSubmitting(false) }
      }

      // State A — 2FA OFF + no setup in progress → show "Enable" button
      if (!twoFaOn && !twoFaSetup && !recoveryCodes) {
        return (
          <div className="card p-5">
            <div className="flex items-start gap-3">
              <div className="w-10 h-10 rounded-full bg-brand-100 dark:bg-brand-900/40 flex items-center justify-center flex-shrink-0">
                <Smartphone size={20} className="text-brand-600 dark:text-brand-400" />
              </div>
              <div className="flex-1 min-w-0">
                <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Two-factor authentication</h3>
                <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">
                  Add an extra layer of security by requiring a 6-digit code from your
                  authenticator app (Google Authenticator, 1Password, Authy, etc.) on every sign-in.
                </p>
                <button type="button" className="btn-primary text-sm mt-4" onClick={startSetup} disabled={submitting}>
                  {submitting ? <><Loader2 size={14} className="animate-spin" /> Setting up…</> : 'Enable 2FA'}
                </button>
              </div>
            </div>
          </div>
        )
      }

      // State B — setup in progress → show QR code + verify input
      if (twoFaSetup) {
        const qrSrc = `https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${encodeURIComponent(twoFaSetup.otpauthUri)}`
        return (
          <form onSubmit={confirmEnable} className="space-y-4">
            <div className="card p-5 space-y-4">
              <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Scan this QR code</h3>
              <div className="flex flex-col items-center gap-3">
                <img src={qrSrc} alt="2FA QR code" className="w-44 h-44 bg-white p-2 rounded border border-gray-200 dark:border-gray-700" />
                <p className="text-xs text-gray-500 dark:text-gray-400 text-center">Or enter this secret manually:</p>
                <code className="text-xs font-mono px-2 py-1 bg-gray-100 dark:bg-gray-800 rounded select-all">
                  {twoFaSetup.secret}
                </code>
              </div>
            </div>
            <div>
              <label className="label">Enter the code from your app</label>
              <input
                type="text"
                inputMode="numeric"
                pattern="[0-9]{6}"
                maxLength={6}
                className="input tracking-widest text-center font-mono"
                placeholder="123456"
                value={twoFaCode}
                onChange={(e) => setTwoFaCode(e.target.value.replace(/\D/g, ''))}
                autoComplete="one-time-code"
                autoFocus
                required
              />
            </div>
            <div className="flex gap-2 justify-end">
              <button type="button" className="btn-secondary text-sm" onClick={() => { setTwoFaSetup(null); setTwoFaCode('') }} disabled={submitting}>
                Cancel
              </button>
              <button type="submit" className="btn-primary text-sm" disabled={submitting}>
                {submitting ? <><Loader2 size={14} className="animate-spin" /> Verifying…</> : 'Verify and enable'}
              </button>
            </div>
          </form>
        )
      }

      // State C — just enabled → show recovery codes (once)
      if (recoveryCodes) {
        return (
          <div className="card p-5 space-y-4">
            <div className="flex items-start gap-3">
              <div className="w-10 h-10 rounded-full bg-amber-100 dark:bg-amber-900/40 flex items-center justify-center flex-shrink-0">
                <AlertCircle size={20} className="text-amber-600 dark:text-amber-400" />
              </div>
              <div className="flex-1 min-w-0">
                <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Save your recovery codes</h3>
                <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">
                  These one-time codes can be used if you lose access to your authenticator.
                  Store them somewhere safe — they&apos;re shown only once.
                </p>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-2 text-sm font-mono">
              {recoveryCodes.map((c) => (
                <div key={c} className="px-2 py-1.5 bg-gray-100 dark:bg-gray-800 rounded text-center select-all">
                  {c}
                </div>
              ))}
            </div>
            <div className="flex gap-2 justify-end">
              <button
                type="button"
                className="btn-secondary text-sm"
                onClick={() => {
                  navigator.clipboard?.writeText(recoveryCodes.join('\n'))
                  setCopiedRecovery(true)
                  setTimeout(() => setCopiedRecovery(false), 2000)
                }}
              >
                {copiedRecovery ? <><Check size={14} /> Copied</> : <><Copy size={14} /> Copy all</>}
              </button>
              <button type="button" className="btn-primary text-sm" onClick={() => setRecoveryCodes(null)}>
                I&apos;ve saved these
              </button>
            </div>
          </div>
        )
      }

      // State D — 2FA already ON → show disable UI
      return (
        <form onSubmit={doDisable} className="space-y-4">
          <div className="card p-5">
            <div className="flex items-start gap-3">
              <div className="w-10 h-10 rounded-full bg-emerald-100 dark:bg-emerald-900/40 flex items-center justify-center flex-shrink-0">
                <ShieldCheck size={20} className="text-emerald-600 dark:text-emerald-400" />
              </div>
              <div className="flex-1 min-w-0">
                <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">
                  Two-factor authentication is enabled
                </h3>
                <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">
                  Signing in to this account requires a code from your authenticator app.
                </p>
              </div>
            </div>
          </div>
          <div>
            <label className="label">Enter your password or a 6-digit code to disable 2FA</label>
            <input
              type="password"
              className="input"
              value={disableConfirm}
              onChange={(e) => setDisableConfirm(e.target.value)}
              placeholder="Password or TOTP code"
              required
            />
          </div>
          <div className="flex gap-2 justify-end">
            <button type="button" className="btn-secondary text-sm" onClick={onClose} disabled={submitting}>Cancel</button>
            <button type="submit" className="btn-danger text-sm" disabled={submitting}>
              {submitting ? <><Loader2 size={14} className="animate-spin" /> Disabling…</> : 'Disable 2FA'}
            </button>
          </div>
        </form>
      )
    }

    // Preferences tab
    const themeOptions: { value: string; label: string; icon: React.ElementType }[] = [
      { value: 'system', label: 'System', icon: Monitor },
      { value: 'light', label: 'Light', icon: Sun },
      { value: 'dark', label: 'Dark', icon: Moon },
    ]
    return (
      <form onSubmit={submitPreferences} className="space-y-5">
        <div className="card p-4 space-y-3">
          <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300 flex items-center gap-2">
            <Sun size={15} /> Theme
          </h3>
          <div className="flex gap-2">
            {themeOptions.map(({ value, label, icon: Icon }) => (
              <button
                key={value}
                type="button"
                onClick={() => setPrefs({ ...prefs, theme: value })}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm border transition-colors ${
                  prefs.theme === value
                    ? 'border-brand-500 bg-brand-50 text-brand-700 dark:bg-brand-900/30 dark:text-brand-400 dark:border-brand-400'
                    : 'border-gray-300 dark:border-gray-600 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700'
                }`}
              >
                <Icon size={14} /> {label}
              </button>
            ))}
          </div>
        </div>

        <div className="card p-4 space-y-3">
          <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300 flex items-center gap-2">
            <Globe size={15} /> Language
          </h3>
          <select
            className="input w-auto"
            value={prefs.language}
            onChange={(e) => setPrefs({ ...prefs, language: e.target.value })}
          >
            <option value="en">English</option>
            <option value="es">Español</option>
            <option value="fr">Français</option>
          </select>
        </div>

        <div className="card p-4">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300 flex items-center gap-2">
                <Bell size={15} /> Email Notifications
              </h3>
              <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">Receive account and activity updates</p>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                className="sr-only peer"
                checked={Boolean(prefs.emailNotifications)}
                onChange={(e) => setPrefs({ ...prefs, emailNotifications: e.target.checked })}
              />
              <div className="w-10 h-5 bg-gray-300 dark:bg-gray-600 rounded-full peer peer-checked:bg-brand-600 transition-colors after:content-[''] after:absolute after:top-0.5 after:left-0.5 after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:after:translate-x-5" />
            </label>
          </div>
        </div>

        <div className="card p-4 space-y-3">
          <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300 flex items-center gap-2">
            <User size={15} /> Navbar display
          </h3>
          <p className="text-xs text-gray-500 dark:text-gray-400">What to show in the navigation bar user button</p>
          <div className="flex gap-2">
            {[
              { value: 'avatar', label: 'Avatar' },
              { value: 'initials', label: 'Initials' },
              { value: 'name', label: 'Name' },
            ].map(({ value, label }) => (
              <button
                key={value}
                type="button"
                onClick={() => setPrefs({ ...prefs, navbarDisplay: value })}
                className={`px-3 py-1.5 rounded-lg text-sm border transition-colors ${
                  prefs.navbarDisplay === value
                    ? 'border-brand-500 bg-brand-50 text-brand-700 dark:bg-brand-900/30 dark:text-brand-400 dark:border-brand-400'
                    : 'border-gray-300 dark:border-gray-600 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700'
                }`}
              >
                {label}
              </button>
            ))}
          </div>
        </div>

        <div className="flex gap-2 justify-end pt-2">
          <button type="button" className="btn-secondary text-sm" disabled={submitting} onClick={onClose}>Cancel</button>
          <button type="submit" className="btn-primary text-sm" disabled={submitting}>
            {submitting ? <><Loader2 size={14} className="animate-spin" />Saving…</> : 'Save Preferences'}
          </button>
        </div>
      </form>
    )
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab, firstName, lastName, email, phoneNumber, avatarUrl, uploadingAvatar, currentPassword, newPassword, confirmPassword, prefs, submitting, user, initials, twoFaSetup, twoFaCode, recoveryCodes, copiedRecovery, disableConfirm])

  if (!isOpen) return null

  return (
    <div
      className="fixed inset-0 z-50 flex items-start md:items-center justify-center p-0 md:p-4 overflow-auto"
      role="dialog"
      aria-modal="true"
      aria-labelledby="accountSettingsTitle"
      onKeyDown={onKeyDown}
    >
      <div className="absolute inset-0 bg-gray-950/95 backdrop-blur-md" onClick={() => { if (!submitting) onClose() }} />
      <div
        ref={panelRef}
        tabIndex={-1}
        className="relative card w-full max-w-3xl rounded-none md:rounded-xl shadow-xl"
      >
        {/* Header — enterprise style with avatar + provider badge */}
        <div className="flex items-center gap-4 p-5 border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/40 md:rounded-t-xl">
          {user && (
            <div className="w-11 h-11 rounded-full bg-brand-100 dark:bg-brand-900/50 flex items-center justify-center text-lg font-semibold text-brand-600 dark:text-brand-400 flex-shrink-0 overflow-hidden">
              {avatarUrl ? <img src={avatarUrl} alt="" className="w-full h-full object-cover" /> : initials}
            </div>
          )}
          <div className="flex-1 min-w-0">
            <h2 id="accountSettingsTitle" className="text-base font-semibold text-gray-900 dark:text-gray-100">
              User Settings
            </h2>
            {user && (
              <div className="flex items-center gap-2 mt-0.5">
                <p className="text-sm text-gray-500 dark:text-gray-400 truncate">
                  {user.email}
                </p>
                {isFederated(user.provider) && (
                  <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-medium bg-emerald-50 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400 flex-shrink-0">
                    <ShieldCheck size={10} /> {providerLabel(user.provider)}
                  </span>
                )}
              </div>
            )}
          </div>
          <button type="button" onClick={() => { if (!submitting) onClose() }} disabled={submitting}
            className="p-2 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-200 dark:hover:text-gray-200 dark:hover:bg-gray-700 transition-colors flex-shrink-0"
            aria-label="Close">
            <X size={18} />
          </button>
        </div>

        {/* Body */}
        <div className="flex flex-col md:flex-row">
          {/* Sidebar — enterprise nav with subtle hover */}
          <aside className="md:w-48 border-b md:border-b-0 md:border-r border-gray-200 dark:border-gray-700 p-3 space-y-1 bg-gray-50/50 dark:bg-gray-800/20">
            <SidebarItem tab="profile" label="Profile" icon={User} />
            <SidebarItem tab="account" label="Account" icon={KeyRound} />
            <SidebarItem tab="security" label="Security" icon={ShieldCheck} />
            <SidebarItem tab="preferences" label="Preferences" icon={Settings} />
          </aside>

          {/* Content */}
          <section className="flex-1 p-6 overflow-auto max-h-[70vh]">
            {rightPanel}
          </section>
        </div>
      </div>

      {/* Avatar crop/reposition modal */}
      {cropSrc && (
        <AvatarCropper
          imageSrc={cropSrc}
          onCancel={() => setCropSrc(null)}
          onSave={handleCroppedAvatar}
        />
      )}
    </div>
  )
}
