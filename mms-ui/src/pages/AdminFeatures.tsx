import { useState, useMemo } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Loader2, Shield, Plus, X, Pencil, Trash2, Check, Settings,
  ShieldCheck, Search,
} from 'lucide-react'
import { notify } from '../components/Toast'
import {
  listFeatures, getFeatureMatrix, setRoleFeatures, createFeature,
  updateFeature, deleteFeature, type FeatureRecord,
  listRoles, createRole, updateRole, deleteRole, type RoleRecord,
} from '../api/client'

function roleLabel(r: string) { return r.replace(/^ROLE_/, '') }

export default function AdminFeatures() {
  const queryClient = useQueryClient()
  const [editingFeature, setEditingFeature] = useState<FeatureRecord | null>(null)
  const [creating, setCreating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [tab, setTab] = useState<'system' | 'roles'>('system')
  const [editingRole, setEditingRole] = useState<RoleRecord | null>(null)
  const [creatingRole, setCreatingRole] = useState(false)
  const [search, setSearch] = useState('')

  const { data: features = [], isLoading: featLoading } = useQuery({
    queryKey: ['admin-features'],
    queryFn: listFeatures,
  })

  const { data: matrix = {} } = useQuery({
    queryKey: ['feature-matrix'],
    queryFn: getFeatureMatrix,
  })

  const { data: roles = [] } = useQuery({
    queryKey: ['admin-roles'],
    queryFn: listRoles,
  })

  const roleNames = roles.map((r) => r.name).sort()
  const matrixRoles = roleNames

  const assignedFeatures = useMemo(() => {
    if (tab !== 'system') return features
    return features.filter(f =>
      matrixRoles.some(role => (matrix[role] || []).includes(f.code))
    )
  }, [features, matrix, matrixRoles, tab])

  const filteredFeatures = useMemo(() => {
    if (!search.trim()) return assignedFeatures
    const q = search.toLowerCase()
    return assignedFeatures.filter(f =>
      f.name.toLowerCase().includes(q) ||
      f.code.toLowerCase().includes(q) ||
      (f.category || '').toLowerCase().includes(q)
    )
  }, [assignedFeatures, search])

  const reload = () => {
    queryClient.invalidateQueries({ queryKey: ['admin-features'] })
    queryClient.invalidateQueries({ queryKey: ['feature-matrix'] })
    queryClient.invalidateQueries({ queryKey: ['admin-roles'] })
  }

  const toggleFeatureForRole = async (role: string, code: string) => {
    const current = new Set<string>(matrix[role] || [])
    if (current.has(code)) current.delete(code)
    else current.add(code)
    try {
      await setRoleFeatures(role, Array.from(current))
      reload()
    } catch (err) {
      notify.error((err as { message?: string })?.message || 'Failed to update.')
    }
  }

  const handleDeleteFeature = async (f: FeatureRecord) => {
    if (!confirm(`Permanently delete "${f.name}"? It will be removed from all roles.`)) return
    try {
      await deleteFeature(f.id)
      notify.success('Feature deleted.')
      setEditingFeature(null)
      setCreating(false)
      reload()
    } catch (err) {
      notify.error((err as { message?: string })?.message || 'Something went wrong.')
    }
  }

  const handleUnassignFromRoles = async (f: FeatureRecord, roleList: string[]) => {
    try {
      for (const role of roleList) {
        const current = matrix[role] || []
        if (current.includes(f.code)) {
          await setRoleFeatures(role, current.filter((c: string) => c !== f.code))
        }
      }
      notify.success(`Removed "${f.name}" from system roles.`)
      reload()
    } catch (err) {
      notify.error((err as { message?: string })?.message || 'Something went wrong.')
    }
  }

  const systemCount = features.filter(f => roleNames.some(r => (matrix[r] || []).includes(f.code))).length

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div className="space-y-1">
          <h1 className="text-2xl font-bold text-[rgb(var(--text-primary))] flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-brand-50 dark:bg-brand-900/30">
              <Settings size={20} className="text-brand-600 dark:text-brand-400" />
            </div>
            Administration
          </h1>
          <p className="text-sm text-[rgb(var(--text-muted))]">
            Manage roles, features, and access control. Tier limits live under Governance.
          </p>
        </div>
        <div className="flex gap-2">
          {tab === 'system' && (
            <button type="button" className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-lg bg-brand-600 text-white text-sm font-medium shadow-sm hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900 transition-colors"
              onClick={() => { setCreating(true); setEditingFeature(null) }}>
              <Plus size={16} /> New feature
            </button>
          )}
          {tab === 'roles' && (
            <button type="button" className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-lg bg-brand-600 text-white text-sm font-medium shadow-sm hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900 transition-colors"
              onClick={() => { setCreatingRole(true); setEditingRole(null) }}>
              <Plus size={16} /> New role
            </button>
          )}
        </div>
      </div>

      {/* Tabs */}
      <div className="border-b border-[rgb(var(--border-subtle))]">
        <nav className="flex gap-0 -mb-px">
          {([
            { key: 'system' as const, label: 'Access', count: systemCount, icon: ShieldCheck },
            { key: 'roles' as const, label: 'Roles', count: roles.length, icon: Shield },
          ]).map(({ key, label, count, icon: Icon }) => (
            <button key={key} type="button" onClick={() => { setTab(key); setSearch('') }}
              className={`group relative flex items-center gap-2 px-5 py-3 text-sm font-medium border-b-2 transition-all ${
                tab === key
                  ? 'border-brand-600 text-brand-600 dark:text-brand-400 dark:border-brand-400'
                  : 'border-transparent text-[rgb(var(--text-muted))] hover:text-[rgb(var(--text-secondary))] hover:border-[rgb(var(--border-strong))]'
              }`}>
              <Icon size={15} className={tab === key ? 'text-brand-500 dark:text-brand-400' : 'text-gray-400 group-hover:text-gray-500'} />
              {label}
              <span className={`inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 rounded-full text-[11px] font-semibold ${
                tab === key
                  ? 'bg-brand-100 text-brand-700 dark:bg-brand-900/50 dark:text-brand-300'
                  : 'bg-[rgb(var(--surface-3))] text-[rgb(var(--text-secondary))]'
              }`}>
                {count}
              </span>
            </button>
          ))}
        </nav>
      </div>

      {/* Feature Matrix */}
      {tab === 'system' && (
        <>
          {featLoading ? (
            <div className="card p-16 flex flex-col items-center justify-center gap-3">
              <Loader2 size={28} className="animate-spin text-brand-600" />
              <p className="text-sm text-gray-500">Loading features...</p>
            </div>
          ) : assignedFeatures.length === 0 ? (
            <div className="card p-16 text-center">
              <div className="inline-flex p-4 rounded-full bg-[rgb(var(--surface-3))] mb-4">
                <Shield size={32} className="text-gray-400" />
              </div>
              <p className="text-[rgb(var(--text-muted))] font-medium">
                No features assigned to any role
              </p>
              <p className="text-sm text-[rgb(var(--text-muted))] mt-1">
                Create a feature and assign it to a role to see it here.
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              {/* Search bar */}
              {assignedFeatures.length > 5 && (
                <div className="relative max-w-xs">
                  <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
                  <input type="text" value={search} onChange={e => setSearch(e.target.value)}
                    placeholder="Filter features..."
                    className="input pl-9 py-2 text-sm" />
                </div>
              )}

              <div className="card overflow-hidden border border-[rgb(var(--border-subtle))] shadow-sm">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="bg-[rgb(var(--surface-2))] border-b border-[rgb(var(--border-subtle))]">
                        <th className="px-4 py-3.5 text-left text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider min-w-[220px]">Feature</th>
                        <th className="px-4 py-3.5 text-left text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider">Category</th>
                        {matrixRoles.map((r) => (
                          <th key={r} className="px-3 py-3.5 text-center text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider min-w-[90px]">
                            {roleLabel(r)}
                          </th>
                        ))}
                        <th className="px-4 py-3.5 text-right text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider w-[100px]">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[rgb(var(--border-subtle))]/60">
                      {filteredFeatures.map((f) => {
                        const grantedCount = matrixRoles.filter(r => (matrix[r] || []).includes(f.code)).length
                        return (
                          <tr key={f.id} className="group hover:bg-[rgb(var(--surface-2)/0.8)] transition-colors">
                            <td className="px-4 py-3.5">
                              <div className="font-medium text-[rgb(var(--text-primary))] text-[13px]">{f.name}</div>
                              {f.description && (
                                <div className="text-xs text-[rgb(var(--text-muted))] mt-0.5 line-clamp-1">{f.description}</div>
                              )}
                            </td>
                            <td className="px-4 py-3.5">
                              {f.category ? (
                                <span className="chip chip--neutral">
                                  {f.category}
                                </span>
                              ) : (
                                <span className="text-[rgb(var(--text-muted))]">--</span>
                              )}
                            </td>
                            {matrixRoles.map((role) => {
                              const granted = (matrix[role] || []).includes(f.code)
                              return (
                                <td key={role} className="px-3 py-3.5 text-center">
                                  <button type="button" onClick={() => toggleFeatureForRole(role, f.code)}
                                    className={`w-7 h-7 rounded-md border-2 inline-flex items-center justify-center transition-all duration-150 ${
                                      granted
                                        ? 'bg-brand-600 border-brand-600 text-white shadow-sm shadow-brand-200 dark:shadow-none'
                                        : 'border-[rgb(var(--border-strong))] text-transparent hover:border-brand-400 dark:hover:border-brand-500'
                                    }`}
                                    aria-label={`${granted ? 'Revoke' : 'Grant'} ${f.name} for ${roleLabel(role)}`}>
                                    <Check size={14} strokeWidth={2.5} />
                                  </button>
                                </td>
                              )
                            })}
                            <td className="px-4 py-3.5 text-right">
                              <div className="inline-flex gap-0.5">
                                <button type="button" onClick={() => { setEditingFeature(f); setCreating(false) }}
                                  className="p-1.5 rounded-md text-[rgb(var(--text-muted))] hover:text-brand-600 hover:bg-brand-50 dark:hover:text-brand-400 dark:hover:bg-brand-900/30 transition-colors"
                                  title="Edit feature">
                                  <Pencil size={14} />
                                </button>
                                <button type="button" onClick={() => handleUnassignFromRoles(f, matrixRoles)}
                                  className="p-1.5 rounded-md text-[rgb(var(--text-muted))] hover:text-amber-600 hover:bg-amber-50 dark:hover:text-amber-400 dark:hover:bg-amber-900/30 transition-colors"
                                  title={`Remove from ${grantedCount} ${tab} role${grantedCount !== 1 ? 's' : ''}`}>
                                  <X size={14} />
                                </button>
                              </div>
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
                {filteredFeatures.length === 0 && search && (
                  <div className="px-4 py-8 text-center text-sm text-gray-400">
                    No features match "{search}"
                  </div>
                )}
              </div>

              <p className="text-xs text-[rgb(var(--text-muted))]">
                {assignedFeatures.length} feature{assignedFeatures.length !== 1 ? 's' : ''} assigned to {tab} roles.
                Use the pencil to edit, or X to remove from all {tab} roles.
              </p>
            </div>
          )}
        </>
      )}

      {/* Roles Tab */}
      {tab === 'roles' && (
        <div className="card overflow-hidden border border-[rgb(var(--border-subtle))] shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[rgb(var(--surface-2))] border-b border-[rgb(var(--border-subtle))]">
                  <th className="px-4 py-3.5 text-left text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider">Role</th>
                  <th className="px-4 py-3.5 text-left text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider">Display name</th>
                  <th className="px-4 py-3.5 text-left text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider">Description</th>
                  <th className="px-4 py-3.5 text-left text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider">Type</th>
                  <th className="px-4 py-3.5 text-right text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider w-[100px]">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[rgb(var(--border-subtle))]/60">
                {roles.map((r) => {
                  const featureCount = (matrix[r.name] || []).length
                  const canDelete = !r.system && featureCount === 0
                  const deleteTitle = r.system
                    ? 'System role -- cannot be deleted'
                    : featureCount > 0
                      ? `${featureCount} feature(s) assigned -- remove them first`
                      : 'Delete role'
                  return (
                    <tr key={r.id} className="group hover:bg-[rgb(var(--surface-2)/0.8)] transition-colors">
                      <td className="px-4 py-3.5">
                        <code className="text-xs font-mono px-1.5 py-0.5 rounded bg-[rgb(var(--surface-3))] text-[rgb(var(--text-secondary))]">
                          {r.name}
                        </code>
                      </td>
                      <td className="px-4 py-3.5 text-[13px] font-medium text-[rgb(var(--text-primary))]">
                        {r.displayName || roleLabel(r.name)}
                      </td>
                      <td className="px-4 py-3.5 text-[rgb(var(--text-muted))] text-xs max-w-[240px] truncate">
                        {r.description || <span className="text-[rgb(var(--text-muted))]">--</span>}
                      </td>
                      <td className="px-4 py-3.5">
                        <div className="flex items-center gap-2.5">
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-brand-50 text-brand-700 dark:bg-brand-900/30 dark:text-brand-400 ring-1 ring-brand-200 dark:ring-brand-800">
                            <ShieldCheck size={10} /> System
                          </span>
                          {featureCount > 0 && (
                            <span className="text-[11px] text-[rgb(var(--text-muted))] tabular-nums">
                              {featureCount} feature{featureCount > 1 ? 's' : ''}
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="px-4 py-3.5 text-right">
                        <div className="inline-flex gap-0.5">
                          <button type="button" onClick={() => { setEditingRole(r); setCreatingRole(false) }}
                            className="p-1.5 rounded-md text-[rgb(var(--text-muted))] hover:text-brand-600 hover:bg-brand-50 dark:hover:text-brand-400 dark:hover:bg-brand-900/30 transition-colors"
                            title="Edit role">
                            <Pencil size={14} />
                          </button>
                          <button type="button"
                            disabled={!canDelete}
                            onClick={async () => {
                              if (!confirm(`Delete role "${r.displayName || roleLabel(r.name)}"?`)) return
                              try { await deleteRole(r.id); notify.success('Role deleted.'); reload() }
                              catch (err) { notify.error((err as { message?: string })?.message || 'Delete failed.') }
                            }}
                            className="p-1.5 rounded-md text-[rgb(var(--text-muted))] hover:text-red-600 hover:bg-red-50 dark:hover:text-red-400 dark:hover:bg-red-900/30 disabled:opacity-20 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-[rgb(var(--text-muted))] transition-colors"
                            title={deleteTitle}>
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

      {/* Feature Modal */}
      {(creating || editingFeature) && (
        <FeatureFormModal
          feature={editingFeature}
          saving={saving}
          onClose={() => { setCreating(false); setEditingFeature(null) }}
          onDelete={editingFeature ? () => handleDeleteFeature(editingFeature) : undefined}
          onSave={async (data) => {
            setSaving(true)
            try {
              if (editingFeature) {
                await updateFeature(editingFeature.id, data)
                notify.success('Feature updated.')
              } else {
                await createFeature(data)
                notify.success('Feature created.')
              }
              setCreating(false); setEditingFeature(null)
              reload()
            } catch (err) {
              notify.error((err as { message?: string })?.message || 'Save failed.')
            } finally { setSaving(false) }
          }}
        />
      )}

      {/* Role Modal */}
      {(creatingRole || editingRole) && (
        <RoleFormModal
          role={editingRole}
          saving={saving}
          onClose={() => { setCreatingRole(false); setEditingRole(null) }}
          onSave={async (data) => {
            setSaving(true)
            try {
              if (editingRole) {
                await updateRole(editingRole.id, data)
                notify.success('Role updated.')
              } else {
                await createRole(data as { name: string; displayName?: string; description?: string })
                notify.success('Role created.')
              }
              setCreatingRole(false); setEditingRole(null)
              reload()
            } catch (err) { notify.error((err as { message?: string })?.message || 'Save failed.') }
            finally { setSaving(false) }
          }}
        />
      )}
    </div>
  )
}

/* ── Feature Form Modal ──────────────────────────────────────────── */

function FeatureFormModal({ feature, saving, onClose, onSave, onDelete }: {
  feature: FeatureRecord | null
  saving: boolean
  onClose: () => void
  onSave: (data: Partial<FeatureRecord>) => void
  onDelete?: () => void
}) {
  const [code, setCode] = useState(feature?.code || '')
  const [name, setName] = useState(feature?.name || '')
  const [description, setDescription] = useState(feature?.description || '')
  const [category, setCategory] = useState(feature?.category || '')
  const [icon, setIcon] = useState(feature?.icon || '')
  const isEdit = Boolean(feature)

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!code.trim() || !name.trim()) { notify.error('Code and name are required.'); return }
    onSave({ code: code.trim(), name: name.trim(), description, category, icon, enabled: true })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true">
      <div className="absolute inset-0 bg-gray-950/95 backdrop-blur-md" onClick={() => !saving && onClose()} />
      <div className="relative bg-[rgb(var(--surface-1))] rounded-2xl w-full max-w-lg shadow-2xl border border-[rgb(var(--border-subtle))] animate-in fade-in zoom-in-95 duration-200">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-[rgb(var(--border-subtle))]">
          <div className="flex items-center gap-3">
            <div className={`p-2 rounded-lg ${isEdit ? 'bg-brand-50 dark:bg-brand-900/30' : 'bg-emerald-50 dark:bg-emerald-900/30'}`}>
              {isEdit ? <Pencil size={16} className="text-brand-600 dark:text-brand-400" /> : <Plus size={16} className="text-emerald-600 dark:text-emerald-400" />}
            </div>
            <div>
              <h2 className="text-base font-semibold text-[rgb(var(--text-primary))]">
                {isEdit ? 'Edit Feature' : 'New Feature'}
              </h2>
              <p className="text-xs text-[rgb(var(--text-muted))]">
                {isEdit ? 'Update feature details' : 'Define a new access feature'}
              </p>
            </div>
          </div>
          <button type="button" onClick={onClose} disabled={saving}
            className="p-2 rounded-lg text-[rgb(var(--text-muted))] hover:text-[rgb(var(--text-secondary))] hover:bg-[rgb(var(--surface-3))] transition-colors">
            <X size={18} />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={submit} className="px-6 py-5 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div className="col-span-2 sm:col-span-1">
              <label className="block text-xs font-medium text-[rgb(var(--text-secondary))] mb-1.5">
                Code <span className="text-red-500">*</span>
              </label>
              <input className="input font-mono text-sm" value={code} onChange={(e) => setCode(e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, '_'))}
                disabled={isEdit} placeholder="e.g. view_reports" required />
              {isEdit && <p className="text-[11px] text-gray-400 mt-1">Immutable after creation</p>}
            </div>
            <div className="col-span-2 sm:col-span-1">
              <label className="block text-xs font-medium text-[rgb(var(--text-secondary))] mb-1.5">
                Name <span className="text-red-500">*</span>
              </label>
              <input className="input text-sm" value={name} onChange={(e) => setName(e.target.value)}
                placeholder="e.g. View Reports" required />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-[rgb(var(--text-secondary))] mb-1.5">Description</label>
            <input className="input text-sm" value={description} onChange={(e) => setDescription(e.target.value)}
              placeholder="Brief description of what this feature controls" />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-[rgb(var(--text-secondary))] mb-1.5">Category</label>
              <input className="input text-sm" value={category} onChange={(e) => setCategory(e.target.value)}
                placeholder="e.g. Reports" />
            </div>
            <div>
              <label className="block text-xs font-medium text-[rgb(var(--text-secondary))] mb-1.5">Icon</label>
              <input className="input text-sm" value={icon} onChange={(e) => setIcon(e.target.value)}
                placeholder="e.g. BarChart3" />
            </div>
          </div>
        </form>

        {/* Footer */}
        <div className="flex items-center justify-between px-6 py-4 border-t border-[rgb(var(--border-subtle))] bg-[rgb(var(--surface-2))] rounded-b-xl">
          <div>
            {isEdit && onDelete && (
              <button type="button" disabled={saving} onClick={onDelete}
                className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-medium text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-900/20 transition-colors">
                <Trash2 size={13} /> Delete Feature
              </button>
            )}
          </div>
          <div className="flex gap-2">
            <button type="button" disabled={saving} onClick={onClose}
              className="px-4 py-2 rounded-lg text-sm font-medium text-[rgb(var(--text-secondary))] bg-[rgb(var(--surface-1))] border border-[rgb(var(--border-strong))] hover:bg-[rgb(var(--surface-3))] transition-colors">
              Cancel
            </button>
            <button type="submit" disabled={saving}
              onClick={(e) => {
                e.preventDefault()
                if (!code.trim() || !name.trim()) { notify.error('Code and name are required.'); return }
                onSave({ code: code.trim(), name: name.trim(), description, category, icon, enabled: true })
              }}
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium text-white bg-brand-600 hover:bg-brand-700 shadow-sm disabled:opacity-50 transition-colors">
              {saving ? <><Loader2 size={16} className="animate-spin" /> Saving...</> : isEdit ? 'Save changes' : 'Create feature'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

/* ── Role Form Modal ─────────────────────────────────────────────── */

function RoleFormModal({ role, saving, onClose, onSave }: {
  role: RoleRecord | null
  saving: boolean
  onClose: () => void
  onSave: (data: { name?: string; displayName?: string; description?: string; category?: string }) => void
}) {
  const isEdit = Boolean(role)
  const [name, setName] = useState(role?.name?.replace(/^ROLE_/, '') || '')
  const [displayName, setDisplayName] = useState(role?.displayName || '')
  const [description, setDescription] = useState(role?.description || '')

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!isEdit && !name.trim()) { notify.error('Role name is required.'); return }
    onSave({
      ...(!isEdit ? { name: name.trim() } : {}),
      displayName: displayName.trim() || undefined,
      description: description.trim() || undefined,
      category: 'System',
    })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true">
      <div className="absolute inset-0 bg-gray-950/95 backdrop-blur-md" onClick={() => !saving && onClose()} />
      <div className="relative bg-[rgb(var(--surface-1))] rounded-2xl w-full max-w-lg shadow-2xl border border-[rgb(var(--border-subtle))] animate-in fade-in zoom-in-95 duration-200">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-[rgb(var(--border-subtle))]">
          <div className="flex items-center gap-3">
            <div className={`p-2 rounded-lg ${isEdit ? 'bg-brand-50 dark:bg-brand-900/30' : 'bg-emerald-50 dark:bg-emerald-900/30'}`}>
              {isEdit ? <Pencil size={16} className="text-brand-600 dark:text-brand-400" /> : <Shield size={16} className="text-emerald-600 dark:text-emerald-400" />}
            </div>
            <div>
              <h2 className="text-base font-semibold text-[rgb(var(--text-primary))]">
                {isEdit ? 'Edit Role' : 'New Role'}
              </h2>
              <p className="text-xs text-[rgb(var(--text-muted))]">
                {isEdit ? 'Update role configuration' : 'Define a new access role'}
              </p>
            </div>
          </div>
          <button type="button" onClick={onClose} disabled={saving}
            className="p-2 rounded-lg text-[rgb(var(--text-muted))] hover:text-[rgb(var(--text-secondary))] hover:bg-[rgb(var(--surface-3))] transition-colors">
            <X size={18} />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={submit} className="px-6 py-5 space-y-4">
          <div>
            <label className="block text-xs font-medium text-[rgb(var(--text-secondary))] mb-1.5">
              Role Code {!isEdit && <span className="text-red-500">*</span>}
            </label>
            <div className="flex items-center">
              <span className="inline-flex items-center px-3 py-2 rounded-l-lg border border-r-0 border-[rgb(var(--border-strong))] bg-[rgb(var(--surface-2))] text-sm text-[rgb(var(--text-muted))] font-mono">
                ROLE_
              </span>
              <input className="input rounded-l-none font-mono uppercase text-sm flex-1" value={name}
                onChange={(e) => setName(e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '_'))}
                disabled={isEdit} placeholder="e.g. ANALYST" required={!isEdit} />
            </div>
            {isEdit && <p className="text-[11px] text-gray-400 mt-1">Immutable after creation</p>}
          </div>

          <div>
            <label className="block text-xs font-medium text-[rgb(var(--text-secondary))] mb-1.5">Display name</label>
            <input className="input text-sm" value={displayName} onChange={(e) => setDisplayName(e.target.value)}
              placeholder="e.g. Data Analyst" />
          </div>

          <div>
            <label className="block text-xs font-medium text-[rgb(var(--text-secondary))] mb-1.5">Description</label>
            <input className="input text-sm" value={description} onChange={(e) => setDescription(e.target.value)}
              placeholder="What this role grants access to" />
          </div>

          {/*
            Roles now always belong to the System category. The Platform category
            (subscription-tier roles) was removed — tier-level access is governed
            by Membership Governance entitlements, not roles. We still send
            category="System" on save so the backend document stays well-formed.
          */}
        </form>

        {/* Footer */}
        <div className="flex items-center justify-end gap-2 px-6 py-4 border-t border-[rgb(var(--border-subtle))] bg-[rgb(var(--surface-2))] rounded-b-xl">
          <button type="button" disabled={saving} onClick={onClose}
            className="px-4 py-2 rounded-lg text-sm font-medium text-[rgb(var(--text-secondary))] bg-[rgb(var(--surface-1))] border border-[rgb(var(--border-strong))] hover:bg-[rgb(var(--surface-3))] transition-colors">
            Cancel
          </button>
          <button type="submit" disabled={saving}
            onClick={(e) => {
              e.preventDefault()
              if (!isEdit && !name.trim()) { notify.error('Role name is required.'); return }
              onSave({
                ...(!isEdit ? { name: name.trim() } : {}),
                displayName: displayName.trim() || undefined,
                description: description.trim() || undefined,
                category: 'System',
              })
            }}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium text-white bg-brand-600 hover:bg-brand-700 shadow-sm disabled:opacity-50 transition-colors">
            {saving ? <><Loader2 size={16} className="animate-spin" /> Saving...</> : isEdit ? 'Save changes' : 'Create role'}
          </button>
        </div>
      </div>
    </div>
  )
}
