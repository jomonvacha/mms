import { useEffect, useState } from 'react'
import { Upload, Loader2, ShieldCheck, Lock, AlertCircle, Camera, Trash2 } from 'lucide-react'
import { notify } from '../Toast'
import { useAuth } from '../../hooks/useAuth'
import { updateMe } from '../../api/client'
import { isFederated, providerLabel } from './shared'

interface ProfileTabProps {
  avatarUrl: string
  uploadingAvatar: boolean
  initials: string
  submitting: boolean
  setSubmitting: (v: boolean) => void
  onFileSelected: (file: File) => void
  onRemoveAvatar: () => void
  onClose: () => void
}

export default function ProfileTab({
  avatarUrl, uploadingAvatar, initials, submitting, setSubmitting, onFileSelected, onRemoveAvatar, onClose,
}: ProfileTabProps) {
  const { user, refreshMe } = useAuth()

  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [phoneNumber, setPhoneNumber] = useState('')

  useEffect(() => {
    if (!user) return
    setFirstName(user.firstName || '')
    setLastName(user.lastName || '')
    setEmail(user.email || '')
    setPhoneNumber(user.phoneNumber || '')
  }, [user])

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
      const e2 = err as { data?: { message?: string }; message?: string }
      notify.error(e2?.data?.message || e2?.message || 'Profile update failed.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={submitProfile} className="space-y-5">
      <div>
        <h3 className="text-sm font-semibold text-[rgb(var(--text-secondary))] mb-3">Profile Picture</h3>
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
                  if (file) onFileSelected(file)
                  e.target.value = ''
                }} />
              </label>
              {avatarUrl && (
                <button type="button" onClick={onRemoveAvatar}
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
            <p className="text-sm text-[rgb(var(--text-secondary))] font-medium">
              {avatarUrl ? 'Hover to change or remove' : 'Upload a profile photo'}
            </p>
            <p className="text-xs text-[rgb(var(--text-muted))]">PNG, JPG or GIF. Max 5 MB.</p>
            {!avatarUrl && (
              <label className="btn-secondary text-xs cursor-pointer inline-flex items-center gap-1">
                <Upload size={13} /> Choose file
                <input type="file" accept="image/*" className="hidden" onChange={(e) => {
                  const file = e.target.files?.[0]
                  if (file) onFileSelected(file)
                  e.target.value = ''
                }} />
              </label>
            )}
          </div>
        </div>
      </div>

      <div>
        <h3 className="text-sm font-semibold text-[rgb(var(--text-secondary))] mb-3">Personal Info</h3>
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
              <p className="mt-1 text-xs text-[rgb(var(--text-muted))] flex items-center gap-1">
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
}
