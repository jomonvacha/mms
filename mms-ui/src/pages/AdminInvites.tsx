import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Ticket, Plus, Loader2, Trash2, X, Copy, Check, ShieldAlert, ToggleLeft, ToggleRight,
  Infinity as InfinityIcon, AlertTriangle, Pencil,
} from 'lucide-react'
import { notify } from '../components/Toast'
import {
  listSignupInvites, createSignupInvite, updateSignupInvite, deleteSignupInvite,
  type SignupInviteCode,
} from '../api/client'

function StatusBadge({ invite }: { invite: SignupInviteCode }) {
  const exhausted = invite.maxUses != null && invite.usedCount >= invite.maxUses
  const expired = Boolean(invite.expiresAt) && new Date(invite.expiresAt!).getTime() < Date.now()
  if (!invite.active) return <span className="chip chip--neutral">Disabled</span>
  if (expired) return <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium bg-red-50 text-red-600 dark:bg-red-900/30 dark:text-red-400">Expired</span>
  if (exhausted) return <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium bg-amber-50 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400">Exhausted</span>
  return <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium bg-emerald-50 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400">Active</span>
}

function formatExpires(value?: string | null) {
  if (!value) return 'Never'
  try {
    const d = new Date(value)
    return d.toLocaleString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
  } catch { return '—' }
}

export default function AdminInvites() {
  const qc = useQueryClient()
  const { data: invites = [], isLoading, isError, error } = useQuery({
    queryKey: ['signup-invites'],
    queryFn: listSignupInvites,
  })

  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<SignupInviteCode | null>(null)
  const [copiedId, setCopiedId] = useState<string | null>(null)
  const [deleting, setDeleting] = useState<SignupInviteCode | null>(null)

  const toggle = useMutation({
    mutationFn: (inv: SignupInviteCode) => updateSignupInvite(inv.id, { active: !inv.active }),
    onSuccess: (_d, inv) => {
      notify.success(`${inv.code} ${inv.active ? 'disabled' : 'enabled'}.`)
      qc.invalidateQueries({ queryKey: ['signup-invites'] })
    },
    onError: (err) => notify.error((err as { message?: string })?.message || 'Failed.'),
  })

  const removeInvite = useMutation({
    mutationFn: (id: string) => deleteSignupInvite(id),
    onSuccess: () => {
      notify.success('Invite deleted.')
      qc.invalidateQueries({ queryKey: ['signup-invites'] })
      setDeleting(null)
    },
    onError: (err) => notify.error((err as { message?: string })?.message || 'Failed.'),
  })

  const onCopy = (code: string, id: string) => {
    navigator.clipboard?.writeText(code)
    setCopiedId(id)
    setTimeout(() => setCopiedId(null), 2000)
    notify.success('Code copied.')
  }

  const sorted = useMemo(() => [...invites].sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || '')), [invites])

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div className="space-y-1">
          <h1 className="text-2xl font-bold text-[rgb(var(--text-primary))] flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-brand-50 dark:bg-brand-900/30">
              <Ticket size={20} className="text-brand-600 dark:text-brand-400" />
            </div>
            Signup Invites
          </h1>
          <p className="text-sm text-[rgb(var(--text-muted))]">
            Issue invite codes required for new IDFY signups. Codes carry a usage cap and optional expiry.
          </p>
        </div>
        <button type="button" onClick={() => setCreating(true)}
          className="btn-primary text-sm">
          <Plus size={15} /> New invite
        </button>
      </div>

      {isLoading && (
        <div className="card p-16 flex flex-col items-center justify-center gap-3">
          <Loader2 size={28} className="animate-spin text-brand-600" />
          <p className="text-sm text-gray-500">Loading invites...</p>
        </div>
      )}

      {isError && (
        <div className="card p-5 border border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/10">
          <div className="flex items-start gap-3">
            <ShieldAlert size={18} className="text-red-500 mt-0.5 flex-shrink-0" />
            <div>
              <p className="text-sm font-medium text-red-800 dark:text-red-300">Failed to load invites</p>
              <p className="text-xs mt-0.5 text-red-600/80 dark:text-red-400/80">{(error as Error)?.message}</p>
            </div>
          </div>
        </div>
      )}

      {!isLoading && !isError && (
        <div className="card overflow-hidden border border-[rgb(var(--border-subtle))]">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[rgb(var(--surface-2))] border-b border-[rgb(var(--border-subtle))]">
                  <th scope="col" className="px-4 py-3 text-left text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider">Code</th>
                  <th scope="col" className="px-4 py-3 text-left text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider">Description</th>
                  <th scope="col" className="px-4 py-3 text-left text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider">Uses</th>
                  <th scope="col" className="px-4 py-3 text-left text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider">Expires</th>
                  <th scope="col" className="px-4 py-3 text-left text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider">Status</th>
                  <th scope="col" className="px-4 py-3 text-right text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[rgb(var(--border-subtle))]/60">
                {sorted.length === 0 && (
                  <tr><td colSpan={6} className="px-4 py-16 text-center">
                    <div className="inline-flex p-3 rounded-full bg-[rgb(var(--surface-3))] mb-3">
                      <Ticket size={22} className="text-gray-400" />
                    </div>
                    <p className="text-sm text-[rgb(var(--text-muted))]">No invite codes yet. Create one to allow signups.</p>
                  </td></tr>
                )}
                {sorted.map((inv) => {
                  const usesLabel = inv.maxUses == null
                    ? <span className="inline-flex items-center gap-1"><InfinityIcon size={12} /> {inv.usedCount}</span>
                    : `${inv.usedCount} / ${inv.maxUses}`
                  return (
                    <tr key={inv.id} className="hover:bg-[rgb(var(--surface-2)/0.8)] transition-colors">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-[13px] font-semibold text-[rgb(var(--text-primary))]">{inv.code}</span>
                          <button type="button" onClick={() => onCopy(inv.code, inv.id)} title="Copy code"
                            className="p-1 rounded text-gray-400 hover:text-brand-600 hover:bg-brand-50 dark:hover:bg-brand-900/20 transition-colors">
                            {copiedId === inv.id ? <Check size={13} className="text-emerald-600" /> : <Copy size={13} />}
                          </button>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-[13px] text-[rgb(var(--text-secondary))]">{inv.description || <span className="text-gray-400">—</span>}</td>
                      <td className="px-4 py-3 text-[13px] text-[rgb(var(--text-secondary))]">{usesLabel}</td>
                      <td className="px-4 py-3 text-[13px] text-[rgb(var(--text-secondary))]">{formatExpires(inv.expiresAt)}</td>
                      <td className="px-4 py-3"><StatusBadge invite={inv} /></td>
                      <td className="px-4 py-3 text-right">
                        <div className="inline-flex items-center gap-0.5">
                          <button type="button" onClick={() => setEditing(inv)} title="Edit"
                            className="p-1.5 rounded-md text-[rgb(var(--text-muted))] hover:text-brand-600 hover:bg-brand-50 dark:hover:text-brand-400 dark:hover:bg-brand-900/30 transition-colors">
                            <Pencil size={14} />
                          </button>
                          <button type="button" onClick={() => toggle.mutate(inv)}
                            disabled={toggle.isPending}
                            title={inv.active ? 'Disable' : 'Enable'}
                            className="p-1.5 rounded-md text-[rgb(var(--text-muted))] hover:text-amber-600 hover:bg-amber-50 dark:hover:text-amber-400 dark:hover:bg-amber-900/30 disabled:opacity-30 transition-colors">
                            {inv.active ? <ToggleRight size={14} /> : <ToggleLeft size={14} />}
                          </button>
                          <button type="button" onClick={() => setDeleting(inv)} title="Delete"
                            className="p-1.5 rounded-md text-[rgb(var(--text-muted))] hover:text-red-600 hover:bg-red-50 dark:hover:text-red-400 dark:hover:bg-red-900/20 transition-colors">
                            <Trash2 size={14} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {creating && <CreateInviteModal onClose={() => setCreating(false)} onSaved={() => {
        setCreating(false); qc.invalidateQueries({ queryKey: ['signup-invites'] })
      }} />}
      {editing && <EditInviteModal invite={editing} onClose={() => setEditing(null)} onSaved={() => {
        setEditing(null); qc.invalidateQueries({ queryKey: ['signup-invites'] })
      }} />}
      {deleting && <DeleteConfirmModal invite={deleting} saving={removeInvite.isPending}
        onCancel={() => setDeleting(null)}
        onConfirm={() => removeInvite.mutate(deleting.id)} />}
    </div>
  )
}

// ── Modals ────────────────────────────────────────────────────────────

function CreateInviteModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [code, setCode] = useState('')
  const [description, setDescription] = useState('')
  const [limitUses, setLimitUses] = useState(true)
  const [maxUses, setMaxUses] = useState<string>('25')
  const [expiresAt, setExpiresAt] = useState('')
  const [saving, setSaving] = useState(false)

  const onSave = async () => {
    const trimmed = code.trim().toUpperCase()
    if (!trimmed) { notify.error('Code is required.'); return }
    let parsedMax: number | null = null
    if (limitUses) {
      parsedMax = parseInt(maxUses, 10)
      if (!Number.isFinite(parsedMax) || parsedMax < 1) { notify.error('Max uses must be at least 1.'); return }
    }
    setSaving(true)
    try {
      await createSignupInvite({
        code: trimmed,
        description: description.trim() || undefined,
        maxUses: parsedMax,
        expiresAt: expiresAt ? new Date(expiresAt).toISOString() : null,
      })
      notify.success('Invite created.')
      onSaved()
    } catch (err) { notify.error((err as { message?: string })?.message || 'Failed.') }
    finally { setSaving(false) }
  }

  return (
    <ModalShell title="New invite code" onClose={onClose} saving={saving}>
      <div className="space-y-4">
        <div>
          <label className="block text-[11px] font-medium text-[rgb(var(--text-muted))] mb-1.5">Code</label>
          <input type="text" value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase().replace(/[^A-Z0-9\-_]/g, ''))}
            placeholder="IDFY-2026-Q2" className="input text-sm font-mono" maxLength={64} />
          <p className="mt-1 text-[11px] text-[rgb(var(--text-muted))]">Uppercase letters, digits, dashes.</p>
        </div>
        <div>
          <label className="block text-[11px] font-medium text-[rgb(var(--text-muted))] mb-1.5">Description (optional)</label>
          <input type="text" value={description} onChange={(e) => setDescription(e.target.value)}
            placeholder="Q2 partner launch" className="input text-sm" maxLength={200} />
        </div>
        <div>
          <label className="flex items-center gap-2 text-xs text-[rgb(var(--text-secondary))]">
            <input type="checkbox" checked={limitUses} onChange={(e) => setLimitUses(e.target.checked)} className="h-3.5 w-3.5" />
            Limit number of signups
          </label>
          {limitUses && (
            <input type="number" min={1} value={maxUses} onChange={(e) => setMaxUses(e.target.value)}
              className="input text-sm mt-2 w-32" />
          )}
        </div>
        <div>
          <label className="block text-[11px] font-medium text-[rgb(var(--text-muted))] mb-1.5">Expires (optional)</label>
          <input type="datetime-local" value={expiresAt} onChange={(e) => setExpiresAt(e.target.value)}
            className="input text-sm" />
        </div>
      </div>

      <ModalFooter onCancel={onClose} onConfirm={onSave} saving={saving} confirmLabel="Create invite" />
    </ModalShell>
  )
}

function EditInviteModal({ invite, onClose, onSaved }: {
  invite: SignupInviteCode; onClose: () => void; onSaved: () => void
}) {
  const [description, setDescription] = useState(invite.description || '')
  const [limitUses, setLimitUses] = useState(invite.maxUses != null)
  const [maxUses, setMaxUses] = useState<string>(invite.maxUses?.toString() || '25')
  const [expiresAt, setExpiresAt] = useState<string>(
    invite.expiresAt ? new Date(invite.expiresAt).toISOString().slice(0, 16) : '',
  )
  const [saving, setSaving] = useState(false)

  const onSave = async () => {
    let parsedMax: number | null = null
    if (limitUses) {
      parsedMax = parseInt(maxUses, 10)
      if (!Number.isFinite(parsedMax) || parsedMax < 1) { notify.error('Max uses must be at least 1.'); return }
      if (parsedMax < invite.usedCount) { notify.error(`Max uses cannot be below current ${invite.usedCount} uses.`); return }
    }
    setSaving(true)
    try {
      await updateSignupInvite(invite.id, {
        description: description.trim(),
        maxUses: parsedMax,
        expiresAt: expiresAt ? new Date(expiresAt).toISOString() : null,
      })
      notify.success('Invite updated.')
      onSaved()
    } catch (err) { notify.error((err as { message?: string })?.message || 'Failed.') }
    finally { setSaving(false) }
  }

  return (
    <ModalShell title={`Edit ${invite.code}`} onClose={onClose} saving={saving}>
      <div className="space-y-4">
        <div>
          <label className="block text-[11px] font-medium text-[rgb(var(--text-muted))] mb-1.5">Description</label>
          <input type="text" value={description} onChange={(e) => setDescription(e.target.value)}
            className="input text-sm" maxLength={200} />
        </div>
        <div>
          <label className="flex items-center gap-2 text-xs text-[rgb(var(--text-secondary))]">
            <input type="checkbox" checked={limitUses} onChange={(e) => setLimitUses(e.target.checked)} className="h-3.5 w-3.5" />
            Limit number of signups (currently used {invite.usedCount} times)
          </label>
          {limitUses && (
            <input type="number" min={Math.max(1, invite.usedCount)} value={maxUses}
              onChange={(e) => setMaxUses(e.target.value)}
              className="input text-sm mt-2 w-32" />
          )}
        </div>
        <div>
          <label className="block text-[11px] font-medium text-[rgb(var(--text-muted))] mb-1.5">Expires</label>
          <input type="datetime-local" value={expiresAt} onChange={(e) => setExpiresAt(e.target.value)}
            className="input text-sm" />
        </div>
      </div>

      <ModalFooter onCancel={onClose} onConfirm={onSave} saving={saving} confirmLabel="Save changes" />
    </ModalShell>
  )
}

function DeleteConfirmModal({ invite, saving, onCancel, onConfirm }: {
  invite: SignupInviteCode; saving: boolean; onCancel: () => void; onConfirm: () => void
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true">
      <div className="absolute inset-0 bg-gray-950/95 backdrop-blur-md" onClick={() => !saving && onCancel()} />
      <div className="relative bg-[rgb(var(--surface-1))] rounded-2xl w-full max-w-md shadow-2xl border border-[rgb(var(--border-subtle))]">
        <div className="flex items-center gap-3 px-6 py-4 border-b border-[rgb(var(--border-subtle))]">
          <div className="p-2 rounded-lg bg-red-50 dark:bg-red-900/30">
            <AlertTriangle size={16} className="text-red-600 dark:text-red-400" />
          </div>
          <div>
            <h2 className="text-base font-semibold text-[rgb(var(--text-primary))]">Delete {invite.code}</h2>
            <p className="text-xs text-[rgb(var(--text-muted))]">This action cannot be undone.</p>
          </div>
        </div>
        <div className="px-6 py-4">
          <p className="text-sm text-[rgb(var(--text-secondary))]">
            The invite code will be permanently removed. Existing signups that used this code are unaffected.
          </p>
        </div>
        <div className="flex items-center justify-end gap-2 px-6 py-4 border-t border-[rgb(var(--border-subtle))] bg-[rgb(var(--surface-2))] rounded-b-xl">
          <button type="button" disabled={saving} onClick={onCancel}
            className="px-4 py-2 rounded-lg text-sm font-medium text-[rgb(var(--text-secondary))] bg-[rgb(var(--surface-1))] border border-[rgb(var(--border-strong))] hover:bg-[rgb(var(--surface-3))] transition-colors">
            Cancel
          </button>
          <button type="button" disabled={saving} onClick={onConfirm}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium text-white bg-red-600 hover:bg-red-700 shadow-sm disabled:opacity-50 transition-colors">
            {saving ? <><Loader2 size={14} className="animate-spin" /> Deleting...</> : 'Delete invite'}
          </button>
        </div>
      </div>
    </div>
  )
}

function ModalShell({ title, onClose, saving, children }: {
  title: string; onClose: () => void; saving: boolean; children: React.ReactNode
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="modal-title">
      <div className="absolute inset-0 bg-gray-950/95 backdrop-blur-md" onClick={() => !saving && onClose()} />
      <div className="relative bg-[rgb(var(--surface-1))] rounded-2xl w-full max-w-md shadow-2xl border border-[rgb(var(--border-subtle))]">
        <div className="flex items-center justify-between px-6 py-4 border-b border-[rgb(var(--border-subtle))]">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-brand-50 dark:bg-brand-900/30">
              <Ticket size={16} className="text-brand-600 dark:text-brand-400" aria-hidden="true" />
            </div>
            <h2 id="modal-title" className="text-base font-semibold text-[rgb(var(--text-primary))]">{title}</h2>
          </div>
          <button type="button" disabled={saving} onClick={onClose} aria-label="Close dialog"
            className="p-2 rounded-lg text-[rgb(var(--text-muted))] hover:text-[rgb(var(--text-secondary))] hover:bg-[rgb(var(--surface-3))] transition-colors">
            <X size={18} aria-hidden="true" />
          </button>
        </div>
        <div className="px-6 py-5">{children}</div>
      </div>
    </div>
  )
}

function ModalFooter({ onCancel, onConfirm, saving, confirmLabel }: {
  onCancel: () => void; onConfirm: () => void; saving: boolean; confirmLabel: string
}) {
  return (
    <div className="flex items-center justify-end gap-2 mt-6 -mx-6 -mb-5 px-6 py-4 border-t border-[rgb(var(--border-subtle))] bg-[rgb(var(--surface-2))] rounded-b-xl">
      <button type="button" disabled={saving} onClick={onCancel}
        className="px-4 py-2 rounded-lg text-sm font-medium text-[rgb(var(--text-secondary))] bg-[rgb(var(--surface-1))] border border-[rgb(var(--border-strong))] hover:bg-[rgb(var(--surface-3))] transition-colors">
        Cancel
      </button>
      <button type="button" disabled={saving} onClick={onConfirm}
        className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium text-white bg-brand-600 hover:bg-brand-700 shadow-sm disabled:opacity-50 transition-colors">
        {saving ? <><Loader2 size={14} className="animate-spin" /> Saving...</> : confirmLabel}
      </button>
    </div>
  )
}
