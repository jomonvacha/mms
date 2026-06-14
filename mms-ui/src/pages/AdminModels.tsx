import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { notify } from '../components/Toast'
import {
  Loader2, Plus, Trash2, Sparkles, Shield, ShieldOff,
  Pencil, Save, X, Layers, CheckCircle2, AlertTriangle, History,
  Cpu, Server,
} from 'lucide-react'
import {
  listAiModels, createAiModel, updateAiModel, deleteAiModel, setAiModelStatus,
  listAiProviders, upsertAiProvider,
  listAiModelBindings, setAiModelBinding, removeAiModelBinding,
  listAiModelAudit, listCategories,
  type AiModelRecord, type AiModelProviderRecord, type AiModelBindingRecord,
  type AiModelAuditRecord, type CategoryRecord,
  type CreateAiModelPayload, type UpdateAiModelPayload,
  type ApiError,
} from '../api/client'
import { useAuth } from '../hooks/useAuth'

type Tab = 'registry' | 'audit'

export default function AdminModels() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const [tab, setTab] = useState<Tab>('registry')
  const [loading, setLoading] = useState(true)
  const [models, setModels] = useState<AiModelRecord[]>([])
  const [providers, setProviders] = useState<AiModelProviderRecord[]>([])
  const [categories, setCategories] = useState<CategoryRecord[]>([])

  const [editorOpen, setEditorOpen] = useState<null | { mode: 'create' } | { mode: 'edit'; model: AiModelRecord }>(null)
  const [bindingsOpen, setBindingsOpen] = useState<AiModelRecord | null>(null)
  const [providerEditorOpen, setProviderEditorOpen] = useState<boolean>(false)

  const isAdmin = useMemo(() => (user?.roles || []).includes('ROLE_ADMIN'), [user])

  const refresh = async () => {
    setLoading(true)
    try {
      const [m, p, c] = await Promise.all([listAiModels(), listAiProviders(), listCategories()])
      setModels(m)
      setProviders(p)
      setCategories(c)
    } catch (e) {
      notify.error((e as Error).message || t('adminModels.loadFailed'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { refresh() }, [])

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="text-[11px] font-semibold uppercase tracking-[0.12em] text-gray-400 dark:text-gray-500 inline-flex items-center gap-2">
            <Sparkles size={12} /> {t('adminModels.eyebrow')}
          </div>
          <h1 className="text-[24px] leading-tight font-semibold text-gray-900 dark:text-gray-100 mt-1.5">
            {t('adminModels.title')}
          </h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400 max-w-2xl">
            {t('adminModels.subtitle')}
          </p>
        </div>
        {isAdmin && tab === 'registry' && (
          <div className="flex items-center gap-2 flex-shrink-0">
            <button type="button" onClick={() => setProviderEditorOpen(true)} className="btn-secondary text-sm">
              <Server size={14} /> {t('adminModels.actions.providers')}
            </button>
            <button type="button" onClick={() => setEditorOpen({ mode: 'create' })} className="btn-primary text-sm">
              <Plus size={14} /> {t('adminModels.actions.addModel')}
            </button>
          </div>
        )}
      </header>

      {/* Tabs */}
      <div className="flex items-center gap-1 border-b border-gray-200 dark:border-gray-800">
        <TabButton active={tab === 'registry'} onClick={() => setTab('registry')} icon={Layers} label={t('adminModels.tabs.registry')} />
        <TabButton active={tab === 'audit'} onClick={() => setTab('audit')} icon={History} label={t('adminModels.tabs.audit')} />
      </div>

      {tab === 'registry' ? (
        loading ? (
          <div className="card p-12 flex items-center justify-center">
            <Loader2 size={24} className="animate-spin text-brand-500" />
          </div>
        ) : models.length === 0 ? (
          <div className="card p-12 text-center">
            <Cpu size={28} className="mx-auto text-gray-300 dark:text-gray-600 mb-3" />
            <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{t('adminModels.empty.noModels')}</p>
            <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">{t('adminModels.empty.noModelsHint')}</p>
            {isAdmin && (
              <button type="button" onClick={() => setEditorOpen({ mode: 'create' })} className="btn-primary text-sm mt-4 inline-flex">
                <Plus size={14} /> {t('adminModels.actions.addModel')}
              </button>
            )}
          </div>
        ) : (
          <ModelsTable
            models={models}
            providers={providers}
            isAdmin={isAdmin}
            onEdit={(m) => setEditorOpen({ mode: 'edit', model: m })}
            onBindings={(m) => setBindingsOpen(m)}
            onStatusChange={async (code, status) => {
              try {
                await setAiModelStatus(code, status)
                notify.success(t('adminModels.statusToast', { code, status }))
                await refresh()
              } catch (e) { notify.error((e as Error).message || t('adminModels.statusFailed')) }
            }}
            onDelete={async (code) => {
              if (!confirm(t('adminModels.deleteConfirm', { code }))) return
              try {
                await deleteAiModel(code)
                notify.success(t('adminModels.deletedToast', { code }))
                await refresh()
              } catch (e) { notify.error((e as Error).message || t('adminModels.deleteFailed')) }
            }}
          />
        )
      ) : (
        <AuditLogTab />
      )}

      {/* Editor */}
      {editorOpen && (
        <ModelEditorModal
          mode={editorOpen.mode}
          initial={editorOpen.mode === 'edit' ? editorOpen.model : null}
          providers={providers}
          onClose={() => setEditorOpen(null)}
          onSaved={async () => { setEditorOpen(null); await refresh() }}
        />
      )}

      {/* Bindings */}
      {bindingsOpen && (
        <BindingsMatrixModal
          model={bindingsOpen}
          categories={categories}
          isAdmin={isAdmin}
          onClose={() => setBindingsOpen(null)}
        />
      )}

      {/* Providers */}
      {providerEditorOpen && (
        <ProvidersModal
          providers={providers}
          onClose={() => setProviderEditorOpen(false)}
          onSaved={async () => { await refresh() }}
        />
      )}
    </div>
  )
}

/* ──────────────────────────────────────────────────────────
 * Registry table
 * ────────────────────────────────────────────────────────── */

function ModelsTable({
  models, providers, isAdmin, onEdit, onBindings, onStatusChange, onDelete,
}: {
  models: AiModelRecord[]
  providers: AiModelProviderRecord[]
  isAdmin: boolean
  onEdit: (m: AiModelRecord) => void
  onBindings: (m: AiModelRecord) => void
  onStatusChange: (code: string, status: 'ENABLED' | 'DISABLED' | 'DEPRECATED') => void
  onDelete: (code: string) => void
}) {
  const { t } = useTranslation()
  const providerMap = useMemo(() => new Map(providers.map((p) => [p.code, p])), [providers])

  const rowActions = (m: AiModelRecord) => (
    <>
      <button
        type="button" onClick={() => onBindings(m)}
        className="icon-btn" title="Tier bindings" aria-label={`Tier bindings for ${m.displayName}`}
      >
        <Layers size={14} />
      </button>
      {isAdmin && (
        <>
          <button
            type="button" onClick={() => onEdit(m)}
            className="icon-btn" title="Edit" aria-label={`Edit ${m.displayName}`}
          >
            <Pencil size={14} />
          </button>
          <StatusActionMenu
            current={m.status}
            onChange={(next) => onStatusChange(m.code, next)}
          />
          <button
            type="button" onClick={() => onDelete(m.code)}
            className="icon-btn icon-btn-danger" title="Delete" aria-label={`Delete ${m.displayName}`}
          >
            <Trash2 size={14} />
          </button>
        </>
      )}
    </>
  )

  return (
    <>
      {/* Desktop + tablet: classic table. Hidden below md. */}
      <div className="card overflow-hidden hidden md:block">
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50 dark:bg-gray-900/40 text-[11px] uppercase tracking-wider text-gray-500 dark:text-gray-400">
              <tr>
                <th className="text-left font-semibold px-4 py-2.5">{t('adminModels.table.model')}</th>
                <th className="text-left font-semibold px-4 py-2.5">{t('adminModels.table.provider')}</th>
                <th className="text-left font-semibold px-4 py-2.5">{t('adminModels.table.qualityCost')}</th>
                <th className="text-left font-semibold px-4 py-2.5">{t('adminModels.table.status')}</th>
                <th className="text-left font-semibold px-4 py-2.5">{t('adminModels.table.lockStyle')}</th>
                <th className="text-left font-semibold px-4 py-2.5">{t('adminModels.table.sort')}</th>
                <th className="text-right font-semibold px-4 py-2.5">{t('adminModels.table.actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
              {models.map((m) => (
                <tr key={m.code} className="hover:bg-gray-50 dark:hover:bg-gray-900/40 transition-colors">
                  <td className="px-4 py-3 align-top min-w-0">
                    <div className="font-medium text-gray-900 dark:text-gray-100">{m.displayName}</div>
                    <div className="text-[11px] text-gray-500 dark:text-gray-400 font-mono">{m.code}</div>
                    {m.description && <div className="text-xs text-gray-500 dark:text-gray-400 mt-1 max-w-md">{m.description}</div>}
                  </td>
                  <td className="px-4 py-3 align-top">
                    <span className="inline-flex items-center gap-1.5 text-xs text-gray-600 dark:text-gray-300">
                      <Server size={11} /> {providerMap.get(m.providerCode)?.displayName || m.providerCode}
                    </span>
                  </td>
                  <td className="px-4 py-3 align-top">
                    <div className="flex items-center gap-3">
                      <LevelDots label="Q" ariaLabel={t('adminModels.table.quality')} value={m.qualityLevel} />
                      <LevelDots label="$" ariaLabel={t('adminModels.table.cost')} value={m.costLevel} />
                    </div>
                  </td>
                  <td className="px-4 py-3 align-top">
                    <StatusChip status={m.status} />
                  </td>
                  <td className="px-4 py-3 align-top">
                    <span className="text-[11px] font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                      {m.lockStyle || 'LOCK'}
                    </span>
                  </td>
                  <td className="px-4 py-3 align-top text-[12px] tabular-nums text-gray-500 dark:text-gray-400">
                    {m.sortOrder ?? 100}
                  </td>
                  <td className="px-4 py-3 align-top text-right">
                    <div className="inline-flex items-center gap-1">
                      {rowActions(m)}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Mobile: stacked card layout. */}
      <ul className="md:hidden space-y-2.5" aria-label="AI models">
        {models.map((m) => (
          <li key={m.code} className="card p-4">
            <div className="flex items-start justify-between gap-2 flex-wrap">
              <div className="min-w-0">
                <div className="font-medium text-gray-900 dark:text-gray-100">{m.displayName}</div>
                <div className="text-[11px] text-gray-500 dark:text-gray-400 font-mono mt-0.5">{m.code}</div>
              </div>
              <StatusChip status={m.status} />
            </div>
            {m.description && (
              <p className="text-xs text-gray-500 dark:text-gray-400 mt-2">{m.description}</p>
            )}
            <div className="flex items-center gap-4 mt-3 flex-wrap">
              <span className="inline-flex items-center gap-1.5 text-xs text-gray-600 dark:text-gray-300">
                <Server size={11} /> {providerMap.get(m.providerCode)?.displayName || m.providerCode}
              </span>
              <LevelDots label="Q" ariaLabel="Quality" value={m.qualityLevel} />
              <LevelDots label="$" ariaLabel="Cost" value={m.costLevel} />
              <span className="text-[11px] font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                {m.lockStyle || 'LOCK'}
              </span>
              <span className="text-[11px] tabular-nums text-gray-500 dark:text-gray-400">
                sort {m.sortOrder ?? 100}
              </span>
            </div>
            <div className="mt-3 pt-3 border-t border-gray-100 dark:border-gray-800 flex items-center gap-1 flex-wrap">
              {rowActions(m)}
            </div>
          </li>
        ))}
      </ul>
    </>
  )
}

function LevelDots({
  label, ariaLabel, value,
}: { label: string; ariaLabel?: string; value?: number }) {
  const n = Math.max(0, Math.min(4, value ?? 0))
  const a11yLabel = `${ariaLabel || label} level ${n} of 4`
  return (
    <div
      className="inline-flex items-center gap-1 text-[10.5px] text-gray-500 dark:text-gray-400"
      role="img"
      aria-label={a11yLabel}
      title={a11yLabel}
    >
      <span className="font-semibold w-3 text-center" aria-hidden="true">{label}</span>
      <div className="flex items-center gap-0.5" aria-hidden="true">
        {[1, 2, 3, 4].map((i) => (
          <span
            key={i}
            className={`w-1.5 h-1.5 rounded-full ${i <= n ? 'bg-brand-500' : 'bg-gray-200 dark:bg-gray-700'}`}
          />
        ))}
      </div>
    </div>
  )
}

function StatusChip({ status }: { status: AiModelRecord['status'] }) {
  const { t } = useTranslation()
  if (status === 'ENABLED') {
    return (
      <span
        role="status" aria-label={`Status: ${t('adminModels.status.enabled')}`}
        className="inline-flex items-center gap-1 h-5 px-1.5 rounded text-[10px] font-semibold uppercase bg-emerald-500/15 text-emerald-600 dark:text-emerald-400">
        <CheckCircle2 size={10} aria-hidden="true" /> {t('adminModels.status.enabled')}
      </span>
    )
  }
  if (status === 'DEPRECATED') {
    return (
      <span
        role="status" aria-label={`Status: ${t('adminModels.status.deprecated')}`}
        className="inline-flex items-center gap-1 h-5 px-1.5 rounded text-[10px] font-semibold uppercase bg-amber-500/15 text-amber-600 dark:text-amber-400">
        <AlertTriangle size={10} aria-hidden="true" /> {t('adminModels.status.deprecated')}
      </span>
    )
  }
  return (
    <span
      role="status" aria-label={`Status: ${t('adminModels.status.disabled')}`}
      className="inline-flex items-center gap-1 h-5 px-1.5 rounded text-[10px] font-semibold uppercase bg-gray-500/15 text-gray-500 dark:text-gray-400">
      <ShieldOff size={10} aria-hidden="true" /> {t('adminModels.status.disabled')}
    </span>
  )
}

function StatusActionMenu({
  current, onChange,
}: {
  current: AiModelRecord['status']
  onChange: (next: 'ENABLED' | 'DISABLED' | 'DEPRECATED') => void
}) {
  const [open, setOpen] = useState(false)
  return (
    <div className="relative">
      <button type="button" onClick={() => setOpen((o) => !o)} className="icon-btn" title="Status">
        <Shield size={14} />
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute right-0 top-full mt-1 w-40 z-20 card shadow-lg py-1">
            {(['ENABLED', 'DISABLED', 'DEPRECATED'] as const).map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => { setOpen(false); if (s !== current) onChange(s) }}
                className={`w-full flex items-center gap-2 px-3 py-1.5 text-xs text-left transition-colors ${
                  s === current
                    ? 'text-gray-400 cursor-default'
                    : 'text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800'
                }`}
              >
                {s === 'ENABLED' ? <CheckCircle2 size={12} /> : s === 'DEPRECATED' ? <AlertTriangle size={12} /> : <ShieldOff size={12} />}
                {s.charAt(0) + s.slice(1).toLowerCase()}
                {s === current && <span className="ml-auto text-[10px]">current</span>}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  )
}

function TabButton({
  active, onClick, icon: Icon, label,
}: { active: boolean; onClick: () => void; icon: React.ElementType; label: string }) {
  return (
    <button
      type="button" onClick={onClick}
      className={`inline-flex items-center gap-2 px-3 h-10 text-sm font-medium border-b-2 transition-colors ${
        active
          ? 'border-brand-500 text-gray-900 dark:text-gray-100'
          : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200'
      }`}
    >
      <Icon size={14} /> {label}
    </button>
  )
}

/* ──────────────────────────────────────────────────────────
 * Model editor (create/edit)
 * ────────────────────────────────────────────────────────── */

function ModelEditorModal({
  mode, initial, providers, onClose, onSaved,
}: {
  mode: 'create' | 'edit'
  initial: AiModelRecord | null
  providers: AiModelProviderRecord[]
  onClose: () => void
  onSaved: () => void
}) {
  const [code, setCode] = useState(initial?.code ?? '')
  const [providerCode, setProviderCode] = useState(initial?.providerCode ?? providers[0]?.code ?? '')
  const [displayName, setDisplayName] = useState(initial?.displayName ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [qualityLevel, setQualityLevel] = useState<number>(initial?.qualityLevel ?? 2)
  const [costLevel, setCostLevel] = useState<number>(initial?.costLevel ?? 2)
  const [sortOrder, setSortOrder] = useState<number>(initial?.sortOrder ?? 100)
  const [lockStyle, setLockStyle] = useState<'LOCK' | 'HIDE'>(initial?.lockStyle ?? 'LOCK')
  const [replacesModelCode, setReplacesModelCode] = useState(initial?.replacesModelCode ?? '')
  const [saving, setSaving] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const { t } = useTranslation()

  const fieldError = (name: string) => fieldErrors[name]

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setFieldErrors({})
    if (mode === 'create' && (!code.trim() || !providerCode || !displayName.trim())) {
      notify.error(t('adminModels.editor.requiredFields'))
      return
    }
    setSaving(true)
    try {
      if (mode === 'create') {
        const payload: CreateAiModelPayload = {
          code: code.trim(),
          providerCode,
          displayName: displayName.trim(),
          description: description.trim() || undefined,
          qualityLevel, costLevel, sortOrder, lockStyle,
          replacesModelCode: replacesModelCode.trim() || undefined,
        }
        await createAiModel(payload)
        notify.success(t('adminModels.editor.createdToast'))
      } else if (initial) {
        const payload: UpdateAiModelPayload = {
          providerCode,
          displayName: displayName.trim(),
          description: description.trim() || undefined,
          qualityLevel, costLevel, sortOrder, lockStyle,
          replacesModelCode: replacesModelCode.trim() || undefined,
        }
        await updateAiModel(initial.code, payload)
        notify.success(t('adminModels.editor.updatedToast'))
      }
      onSaved()
    } catch (e) {
      const err = e as ApiError
      if (err.fieldErrors && Object.keys(err.fieldErrors).length) {
        setFieldErrors(err.fieldErrors)
        notify.error(t('adminModels.editor.fieldErrorsToast'))
      } else {
        notify.error(err.message || t('adminModels.editor.saveFailed'))
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={mode === 'create' ? t('adminModels.editor.titleCreate') : t('adminModels.editor.titleEdit', { name: initial?.displayName })} onClose={onClose}>
      <form onSubmit={onSubmit} className="space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="label" htmlFor="ai-model-code">Code <span className="text-red-500">*</span></label>
            <input
              id="ai-model-code"
              className="input font-mono" value={code} onChange={(e) => setCode(e.target.value)}
              placeholder="gpt-5.4-mini" disabled={mode === 'edit'} required
              aria-invalid={Boolean(fieldError('code')) || undefined}
              aria-describedby={fieldError('code') ? 'ai-model-code-err' : undefined}
            />
            {fieldError('code')
              ? <p id="ai-model-code-err" className="text-[11px] text-red-500 mt-1">{fieldError('code')}</p>
              : mode === 'edit' && <p className="text-[11px] text-gray-500 mt-1">Code is immutable after creation.</p>}
          </div>
          <div>
            <label className="label" htmlFor="ai-model-provider">Provider <span className="text-red-500">*</span></label>
            <select
              id="ai-model-provider"
              className="input" value={providerCode} onChange={(e) => setProviderCode(e.target.value)} required
              aria-invalid={Boolean(fieldError('providerCode')) || undefined}
              aria-describedby={fieldError('providerCode') ? 'ai-model-provider-err' : undefined}
            >
              {providers.map((p) => <option key={p.code} value={p.code}>{p.displayName || p.code}</option>)}
            </select>
            {fieldError('providerCode') && (
              <p id="ai-model-provider-err" className="text-[11px] text-red-500 mt-1">{fieldError('providerCode')}</p>
            )}
          </div>
        </div>
        <div>
          <label className="label" htmlFor="ai-model-display">Display name <span className="text-red-500">*</span></label>
          <input
            id="ai-model-display"
            className="input" value={displayName} onChange={(e) => setDisplayName(e.target.value)} required
            aria-invalid={Boolean(fieldError('displayName')) || undefined}
            aria-describedby={fieldError('displayName') ? 'ai-model-display-err' : undefined}
          />
          {fieldError('displayName') && (
            <p id="ai-model-display-err" className="text-[11px] text-red-500 mt-1">{fieldError('displayName')}</p>
          )}
        </div>
        <div>
          <label className="label">Description</label>
          <textarea className="input min-h-[64px]" value={description} onChange={(e) => setDescription(e.target.value)}
            placeholder="Short marketing blurb shown on selection cards" />
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <LevelInput label="Quality" value={qualityLevel} onChange={setQualityLevel} />
          <LevelInput label="Cost" value={costLevel} onChange={setCostLevel} />
          <div>
            <label className="label">Sort order</label>
            <input className="input" type="number" value={sortOrder}
              onChange={(e) => setSortOrder(Number(e.target.value) || 0)} />
          </div>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="label">Lock style</label>
            <div className="flex gap-2">
              {(['LOCK', 'HIDE'] as const).map((opt) => (
                <button key={opt} type="button" onClick={() => setLockStyle(opt)}
                  className={`px-3 py-1.5 rounded-lg text-sm border transition-colors ${
                    lockStyle === opt
                      ? 'border-brand-500 bg-brand-50 text-brand-700 dark:bg-brand-900/30 dark:text-brand-300'
                      : 'border-gray-300 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800'
                  }`}>{opt}</button>
              ))}
            </div>
            <p className="text-[11px] text-gray-500 mt-1">
              {lockStyle === 'LOCK'
                ? 'Unavailable tiers see the model with an Upgrade prompt.'
                : 'Unavailable tiers don\'t see the model at all.'}
            </p>
          </div>
          <div>
            <label className="label">Replaces (optional)</label>
            <input className="input font-mono" value={replacesModelCode}
              onChange={(e) => setReplacesModelCode(e.target.value)}
              placeholder="e.g. gpt-5.3" />
            <p className="text-[11px] text-gray-500 mt-1">Code of the model this one replaces, for migration hints.</p>
          </div>
        </div>

        <div className="flex items-center justify-end gap-2 pt-2 border-t border-gray-100 dark:border-gray-800">
          <button type="button" className="btn-secondary text-sm" onClick={onClose} disabled={saving}>{t('adminModels.actions.cancel')}</button>
          <button type="submit" className="btn-primary text-sm" disabled={saving}>
            {saving ? <><Loader2 size={14} className="animate-spin" /> {t('adminModels.editor.savingEllipsis')}</> : <><Save size={14} /> {t('adminModels.actions.save')}</>}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function LevelInput({
  label, value, onChange,
}: { label: string; value: number; onChange: (n: number) => void }) {
  return (
    <div>
      <label className="label">{label} <span className="text-gray-400 font-normal">(1-4)</span></label>
      <div className="flex gap-1">
        {[1, 2, 3, 4].map((i) => (
          <button key={i} type="button" onClick={() => onChange(i)}
            className={`flex-1 h-9 rounded-lg border text-sm font-medium transition-colors ${
              value === i
                ? 'border-brand-500 bg-brand-50 text-brand-700 dark:bg-brand-900/30 dark:text-brand-300'
                : 'border-gray-300 dark:border-gray-700 text-gray-500 hover:bg-gray-50 dark:hover:bg-gray-800'
            }`}>{i}</button>
        ))}
      </div>
    </div>
  )
}

/* ──────────────────────────────────────────────────────────
 * Tier bindings matrix
 * ────────────────────────────────────────────────────────── */

function BindingsMatrixModal({
  model, categories, isAdmin, onClose,
}: {
  model: AiModelRecord
  categories: CategoryRecord[]
  isAdmin: boolean
  onClose: () => void
}) {
  const [bindings, setBindings] = useState<AiModelBindingRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState<string>('') // key like "PERSONAL/PRO"
  const { t } = useTranslation()

  const refresh = async () => {
    setLoading(true)
    try {
      const b = await listAiModelBindings(model.code)
      setBindings(b)
    } catch (e) {
      notify.error((e as Error).message || t('adminModels.bindings.loadFailed'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { refresh() }, [model.code])

  const bindingMap = useMemo(() => {
    const map = new Map<string, AiModelBindingRecord>()
    for (const b of bindings) map.set(`${b.categoryCode}/${b.tierCode}`, b)
    return map
  }, [bindings])

  const toggleBinding = async (category: string, tier: string) => {
    const key = `${category}/${tier}`
    setSaving(key)
    try {
      if (bindingMap.has(key)) {
        await removeAiModelBinding(model.code, category, tier)
        notify.success(t('adminModels.bindings.removedFromToast', { key }))
      } else {
        await setAiModelBinding(model.code, category, tier, {})
        notify.success(t('adminModels.bindings.includedInToast', { key }))
      }
      await refresh()
    } catch (e) {
      notify.error((e as Error).message || t('adminModels.bindings.updateFailed'))
    } finally { setSaving('') }
  }

  const setDefault = async (category: string, tier: string) => {
    const key = `${category}/${tier}`
    setSaving(key)
    try {
      await setAiModelBinding(model.code, category, tier, { isDefault: true })
      notify.success(t('adminModels.bindings.defaultForToast', { key }))
      await refresh()
    } catch (e) {
      notify.error((e as Error).message || t('adminModels.bindings.setDefaultFailed'))
    } finally { setSaving('') }
  }

  // Arrow-key keyboard navigation across the binding toggles. Moves focus
  // between the "Included / Not included" buttons; Home / End jump to first
  // or last. Enter/Space fall through to the button's native click.
  const bindingsGridKeyHandler = (e: React.KeyboardEvent<HTMLDivElement>) => {
    const key = e.key
    if (!['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(key)) return
    const buttons = Array.from(
      e.currentTarget.querySelectorAll<HTMLButtonElement>('[data-binding-toggle]')
    )
    if (buttons.length === 0) return
    const idx = buttons.findIndex((b) => b === document.activeElement)
    let next = idx
    if (key === 'ArrowDown' || key === 'ArrowRight') next = (idx + 1 + buttons.length) % buttons.length
    else if (key === 'ArrowUp' || key === 'ArrowLeft') next = (idx - 1 + buttons.length) % buttons.length
    else if (key === 'Home') next = 0
    else if (key === 'End') next = buttons.length - 1
    if (next !== idx) { e.preventDefault(); buttons[next]?.focus() }
  }

  return (
    <Modal title={t('adminModels.bindings.title', { name: model.displayName })} onClose={onClose} wide>
      <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
        {t('adminModels.bindings.description')}
      </p>

      {loading ? (
        <div className="h-40 flex items-center justify-center">
          <Loader2 size={22} className="animate-spin text-brand-500" />
        </div>
      ) : (
        <div
          className="space-y-4"
          onKeyDown={bindingsGridKeyHandler}
          role="grid"
          aria-label="Tier bindings grid"
        >
          {categories.map((cat) => (
            <div key={cat.code} className="rounded-lg border border-gray-200 dark:border-gray-800 overflow-hidden">
              <div className="px-4 py-2 bg-gray-50 dark:bg-gray-900/40 flex items-center justify-between">
                <span className="text-sm font-semibold text-gray-900 dark:text-gray-100">{cat.displayName || cat.code}</span>
                <span className="text-[11px] uppercase tracking-wider text-gray-500 dark:text-gray-400">{cat.code}</span>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2 p-3" role="row">
                {(cat.tiers || []).map((tier) => {
                  const key = `${cat.code}/${tier.tierCode}`
                  const b = bindingMap.get(key)
                  const included = Boolean(b)
                  const isDefault = b?.isDefault ?? false
                  const rowSaving = saving === key
                  return (
                    <div key={tier.tierCode}
                      className={`rounded-lg border p-3 transition-colors ${
                        included
                          ? 'border-brand-500/50 bg-brand-50/50 dark:bg-brand-900/20'
                          : 'border-gray-200 dark:border-gray-800'
                      }`}>
                      <div className="flex items-center justify-between gap-2">
                        <div className="min-w-0">
                          <div className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
                            {tier.displayName || tier.tierCode}
                          </div>
                          <div className="text-[11px] text-gray-500 dark:text-gray-400 font-mono">{tier.tierCode}</div>
                        </div>
                        {isDefault && (
                          <span className="text-[10px] font-semibold uppercase px-1.5 h-5 inline-flex items-center rounded bg-amber-500/15 text-amber-600 dark:text-amber-400">
                            {t('adminModels.bindings.defaultBadge')}
                          </span>
                        )}
                      </div>
                      <div className="flex items-center gap-2 mt-2">
                        <button
                          type="button" disabled={!isAdmin || rowSaving}
                          data-binding-toggle="true"
                          aria-pressed={included}
                          aria-label={t(`adminModels.bindings.${included ? 'included' : 'notIncluded'}`) + ` ${cat.code}/${tier.tierCode}`}
                          onClick={() => toggleBinding(cat.code, tier.tierCode)}
                          className={`flex-1 h-8 rounded-md text-xs font-medium border transition-colors
                            focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 ${
                            included
                              ? 'border-brand-500 bg-brand-500/10 text-brand-700 dark:text-brand-300 hover:bg-brand-500/20'
                              : 'border-gray-300 dark:border-gray-700 text-gray-500 hover:bg-gray-50 dark:hover:bg-gray-800'
                          } ${!isAdmin || rowSaving ? 'opacity-60 cursor-not-allowed' : ''}`}
                        >
                          {rowSaving ? <Loader2 size={12} className="animate-spin mx-auto" aria-label="Saving" /> :
                           included ? t('adminModels.bindings.included') : t('adminModels.bindings.notIncluded')}
                        </button>
                        {included && isAdmin && !isDefault && (
                          <button type="button" disabled={rowSaving}
                            onClick={() => setDefault(cat.code, tier.tierCode)}
                            className="h-8 px-2 rounded-md text-[11px] font-medium border border-gray-300 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800"
                            title={t('adminModels.bindings.setDefault')}>
                            {t('adminModels.bindings.setDefault')}
                          </button>
                        )}
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="flex items-center justify-end gap-2 pt-4 mt-4 border-t border-gray-100 dark:border-gray-800">
        <button type="button" className="btn-secondary text-sm" onClick={onClose}>{t('adminModels.actions.done')}</button>
      </div>
    </Modal>
  )
}

/* ──────────────────────────────────────────────────────────
 * Providers modal
 * ────────────────────────────────────────────────────────── */

function ProvidersModal({
  providers, onClose, onSaved,
}: {
  providers: AiModelProviderRecord[]
  onClose: () => void
  onSaved: () => Promise<void>
}) {
  const [rows, setRows] = useState<AiModelProviderRecord[]>(() => [...providers])
  const [newCode, setNewCode] = useState('')
  const [newName, setNewName] = useState('')
  const [saving, setSaving] = useState(false)
  const { t } = useTranslation()

  const save = async (p: AiModelProviderRecord) => {
    setSaving(true)
    try {
      await upsertAiProvider(p.code, { displayName: p.displayName, baseUrl: p.baseUrl, enabled: p.enabled })
      notify.success(t('adminModels.providers.savedToast', { code: p.code }))
      await onSaved()
    } catch (e) { notify.error((e as Error).message || t('adminModels.providers.saveFailed')) }
    finally { setSaving(false) }
  }

  const addNew = async () => {
    if (!newCode.trim()) { notify.error(t('adminModels.providers.codeRequired')); return }
    setSaving(true)
    try {
      await upsertAiProvider(newCode.trim(), { displayName: newName.trim() || newCode.trim(), enabled: true })
      notify.success(t('adminModels.providers.addedToast', { code: newCode }))
      setNewCode(''); setNewName('')
      await onSaved()
    } catch (e) { notify.error((e as Error).message || t('adminModels.providers.addFailed')) }
    finally { setSaving(false) }
  }

  useEffect(() => { setRows([...providers]) }, [providers])

  return (
    <Modal title={t('adminModels.providers.title')} onClose={onClose}>
      <div className="space-y-3">
        {rows.map((p, idx) => (
          <div key={p.code} className="flex flex-wrap items-center gap-2 p-3 rounded-lg border border-gray-200 dark:border-gray-800">
            <span className="text-xs font-mono text-gray-600 dark:text-gray-400 w-24 truncate">{p.code}</span>
            <input
              className="input flex-1 min-w-[200px]"
              value={p.displayName || ''}
              onChange={(e) => setRows(rows.map((r, i) => i === idx ? { ...r, displayName: e.target.value } : r))}
              placeholder={t('adminModels.providers.displayNamePlaceholder')}
            />
            <input
              className="input flex-1 min-w-[240px]"
              value={p.baseUrl || ''}
              onChange={(e) => setRows(rows.map((r, i) => i === idx ? { ...r, baseUrl: e.target.value } : r))}
              placeholder={t('adminModels.providers.baseUrlPlaceholder')}
            />
            <label className="inline-flex items-center gap-1.5 text-xs text-gray-600 dark:text-gray-300">
              <input
                type="checkbox"
                checked={p.enabled}
                onChange={(e) => setRows(rows.map((r, i) => i === idx ? { ...r, enabled: e.target.checked } : r))}
              /> {t('adminModels.providers.enabled')}
            </label>
            <button type="button" className="btn-secondary text-xs" onClick={() => save(p)} disabled={saving}>
              <Save size={12} /> {t('adminModels.actions.save')}
            </button>
          </div>
        ))}

        <div className="flex flex-wrap items-center gap-2 p-3 rounded-lg border border-dashed border-gray-300 dark:border-gray-700">
          <input className="input flex-1 min-w-[140px] font-mono" placeholder={t('adminModels.providers.newPlaceholder')}
            value={newCode} onChange={(e) => setNewCode(e.target.value)} />
          <input className="input flex-1 min-w-[200px]" placeholder={t('adminModels.providers.displayNamePlaceholder')}
            value={newName} onChange={(e) => setNewName(e.target.value)} />
          <button type="button" className="btn-primary text-xs" onClick={addNew} disabled={saving || !newCode.trim()}>
            <Plus size={12} /> {t('adminModels.providers.add')}
          </button>
        </div>
      </div>

      <div className="flex items-center justify-end gap-2 pt-4 mt-4 border-t border-gray-100 dark:border-gray-800">
        <button type="button" className="btn-secondary text-sm" onClick={onClose}>{t('adminModels.actions.done')}</button>
      </div>
    </Modal>
  )
}

/* ──────────────────────────────────────────────────────────
 * Audit log
 * ────────────────────────────────────────────────────────── */

function AuditLogTab() {
  const [rows, setRows] = useState<AiModelAuditRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [filterModel, setFilterModel] = useState('')
  const { t } = useTranslation()

  const refresh = async () => {
    setLoading(true)
    try {
      const res = await listAiModelAudit({ modelCode: filterModel || undefined, size: 100 })
      setRows(res.content)
    } catch (e) {
      notify.error((e as Error).message || t('adminModels.audit.loadFailed'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { refresh() }, [])

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2 flex-wrap">
        <input className="input flex-1 max-w-xs font-mono" placeholder={t('adminModels.audit.filterPlaceholder')}
          value={filterModel} onChange={(e) => setFilterModel(e.target.value)} />
        <button type="button" className="btn-secondary text-sm" onClick={refresh}>
          {t('adminModels.actions.apply')}
        </button>
      </div>
      {loading ? (
        <div className="card p-12 flex items-center justify-center">
          <Loader2 size={22} className="animate-spin text-brand-500" />
        </div>
      ) : rows.length === 0 ? (
        <div className="card p-8 text-center text-sm text-gray-500 dark:text-gray-400">
          {t('adminModels.empty.noAudit')}
        </div>
      ) : (
        <>
          {/* Desktop + tablet: table */}
          <div className="card overflow-hidden hidden md:block">
            <table className="min-w-full text-sm">
              <thead className="bg-gray-50 dark:bg-gray-900/40 text-[11px] uppercase tracking-wider text-gray-500 dark:text-gray-400">
                <tr>
                  <th className="text-left font-semibold px-4 py-2.5">{t('adminModels.table.when')}</th>
                  <th className="text-left font-semibold px-4 py-2.5">{t('adminModels.table.actor')}</th>
                  <th className="text-left font-semibold px-4 py-2.5">{t('adminModels.table.action')}</th>
                  <th className="text-left font-semibold px-4 py-2.5">{t('adminModels.table.model')}</th>
                  <th className="text-left font-semibold px-4 py-2.5">{t('adminModels.table.binding')}</th>
                  <th className="text-left font-semibold px-4 py-2.5">{t('adminModels.table.ip')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                {rows.map((r) => (
                  <tr key={r.id} className="align-top">
                    <td className="px-4 py-2.5 text-[12px] text-gray-500 dark:text-gray-400 tabular-nums whitespace-nowrap">
                      {new Date(r.at).toLocaleString()}
                    </td>
                    <td className="px-4 py-2.5 text-xs font-mono text-gray-700 dark:text-gray-300">{r.actorId || '—'}</td>
                    <td className="px-4 py-2.5">
                      <span className="inline-flex items-center gap-1 h-5 px-1.5 rounded text-[10px] font-semibold uppercase bg-gray-500/10 text-gray-600 dark:text-gray-300">
                        {r.action}
                      </span>
                    </td>
                    <td className="px-4 py-2.5 text-xs font-mono text-gray-700 dark:text-gray-300">{r.modelCode || '—'}</td>
                    <td className="px-4 py-2.5 text-xs text-gray-500 dark:text-gray-400">
                      {r.binding ? `${r.binding.categoryCode}/${r.binding.tierCode}` : '—'}
                    </td>
                    <td className="px-4 py-2.5 text-xs text-gray-500 dark:text-gray-400 font-mono">{r.ip || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile: card stack */}
          <ul className="md:hidden space-y-2" aria-label="Audit events">
            {rows.map((r) => (
              <li key={r.id} className="card p-3">
                <div className="flex items-center justify-between gap-2 flex-wrap">
                  <span className="inline-flex items-center gap-1 h-5 px-1.5 rounded text-[10px] font-semibold uppercase bg-gray-500/10 text-gray-600 dark:text-gray-300">
                    {r.action}
                  </span>
                  <span className="text-[11px] text-gray-500 dark:text-gray-400 tabular-nums">
                    {new Date(r.at).toLocaleString()}
                  </span>
                </div>
                <div className="mt-2 text-xs text-gray-700 dark:text-gray-300 font-mono truncate">
                  {r.modelCode || '—'}
                  {r.binding && <span className="text-gray-500 dark:text-gray-400"> · {r.binding.categoryCode}/{r.binding.tierCode}</span>}
                </div>
                <div className="mt-2 flex items-center gap-2 flex-wrap text-[11px] text-gray-500 dark:text-gray-400">
                  <span className="font-mono">{r.actorId || '—'}</span>
                  {r.ip && <span className="font-mono opacity-75">· {r.ip}</span>}
                </div>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}

/* ──────────────────────────────────────────────────────────
 * Modal shell
 * ────────────────────────────────────────────────────────── */

function Modal({
  title, onClose, children, wide,
}: { title: string; onClose: () => void; children: React.ReactNode; wide?: boolean }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-gray-950/70 backdrop-blur-sm" onClick={onClose} />
      <div className={`relative bg-white dark:bg-gray-900 rounded-xl shadow-2xl border border-gray-200 dark:border-gray-800 w-full ${wide ? 'max-w-4xl' : 'max-w-2xl'} max-h-[90vh] flex flex-col`}>
        <div className="flex items-center justify-between p-4 border-b border-gray-200 dark:border-gray-800">
          <h3 className="text-[15px] font-semibold text-gray-900 dark:text-gray-100">{title}</h3>
          <button type="button" onClick={onClose} className="icon-btn" aria-label="Close">
            <X size={16} />
          </button>
        </div>
        <div className="flex-1 overflow-auto p-5">
          {children}
        </div>
      </div>
    </div>
  )
}
