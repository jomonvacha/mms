import { useState } from 'react'
import { Link } from 'react-router-dom'
import { KeyRound, Loader2, Mail, CheckCircle2, ArrowLeft } from 'lucide-react'
import { forgotPassword } from '../api/client'

export default function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [loading, setLoading] = useState(false)
  const [submitted, setSubmitted] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setError('Please enter a valid email address.')
      return
    }
    setLoading(true)
    try {
      await forgotPassword(email)
      setSubmitted(true)
    } catch (err) {
      const e = err as { message?: string }
      // Backend returns 200 for unknown emails too, so any error here is unexpected
      setError(e?.message || 'Something went wrong. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  if (submitted) {
    return (
      <div className="flex items-center justify-center py-8">
        <div className="w-full max-w-md">
          <div className="card p-8 text-center space-y-5">
            <div className="w-14 h-14 mx-auto rounded-full bg-emerald-100 dark:bg-emerald-900/40 flex items-center justify-center">
              <CheckCircle2 size={28} className="text-emerald-600 dark:text-emerald-400" />
            </div>
            <div>
              <h1 className="text-2xl font-bold text-[rgb(var(--text-primary))]">Check your email</h1>
              <p className="mt-2 text-sm text-[rgb(var(--text-secondary))]">
                If an account exists for <strong>{email}</strong>, we&apos;ve sent a password
                reset link. The link is valid for 30 minutes.
              </p>
              <p className="mt-3 text-xs text-[rgb(var(--text-muted))]">
                Didn&apos;t get anything? Check your spam folder or try again.
              </p>
            </div>
            <Link to="/signin" className="btn-secondary text-sm inline-flex items-center gap-1.5">
              <ArrowLeft size={14} /> Back to sign in
            </Link>
          </div>
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
              <KeyRound size={24} className="text-brand-600 dark:text-brand-400" />
            </div>
            <h1 className="text-2xl font-bold text-[rgb(var(--text-primary))]">Forgot password?</h1>
            <p className="mt-1 text-sm text-[rgb(var(--text-muted))]">
              Enter your email and we&apos;ll send you a reset link.
            </p>
          </div>

          {error && (
            <div className="card p-3 border-rose-200 bg-rose-50 dark:border-rose-900/50 dark:bg-rose-900/20 text-sm text-rose-700 dark:text-rose-300">
              {error}
            </div>
          )}

          <form onSubmit={onSubmit} className="space-y-4">
            <div>
              <label className="label">Email</label>
              <div className="relative">
                <Mail size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
                <input
                  type="email"
                  className="input pl-9"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@example.com"
                  required
                  autoFocus
                />
              </div>
            </div>
            <button type="submit" className="btn-primary w-full text-sm" disabled={loading}>
              {loading ? <><Loader2 size={14} className="animate-spin" /> Sending…</> : 'Send reset link'}
            </button>
          </form>

          <div className="text-center text-sm text-[rgb(var(--text-muted))]">
            Remembered it?{' '}
            <Link to="/signin" className="font-medium text-brand-600 hover:text-brand-700 dark:text-brand-400">
              Sign in
            </Link>
          </div>
        </div>
      </div>
    </div>
  )
}
