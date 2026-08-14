import { useState } from 'react'
import { ShieldCheck, Lock, Mail, Trash2, Loader2 } from 'lucide-react'
import { notify } from '../Toast'
import { useAuth } from '../../hooks/useAuth'
import { changePassword, disconnectProvider, requestEmailChange, requestAccountDeletion, cancelAccountDeletion } from '../../api/client'
import { isFederated, providerLabel } from './shared'

interface AccountTabProps {
  submitting: boolean
  setSubmitting: (v: boolean) => void
  onClose: () => void
}

export default function AccountTab({ submitting, setSubmitting, onClose }: AccountTabProps) {
  const { user, refreshMe } = useAuth()

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [newEmail, setNewEmail] = useState('')
  const [emailChangePassword, setEmailChangePassword] = useState('')
  const [deletePassword, setDeletePassword] = useState('')

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
      const e2 = err as { data?: { message?: string }; message?: string }
      notify.error(e2?.data?.message || e2?.message || 'Password update failed.')
    } finally {
      setSubmitting(false)
    }
  }

  const submitEmailChange = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newEmail || !emailChangePassword) { notify.error('New email and current password are required.'); return }
    setSubmitting(true)
    try {
      await requestEmailChange(newEmail, emailChangePassword)
      setNewEmail(''); setEmailChangePassword('')
      notify.success('Confirmation sent to your new address. The change takes effect once you confirm it.')
    } catch (err) {
      const e2 = err as { data?: { message?: string }; message?: string }
      notify.error(e2?.data?.message || e2?.message || 'Could not start email change.')
    } finally { setSubmitting(false) }
  }

  const submitAccountDeletion = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    try {
      await requestAccountDeletion(deletePassword || undefined)
      setDeletePassword('')
      await refreshMe()
      notify.success('Account deletion scheduled. You can cancel any time during the grace period.')
    } catch (err) {
      const e2 = err as { data?: { message?: string }; message?: string }
      notify.error(e2?.data?.message || e2?.message || 'Could not schedule deletion.')
    } finally { setSubmitting(false) }
  }

  const handleCancelDeletion = async () => {
    setSubmitting(true)
    try {
      await cancelAccountDeletion()
      await refreshMe()
      notify.success('Account deletion cancelled.')
    } catch (err) {
      const e2 = err as { message?: string }
      notify.error(e2?.message || 'Could not cancel deletion.')
    } finally { setSubmitting(false) }
  }

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
      const e2 = err as { data?: { message?: string }; message?: string }
      notify.error(e2?.data?.message || e2?.message || 'Disconnect failed.')
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
              <h3 className="text-sm font-semibold text-[rgb(var(--text-primary))]">Connected to {label}</h3>
              <p className="mt-1 text-sm text-[rgb(var(--text-secondary))]">
                Your account is managed by {label}. Password changes and email updates
                are handled through your {label} account.
              </p>
              <ul className="mt-3 space-y-1.5 text-sm text-[rgb(var(--text-secondary))]">
                <li className="flex items-center gap-2"><Lock size={13} className="text-[rgb(var(--text-muted))]" /> Password managed by {label}</li>
                <li className="flex items-center gap-2"><Lock size={13} className="text-[rgb(var(--text-muted))]" /> Email managed by {label}</li>
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

        <div className="border-t border-[rgb(var(--border-subtle))] pt-5">
          <h3 className="text-sm font-semibold text-[rgb(var(--text-primary))] mb-1">Disconnect from {label}</h3>
          <p className="text-xs text-[rgb(var(--text-muted))] mb-4">
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
                  <p className="mt-1 text-xs text-[rgb(var(--text-muted))]">At least 8 characters with one letter and one number.</p>
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
    <div className="card p-4 text-sm text-[rgb(var(--text-secondary))]">
      No local password is set on this account.
    </div>
  )

  // ── Local user with password: normal change-password form ─────────
  const pendingDeletion = Boolean(user?.pendingDeletion)
  return (
    <div className="space-y-6">
      <form onSubmit={submitPassword} className="space-y-4">
        <h3 className="text-sm font-semibold text-[rgb(var(--text-secondary))] flex items-center gap-2"><Lock size={15} /> Change password</h3>
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
          <button type="submit" className="btn-primary text-sm" disabled={submitting}>
            {submitting ? <><Loader2 size={14} className="animate-spin" />Updating…</> : 'Update Password'}
          </button>
        </div>
      </form>

      {/* Verified email change — never a silent swap via the profile form */}
      <form onSubmit={submitEmailChange} className="card p-4 space-y-3">
        <h3 className="text-sm font-semibold text-[rgb(var(--text-secondary))] flex items-center gap-2"><Mail size={15} /> Change email</h3>
        <p className="text-xs text-[rgb(var(--text-muted))]">
          Current: <span className="font-medium">{user?.email}</span>. We&apos;ll email a confirmation link to the
          new address and notify your current one. The change takes effect only after you confirm.
        </p>
        <div>
          <label className="label">New email</label>
          <input type="email" className="input" value={newEmail} onChange={(e) => setNewEmail(e.target.value)} placeholder="you@example.com" required />
        </div>
        <div>
          <label className="label">Current password</label>
          <input type="password" className="input" value={emailChangePassword} onChange={(e) => setEmailChangePassword(e.target.value)} required />
        </div>
        <div className="flex justify-end">
          <button type="submit" className="btn-secondary text-sm" disabled={submitting}>Send confirmation</button>
        </div>
      </form>

      {/* Danger zone — self-service account deletion with grace window */}
      <div className="card p-4 space-y-3 border-rose-200 dark:border-rose-900/50">
        <h3 className="text-sm font-semibold text-rose-700 dark:text-rose-400 flex items-center gap-2"><Trash2 size={15} /> Delete account</h3>
        {pendingDeletion ? (
          <>
            <p className="text-xs text-[rgb(var(--text-secondary))]">
              Your account is scheduled for deletion
              {user?.deletionScheduledAt ? ` on ${new Date(user.deletionScheduledAt).toLocaleDateString()}` : ''}.
              You can still cancel until then.
            </p>
            <div className="flex justify-end">
              <button type="button" className="btn-primary text-sm" disabled={submitting} onClick={handleCancelDeletion}>
                {submitting ? <><Loader2 size={14} className="animate-spin" />Cancelling…</> : 'Cancel deletion'}
              </button>
            </div>
          </>
        ) : (
          <form onSubmit={submitAccountDeletion} className="space-y-3">
            <p className="text-xs text-[rgb(var(--text-muted))]">
              Schedules permanent deletion after a grace period. You can cancel any time before then.
            </p>
            <div>
              <label className="label">Confirm with your password</label>
              <input type="password" className="input" value={deletePassword} onChange={(e) => setDeletePassword(e.target.value)} required />
            </div>
            <div className="flex justify-end">
              <button type="submit" className="btn-danger text-sm" disabled={submitting}>
                {submitting ? <><Loader2 size={14} className="animate-spin" />Scheduling…</> : 'Delete my account'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}
