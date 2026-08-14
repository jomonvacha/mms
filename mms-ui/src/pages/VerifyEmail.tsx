import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Loader2, CheckCircle2, XCircle } from 'lucide-react'
import { verifyEmail } from '../api/client'

type Status = 'loading' | 'ok' | 'error'

export default function VerifyEmail() {
  const [params] = useSearchParams()
  const token = params.get('token') || ''
  const [status, setStatus] = useState<Status>('loading')
  const [message, setMessage] = useState('')

  useEffect(() => {
    if (!token) {
      setStatus('error')
      setMessage('Missing verification token.')
      return
    }
    let cancelled = false
    verifyEmail(token)
      .then(() => {
        if (cancelled) return
        setStatus('ok')
        setMessage('Your email has been verified.')
      })
      .catch((err: { message?: string }) => {
        if (cancelled) return
        setStatus('error')
        setMessage(err?.message || 'Verification link is invalid or has expired.')
      })
    return () => { cancelled = true }
  }, [token])

  return (
    <div className="flex items-center justify-center py-12">
      <div className="card p-10 max-w-md w-full text-center space-y-5">
        {status === 'loading' && (
          <>
            <Loader2 size={32} className="animate-spin text-brand-600 dark:text-brand-400 mx-auto" />
            <p className="text-sm text-[rgb(var(--text-secondary))]">Verifying your email…</p>
          </>
        )}
        {status === 'ok' && (
          <>
            <div className="w-14 h-14 mx-auto rounded-full bg-emerald-100 dark:bg-emerald-900/40 flex items-center justify-center">
              <CheckCircle2 size={28} className="text-emerald-600 dark:text-emerald-400" />
            </div>
            <h1 className="text-xl font-semibold text-[rgb(var(--text-primary))]">Email verified</h1>
            <p className="text-sm text-[rgb(var(--text-secondary))]">{message}</p>
            <Link to="/" className="btn-primary text-sm inline-block">Go home</Link>
          </>
        )}
        {status === 'error' && (
          <>
            <div className="w-14 h-14 mx-auto rounded-full bg-rose-100 dark:bg-rose-900/40 flex items-center justify-center">
              <XCircle size={28} className="text-rose-600 dark:text-rose-400" />
            </div>
            <h1 className="text-xl font-semibold text-[rgb(var(--text-primary))]">Verification failed</h1>
            <p className="text-sm text-[rgb(var(--text-secondary))]">{message}</p>
            <Link to="/signin" className="btn-secondary text-sm inline-block">Back to sign in</Link>
          </>
        )}
      </div>
    </div>
  )
}
