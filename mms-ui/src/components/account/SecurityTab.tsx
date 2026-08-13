import { useState } from 'react'
import { ShieldCheck, Smartphone, Copy, Check, AlertCircle, Loader2, RefreshCw } from 'lucide-react'
import { notify } from '../Toast'
import { useAuth } from '../../hooks/useAuth'
import { twoFactorSetup, twoFactorEnable, twoFactorDisable, twoFactorRegenerateRecoveryCodes } from '../../api/client'
import { isFederated, providerLabel } from './shared'

interface SecurityTabProps {
  submitting: boolean
  setSubmitting: (v: boolean) => void
  onClose: () => void
}

export default function SecurityTab({ submitting, setSubmitting, onClose }: SecurityTabProps) {
  const { user, refreshMe } = useAuth()

  const [twoFaSetup, setTwoFaSetup] = useState<{ secret: string; otpauthUri: string } | null>(null)
  const [twoFaCode, setTwoFaCode] = useState('')
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null)
  const [copiedRecovery, setCopiedRecovery] = useState(false)
  const [disableConfirm, setDisableConfirm] = useState('')

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

  const regenerateCodes = async () => {
    setSubmitting(true)
    try {
      const r = await twoFactorRegenerateRecoveryCodes()
      setRecoveryCodes(r.recoveryCodes || [])
      notify.success('New recovery codes generated. Save them now.')
    } catch (err) {
      const e = err as { message?: string }
      notify.error(e?.message || 'Could not regenerate recovery codes.')
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
            <button type="button" className="btn-secondary text-sm mt-3 inline-flex items-center gap-1.5"
              onClick={regenerateCodes} disabled={submitting}>
              <RefreshCw size={14} /> Generate new recovery codes
            </button>
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
