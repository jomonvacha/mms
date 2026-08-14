import { useState } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { Lock, Loader2, CheckCircle2, AlertCircle } from 'lucide-react'
import { notify } from '../components/Toast'
import { resetPassword } from '../api/client'

export default function ResetPassword() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') || ''
  const navigate = useNavigate()
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  if (!token) {
    // No token param — redirect to forgot-password
    return <Navigate to="/forgot-password" replace />
  }

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!newPassword || newPassword.length < 8) {
      setError('Password must be at least 8 characters.')
      return
    }
    if (!/[A-Za-z]/.test(newPassword) || !/\d/.test(newPassword)) {
      setError('Password must contain at least one letter and one number.')
      return
    }
    if (newPassword !== confirmPassword) {
      setError('Passwords do not match.')
      return
    }
    setLoading(true)
    try {
      await resetPassword(token, newPassword)
      setDone(true)
      notify.success('Password reset. You can sign in now.')
      setTimeout(() => navigate('/signin'), 1500)
    } catch (err) {
      const e = err as { message?: string }
      setError(e?.message || 'Could not reset your password. The link may have expired.')
    } finally {
      setLoading(false)
    }
  }

  if (done) {
    return (
      <div className="flex items-center justify-center py-8">
        <div className="card p-8 max-w-md w-full text-center space-y-4">
          <div className="w-14 h-14 mx-auto rounded-full bg-emerald-100 dark:bg-emerald-900/40 flex items-center justify-center">
            <CheckCircle2 size={28} className="text-emerald-600 dark:text-emerald-400" />
          </div>
          <h1 className="text-xl font-semibold text-[rgb(var(--text-primary))]">Password reset</h1>
          <p className="text-sm text-[rgb(var(--text-secondary))]">Redirecting you to sign in…</p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex items-center justify-center py-8">
      <div className="w-full max-w-md">
        <div className="card p-8 space-y-6">
          <div className="text-center">
            <div className="w-12 h-12 rounded-full bg-brand-100 dark:bg-brand-900/50 flex items-center justify-center mx-auto mb-3">
              <Lock size={24} className="text-brand-600 dark:text-brand-400" />
            </div>
            <h1 className="text-2xl font-bold text-[rgb(var(--text-primary))]">Choose a new password</h1>
            <p className="mt-1 text-sm text-[rgb(var(--text-muted))]">
              At least 8 characters with one letter and one number.
            </p>
          </div>

          {error && (
            <div className="card p-3 border-rose-200 bg-rose-50 dark:border-rose-900/50 dark:bg-rose-900/20 text-sm text-rose-700 dark:text-rose-300 flex items-start gap-2">
              <AlertCircle size={15} className="flex-shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          <form onSubmit={onSubmit} className="space-y-4">
            <div>
              <label className="label">New password</label>
              <input
                type="password"
                className="input"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                required
                autoFocus
              />
            </div>
            <div>
              <label className="label">Confirm new password</label>
              <input
                type="password"
                className="input"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
              />
            </div>
            <button type="submit" className="btn-primary w-full text-sm" disabled={loading}>
              {loading ? <><Loader2 size={14} className="animate-spin" /> Resetting…</> : 'Reset password'}
            </button>
          </form>

          <div className="text-center text-sm text-[rgb(var(--text-muted))]">
            <Link to="/signin" className="font-medium text-brand-600 hover:text-brand-700 dark:text-brand-400">
              Back to sign in
            </Link>
          </div>
        </div>
      </div>
    </div>
  )
}
