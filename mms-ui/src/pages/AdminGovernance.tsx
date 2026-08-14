import { useEffect, useMemo, useState } from 'react'
import { notify } from '../components/Toast'
import { Loader2, Plus, Trash2, Save, Shield, Layers, Check, X, ChevronDown, ChevronRight, Pencil } from 'lucide-react'
import {
  listCategories,
  createCategory,
  updateCategory,
  deleteCategory,
  createTier,
  updateTier,
  deleteTier,
  listEntitlementDefs,
  getTierEntitlements,
  setTierEntitlement,
  removeTierEntitlement,
  type CategoryRecord,
  type TierRecord,
  type EntitlementRecord,
} from '../api/client'
import { useAuth } from '../hooks/useAuth'

type TierWithEntitlements = TierRecord & { entitlements?: Record<string, string>; entitlementsLoaded?: boolean }

export default function AdminGovernance() {
  const { user } = useAuth()
  const [categories, setCategories] = useState<CategoryRecord[]>([])
  const [entitlementDefs, setEntitlementDefs] = useState<EntitlementRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [expanded, setExpanded] = useState<Record<string, boolean>>({})
  const [tierEntitlements, setTierEntitlements] = useState<Record<string, TierWithEntitlements>>({})
  const [showNewCategory, setShowNewCategory] = useState(false)
  const [newCategoryForm, setNewCategoryForm] = useState({ code: '', displayName: '', description: '' })
  const [newTierForm, setNewTierForm] = useState<Record<string, { tierCode: string; displayName: string; description: string }>>({})
  const [savingKey, setSavingKey] = useState<string>('')

  const canManage = useMemo(() => {
    const roles = user?.roles || []
    return roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER')
  }, [user])

  const refresh = async () => {
    setLoading(true)
    try {
      const [cats, defs] = await Promise.all([listCategories(), listEntitlementDefs()])
      setCategories(cats)
      setEntitlementDefs(defs)
    } catch (e) {
      notify.error((e as Error).message || 'Failed to load governance data')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { refresh() }, [])

  const toggleCategoryEnabled = async (cat: CategoryRecord) => {
    try {
      setSavingKey('cat:' + cat.code)
      await updateCategory(cat.code, { enabled: !cat.enabled })
      notify.success(`${cat.displayName || cat.code} ${cat.enabled ? 'disabled' : 'enabled'}`)
      await refresh()
    } catch (e) {
      notify.error((e as Error).message || 'Failed to update category')
    } finally {
      setSavingKey('')
    }
  }

  const toggleTierEnabled = async (categoryCode: string, tier: TierRecord) => {
    try {
      setSavingKey(`tier:${categoryCode}/${tier.tierCode}`)
      await updateTier(categoryCode, tier.tierCode, { enabled: !tier.enabled })
      notify.success(`${tier.displayName || tier.tierCode} ${tier.enabled ? 'disabled' : 'enabled'}`)
      await refresh()
    } catch (e) {
      notify.error((e as Error).message || 'Failed to update tier')
    } finally {
      setSavingKey('')
    }
  }

  const handleCreateCategory = async () => {
    const { code, displayName, description } = newCategoryForm
    if (!code.trim()) { notify.error('Category code is required'); return }
    try {
      setSavingKey('new-cat')
      await createCategory({
        code: code.trim().toUpperCase(),
        displayName: displayName.trim() || undefined,
        description: description.trim() || undefined,
      })
      notify.success('Category created')
      setShowNewCategory(false)
      setNewCategoryForm({ code: '', displayName: '', description: '' })
      await refresh()
    } catch (e) {
      notify.error((e as Error).message || 'Failed to create category')
    } finally {
      setSavingKey('')
    }
  }

  const handleDeleteCategory = async (cat: CategoryRecord) => {
    if (cat.system) { notify.error('System categories cannot be deleted — disable instead'); return }
    if (!confirm(`Delete category "${cat.displayName || cat.code}"?`)) return
    try {
      await deleteCategory(cat.code)
      notify.success('Category deleted')
      await refresh()
    } catch (e) {
      notify.error((e as Error).message || 'Delete failed')
    }
  }

  const handleCreateTier = async (categoryCode: string) => {
    const form = newTierForm[categoryCode]
    if (!form?.tierCode?.trim()) { notify.error('Tier code is required'); return }
    try {
      setSavingKey(`new-tier:${categoryCode}`)
      await createTier(categoryCode, {
        tierCode: form.tierCode.trim().toUpperCase(),
        displayName: form.displayName?.trim() || undefined,
        description: form.description?.trim() || undefined,
      })
      notify.success('Tier created')
      setNewTierForm((s) => ({ ...s, [categoryCode]: { tierCode: '', displayName: '', description: '' } }))
      await refresh()
    } catch (e) {
      notify.error((e as Error).message || 'Failed to create tier')
    } finally {
      setSavingKey('')
    }
  }

  const handleDeleteTier = async (categoryCode: string, tier: TierRecord) => {
    if (tier.system) { notify.error('System tiers cannot be deleted — disable instead'); return }
    if (!confirm(`Delete tier "${tier.displayName || tier.tierCode}" (category: ${categoryCode})?`)) return
    try {
      await deleteTier(categoryCode, tier.tierCode)
      notify.success('Tier deleted')
      await refresh()
    } catch (e) {
      notify.error((e as Error).message || 'Delete failed')
    }
  }

  const toggleExpand = async (categoryCode: string, tierCode: string) => {
    const key = `${categoryCode}/${tierCode}`
    const next = !expanded[key]
    setExpanded((s) => ({ ...s, [key]: next }))
    if (next && !tierEntitlements[key]?.entitlementsLoaded) {
      try {
        const values = await getTierEntitlements(categoryCode, tierCode)
        setTierEntitlements((s) => ({ ...s, [key]: { categoryCode, tierCode, entitlements: values, entitlementsLoaded: true } as TierWithEntitlements }))
      } catch (e) {
        notify.error((e as Error).message || 'Failed to load entitlements')
      }
    }
  }

  const updateEntitlementValue = async (categoryCode: string, tierCode: string, entitlementKey: string, value: string) => {
    const key = `${categoryCode}/${tierCode}`
    try {
      setSavingKey(`ent:${key}:${entitlementKey}`)
      await setTierEntitlement(categoryCode, tierCode, { entitlementKey, value })
      notify.success('Entitlement saved')
      setTierEntitlements((s) => ({
        ...s,
        [key]: { ...(s[key] || {} as TierWithEntitlements), entitlements: { ...(s[key]?.entitlements || {}), [entitlementKey]: value } } as TierWithEntitlements,
      }))
    } catch (e) {
      notify.error((e as Error).message || 'Save failed')
    } finally {
      setSavingKey('')
    }
  }

  const resetEntitlement = async (categoryCode: string, tierCode: string, entitlementKey: string) => {
    const key = `${categoryCode}/${tierCode}`
    try {
      await removeTierEntitlement(categoryCode, tierCode, entitlementKey)
      notify.success('Reset to default')
      const values = await getTierEntitlements(categoryCode, tierCode)
      setTierEntitlements((s) => ({ ...s, [key]: { categoryCode, tierCode, entitlements: values, entitlementsLoaded: true } as TierWithEntitlements }))
    } catch (e) {
      notify.error((e as Error).message || 'Reset failed')
    }
  }

  if (loading) {
    return (
      <div className="container mx-auto px-4 max-w-6xl py-10">
        <div className="card p-10 flex items-center justify-center">
          <Loader2 className="animate-spin text-brand-600" size={24} />
        </div>
      </div>
    )
  }

  return (
    <div className="container mx-auto px-4 max-w-6xl py-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-[rgb(var(--text-primary))] flex items-center gap-2">
            <Shield size={22} className="text-brand-600 dark:text-brand-400" />
            Membership Governance
          </h1>
          <p className="text-sm text-[rgb(var(--text-muted))] mt-1">
            Manage membership categories, category-specific tiers, and entitlement values that drive feature enforcement.
          </p>
        </div>
        {canManage && !showNewCategory && (
          <button className="btn-primary flex items-center gap-1.5" onClick={() => setShowNewCategory(true)}>
            <Plus size={16} /> New category
          </button>
        )}
      </div>

      {showNewCategory && (
        <div className="card p-4 space-y-3">
          <h3 className="font-semibold text-[rgb(var(--text-primary))]">New category</h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <input
              className="input"
              placeholder="CODE (e.g. PARTNER)"
              value={newCategoryForm.code}
              onChange={(e) => setNewCategoryForm((s) => ({ ...s, code: e.target.value.toUpperCase() }))}
            />
            <input
              className="input"
              placeholder="Display name"
              value={newCategoryForm.displayName}
              onChange={(e) => setNewCategoryForm((s) => ({ ...s, displayName: e.target.value }))}
            />
            <input
              className="input"
              placeholder="Description"
              value={newCategoryForm.description}
              onChange={(e) => setNewCategoryForm((s) => ({ ...s, description: e.target.value }))}
            />
          </div>
          <div className="flex gap-2 justify-end">
            <button className="btn-secondary" onClick={() => setShowNewCategory(false)}>Cancel</button>
            <button className="btn-primary flex items-center gap-1.5" disabled={savingKey === 'new-cat'} onClick={handleCreateCategory}>
              {savingKey === 'new-cat' ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
              Create
            </button>
          </div>
        </div>
      )}

      {categories.length === 0 ? (
        <div className="card p-10 text-center text-[rgb(var(--text-muted))]">
          No membership categories yet.
        </div>
      ) : (
        <div className="space-y-4">
          {categories.map((cat) => (
            <div key={cat.code} className="card">
              <div className="p-4 border-b border-[rgb(var(--border-subtle))] flex items-center gap-3">
                <Layers size={18} className="text-brand-600 dark:text-brand-400" />
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-[rgb(var(--text-primary))]">{cat.displayName || cat.code}</span>
                    <span className="text-xs font-mono text-[rgb(var(--text-muted))]">{cat.code}</span>
                    {cat.system && (
                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-brand-50 text-brand-700 dark:bg-brand-900/30 dark:text-brand-400">System</span>
                    )}
                    <span className={`text-[10px] px-1.5 py-0.5 rounded ${cat.enabled ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400' : 'bg-[rgb(var(--surface-3))] text-[rgb(var(--text-secondary))]'}`}>
                      {cat.enabled ? 'Enabled' : 'Disabled'}
                    </span>
                  </div>
                  {cat.description && <p className="text-xs text-[rgb(var(--text-muted))] mt-0.5">{cat.description}</p>}
                </div>
                {canManage && (
                  <div className="flex items-center gap-2">
                    <button
                      className="btn-secondary flex items-center gap-1 text-xs"
                      disabled={savingKey === 'cat:' + cat.code}
                      onClick={() => toggleCategoryEnabled(cat)}
                    >
                      {cat.enabled ? <><X size={14} /> Disable</> : <><Check size={14} /> Enable</>}
                    </button>
                    {!cat.system && (
                      <button className="btn-danger flex items-center gap-1 text-xs" onClick={() => handleDeleteCategory(cat)}>
                        <Trash2 size={14} /> Delete
                      </button>
                    )}
                  </div>
                )}
              </div>

              <div className="p-4 space-y-2">
                {(cat.tiers || []).length === 0 && (
                  <p className="text-sm text-[rgb(var(--text-muted))] italic">No tiers in this category yet.</p>
                )}
                {(cat.tiers || []).map((tier) => {
                  const key = `${cat.code}/${tier.tierCode}`
                  const isExpanded = expanded[key]
                  const values = tierEntitlements[key]?.entitlements || {}
                  return (
                    <div key={tier.tierCode} className="border border-[rgb(var(--border-subtle))] rounded-lg">
                      <div className="flex items-center gap-2 p-3">
                        <button
                          onClick={() => toggleExpand(cat.code, tier.tierCode)}
                          className="p-1 text-[rgb(var(--text-muted))] hover:text-[rgb(var(--text-primary))]"
                          aria-label="Toggle tier entitlements"
                        >
                          {isExpanded ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                        </button>
                        <div className="flex-1">
                          <div className="flex items-center gap-2">
                            <span className="font-medium text-[rgb(var(--text-primary))]">{tier.displayName || tier.tierCode}</span>
                            <span className="text-xs font-mono text-[rgb(var(--text-muted))]">{tier.tierCode}</span>
                            {tier.system && <span className="text-[10px] px-1.5 py-0.5 rounded bg-brand-50 text-brand-700 dark:bg-brand-900/30 dark:text-brand-400">System</span>}
                            <span className={`text-[10px] px-1.5 py-0.5 rounded ${tier.enabled ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400' : 'bg-[rgb(var(--surface-3))] text-[rgb(var(--text-secondary))]'}`}>
                              {tier.enabled ? 'Enabled' : 'Disabled'}
                            </span>
                          </div>
                          {tier.description && <p className="text-xs text-[rgb(var(--text-muted))] mt-0.5">{tier.description}</p>}
                        </div>
                        {canManage && (
                          <div className="flex items-center gap-2">
                            <button
                              className="btn-secondary flex items-center gap-1 text-xs"
                              disabled={savingKey === `tier:${cat.code}/${tier.tierCode}`}
                              onClick={() => toggleTierEnabled(cat.code, tier)}
                            >
                              {tier.enabled ? <><X size={14} /> Disable</> : <><Check size={14} /> Enable</>}
                            </button>
                            {!tier.system && (
                              <button className="btn-danger flex items-center gap-1 text-xs" onClick={() => handleDeleteTier(cat.code, tier)}>
                                <Trash2 size={14} />
                              </button>
                            )}
                          </div>
                        )}
                      </div>

                      {isExpanded && (
                        <div className="border-t border-[rgb(var(--border-subtle))] p-3 bg-[rgb(var(--surface-2))] space-y-2">
                          <h4 className="text-xs font-semibold text-[rgb(var(--text-secondary))] uppercase tracking-wider">Entitlements</h4>
                          {entitlementDefs.length === 0 && (
                            <p className="text-sm text-[rgb(var(--text-muted))] italic">No entitlement definitions configured.</p>
                          )}
                          <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                            {entitlementDefs.map((def) => {
                              const hasOverride = Object.prototype.hasOwnProperty.call(values, def.key)
                              const value = hasOverride ? values[def.key] : def.defaultValue
                              return (
                                <EntitlementRow
                                  key={def.key}
                                  def={def}
                                  value={value}
                                  hasOverride={hasOverride}
                                  disabled={!canManage}
                                  saving={savingKey === `ent:${key}:${def.key}`}
                                  onSave={(v) => updateEntitlementValue(cat.code, tier.tierCode, def.key, v)}
                                  onReset={() => resetEntitlement(cat.code, tier.tierCode, def.key)}
                                />
                              )
                            })}
                          </div>
                        </div>
                      )}
                    </div>
                  )
                })}

                {canManage && (
                  <div className="flex items-end gap-2 pt-2">
                    <input
                      className="input flex-1"
                      placeholder="TIER_CODE"
                      value={newTierForm[cat.code]?.tierCode || ''}
                      onChange={(e) => setNewTierForm((s) => ({ ...s, [cat.code]: { ...(s[cat.code] || { tierCode: '', displayName: '', description: '' }), tierCode: e.target.value.toUpperCase() } }))}
                    />
                    <input
                      className="input flex-1"
                      placeholder="Display name"
                      value={newTierForm[cat.code]?.displayName || ''}
                      onChange={(e) => setNewTierForm((s) => ({ ...s, [cat.code]: { ...(s[cat.code] || { tierCode: '', displayName: '', description: '' }), displayName: e.target.value } }))}
                    />
                    <button
                      className="btn-primary flex items-center gap-1 text-sm"
                      disabled={savingKey === `new-tier:${cat.code}`}
                      onClick={() => handleCreateTier(cat.code)}
                    >
                      <Plus size={14} /> Add tier
                    </button>
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

interface EntitlementRowProps {
  def: EntitlementRecord
  value: string
  hasOverride: boolean
  disabled: boolean
  saving: boolean
  onSave: (v: string) => void
  onReset: () => void
}

function EntitlementRow({ def, value, hasOverride, disabled, saving, onSave, onReset }: EntitlementRowProps) {
  const [draft, setDraft] = useState(value)
  const [editing, setEditing] = useState(false)
  useEffect(() => { setDraft(value) }, [value])

  const asBoolean = def.valueType === 'BOOLEAN'
  const display = asBoolean ? (value === 'true' ? 'On' : 'Off') : value

  const handleBooleanToggle = () => {
    if (disabled) return
    onSave(value === 'true' ? 'false' : 'true')
  }

  return (
    <div className="rounded border border-[rgb(var(--border-subtle))] bg-[rgb(var(--surface-1))] p-3">
      <div className="flex items-start gap-2">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5 flex-wrap">
            <span className="text-sm font-medium text-[rgb(var(--text-primary))] truncate">{def.displayName || def.key}</span>
            {hasOverride ? (
              <span className="text-[10px] px-1 py-0.5 rounded bg-brand-50 text-brand-700 dark:bg-brand-900/30 dark:text-brand-400">Override</span>
            ) : (
              <span className="chip chip--neutral">Default</span>
            )}
          </div>
          <p className="text-[11px] font-mono text-[rgb(var(--text-muted))] truncate">{def.key}</p>
          {def.description && <p className="text-[11px] text-[rgb(var(--text-muted))] mt-0.5 line-clamp-2">{def.description}</p>}
        </div>
        {asBoolean ? (
          <button
            type="button"
            className={`shrink-0 px-2 py-1 rounded text-xs font-medium ${value === 'true' ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400' : 'bg-[rgb(var(--surface-3))] text-[rgb(var(--text-secondary))]'} ${disabled ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer hover:brightness-95'}`}
            disabled={disabled || saving}
            onClick={handleBooleanToggle}
          >
            {saving ? <Loader2 size={12} className="animate-spin" /> : display}
          </button>
        ) : editing ? (
          <div className="flex items-center gap-1">
            <input
              className="input py-1 px-2 text-xs w-24"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
            />
            <button
              className="btn-primary p-1"
              disabled={saving || disabled}
              onClick={() => { onSave(draft); setEditing(false) }}
              aria-label="Save"
            >
              {saving ? <Loader2 size={12} className="animate-spin" /> : <Save size={12} />}
            </button>
            <button className="btn-secondary p-1" onClick={() => { setDraft(value); setEditing(false) }} aria-label="Cancel"><X size={12} /></button>
          </div>
        ) : (
          <div className="flex items-center gap-1">
            <span className="font-mono text-sm text-[rgb(var(--text-primary))]">{display}</span>
            {!disabled && (
              <button className="p-1 text-[rgb(var(--text-muted))] hover:text-[rgb(var(--text-secondary))]" onClick={() => setEditing(true)} aria-label="Edit">
                <Pencil size={12} />
              </button>
            )}
          </div>
        )}
      </div>
      {hasOverride && !disabled && (
        <div className="mt-2 text-right">
          <button className="text-[10px] text-[rgb(var(--text-muted))] hover:text-[rgb(var(--text-primary))]" onClick={onReset}>
            Reset to default
          </button>
        </div>
      )}
    </div>
  )
}
