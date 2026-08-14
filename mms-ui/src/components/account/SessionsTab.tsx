import { useCallback, useEffect, useState } from 'react'
import { Loader2, Laptop, LogOut } from 'lucide-react'
import { notify } from '../Toast'
import { listSessions, revokeSession, revokeOtherSessions, type SessionRecord } from '../../api/client'

interface SessionsTabProps {
  submitting: boolean
  setSubmitting: (v: boolean) => void
}

export default function SessionsTab({ submitting, setSubmitting }: SessionsTabProps) {
  const [sessions, setSessions] = useState<SessionRecord[]>([])
  const [sessionsLoading, setSessionsLoading] = useState(false)

  const loadSessions = useCallback(async () => {
    setSessionsLoading(true)
    try {
      setSessions(await listSessions())
    } catch (err) {
      const e = err as { message?: string }
      notify.error(e?.message || 'Could not load sessions.')
    } finally { setSessionsLoading(false) }
  }, [])

  useEffect(() => { loadSessions() }, [loadSessions])

  const revokeOne = async (id: string) => {
    setSubmitting(true)
    try { await revokeSession(id); await loadSessions(); notify.success('Session signed out.') }
    catch (err) { notify.error((err as { message?: string })?.message || 'Could not sign out session.') }
    finally { setSubmitting(false) }
  }
  const revokeOthers = async () => {
    setSubmitting(true)
    try { await revokeOtherSessions(); await loadSessions(); notify.success('Signed out other sessions.') }
    catch (err) { notify.error((err as { message?: string })?.message || 'Could not sign out other sessions.') }
    finally { setSubmitting(false) }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-[rgb(var(--text-secondary))] flex items-center gap-2"><Laptop size={15} /> Active sessions</h3>
          <p className="text-xs text-[rgb(var(--text-muted))] mt-0.5">Devices currently signed in to your account.</p>
        </div>
        <button type="button" className="btn-secondary text-sm inline-flex items-center gap-1.5"
          onClick={revokeOthers} disabled={submitting || sessions.length <= 1}>
          <LogOut size={16} /> Sign out others
        </button>
      </div>
      {sessionsLoading ? (
        <div className="flex justify-center py-8"><Loader2 size={20} className="animate-spin text-brand-500" /></div>
      ) : sessions.length === 0 ? (
        <p className="text-sm text-[rgb(var(--text-muted))] py-6 text-center">No active sessions.</p>
      ) : (
        <ul className="space-y-2">
          {sessions.map((s) => (
            <li key={s.id} className="card p-3 flex items-center justify-between gap-3">
              <div className="min-w-0">
                <p className="text-sm font-medium text-[rgb(var(--text-primary))] flex items-center gap-2">
                  {s.deviceLabel || 'Unknown device'}
                  {s.current && <span className="px-1.5 py-0.5 rounded-full text-[10px] font-medium bg-emerald-50 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400">This device</span>}
                </p>
                <p className="text-xs text-[rgb(var(--text-muted))] truncate">
                  {s.ip || 'unknown IP'}{s.lastActiveAt ? ` · last active ${new Date(s.lastActiveAt).toLocaleString()}` : ''}
                </p>
              </div>
              {!s.current && (
                <button type="button" className="btn-secondary h-9 px-3 text-xs flex-shrink-0" disabled={submitting} onClick={() => revokeOne(s.id)}>
                  Sign out
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
