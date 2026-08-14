import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Loader2, Users, Search, ChevronLeft, ChevronRight, ShieldCheck,
  ToggleLeft, ToggleRight, X, KeyRound, ShieldAlert, Trash2, Pencil, Check, Lock, Globe, Smartphone,
  UserCog, Crown, User, Mail,
} from 'lucide-react'
import { notify } from '../components/Toast'
import {
  listAdminUsers, updateAdminUserRoles, updateAdminUserStatus, updateAdminUserProvider,
  updateAdminUserProfile, setAdminUserMembership,
  setAdminUserPassword, sendAdminUserResetLink,
  deleteAdminUser, listMembers, deleteMember,
  listRoles, listCategories,
  type AdminUserResponse, type MemberRecord, type RoleRecord, type CategoryRecord,
} from '../api/client'
import { useAuth } from '../hooks/useAuth'

// Defensive filter for deprecated Platform Access roles. The backend seeder
// strips these from users and deletes the documents on startup; this set is a
// safety net for clients that load the UI before that cleanup has run.
// TODO remove after one release cycle.
const LEGACY_TIER_ROLES = new Set(['ROLE_BASIC', 'ROLE_PREMIUM', 'ROLE_ENTERPRISE'])

function ProviderBadge({ provider }: { provider?: string }) {
  if (!provider || provider === 'LOCAL') {
    return (
      <span className="chip chip--neutral">
        <KeyRound size={10} /> Local
      </span>
    )
  }
  return (
    <span className="chip chip--success">
      <Globe size={10} /> {provider.charAt(0) + provider.slice(1).toLowerCase()}
    </span>
  )
}

function StatusBadge({ active }: { active?: boolean }) {
  return active
    ? <span className="chip chip--success">Active</span>
    : <span className="chip chip--danger">Inactive</span>
}

function RoleChip({ role, className = '' }: { role: string; className?: string }) {
  const label = role.replace(/^ROLE_/, '')
  const tone = role === 'ROLE_ADMIN'
    ? 'chip--danger'
    : role === 'ROLE_MODERATOR' || role === 'ROLE_MANAGER'
      ? 'chip--warn'
      : 'chip--brand'
  return <span className={`chip ${tone} ${className}`}>{label}</span>
}

function tierTone(tierCode: string) {
  const t = tierCode.toUpperCase()
  if (t === 'FREE') return 'chip--neutral'
  if (t.includes('MAX') || t.includes('ENTERPRISE')) return 'bg-purple-50 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400'
  return 'chip--warn'
}

function TierBadge({ member, categories }: { member?: MemberRecord; categories?: CategoryRecord[] }) {
  if (!member?.categoryCode || !member?.tierCode) {
    return <span className="text-[rgb(var(--text-muted))]">--</span>
  }
  const cat = categories?.find((c) => c.code === member.categoryCode)
  const tier = cat?.tiers?.find((t) => t.tierCode === member.tierCode)
  const catLabel = (cat?.displayName || member.categoryCode).toUpperCase()
  const tierLabel = (tier?.displayName || member.tierCode).toUpperCase()
  return (
    <span className={`chip ${tierTone(member.tierCode)}`}>
      <Crown size={10} />
      <span className="opacity-60">{catLabel}</span>
      <span className="opacity-30">/</span>
      <span>{tierLabel}</span>
    </span>
  )
}

interface UserRow extends AdminUserResponse {
  member?: MemberRecord
}

export default function AdminUsers() {
  const queryClient = useQueryClient()
  const { user: currentUser } = useAuth()
  const [page, setPage] = useState(0)
  const [size] = useState(20)
  const [sortBy, setSortBy] = useState<'username' | 'email' | 'createdAt'>('username')
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc')
  const [search, setSearch] = useState('')
  const [editingUser, setEditingUser] = useState<UserRow | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [bulkDeleting, setBulkDeleting] = useState(false)
  const [savingId, setSavingId] = useState<string | null>(null)

  const { data: allRoles = [] } = useQuery({ queryKey: ['roles-list'], queryFn: listRoles })
  const { data: categories = [] } = useQuery({ queryKey: ['governance-categories'], queryFn: listCategories })

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['admin-users', page, size, sortBy, sortDir],
    queryFn: async () => {
      const [usersPage, memberList] = await Promise.all([
        listAdminUsers({ page, size, sortBy, sortDir }),
        listMembers({ size: 500 }),
      ])
      const membersByUserId = new Map<string, MemberRecord>()
      for (const m of memberList) {
        const uid = m.user?.id || m.userId
        if (uid) membersByUserId.set(uid, m)
      }
      const rows: UserRow[] = (usersPage.content || []).map((u) => ({
        ...u,
        member: membersByUserId.get(u.id),
      }))
      return { ...usersPage, content: rows }
    },
  })

  const filtered = useMemo(() => {
    const list = data?.content || []
    const q = search.trim().toLowerCase()
    if (!q) return list
    return list.filter((u) =>
      u.username?.toLowerCase().includes(q) ||
      u.email?.toLowerCase().includes(q) ||
      `${u.firstName ?? ''} ${u.lastName ?? ''}`.toLowerCase().includes(q)
    )
  }, [data, search])

  const isAdmin = currentUser?.roles?.includes('ROLE_ADMIN') ?? false
  const reload = () => { setSelected(new Set()); return queryClient.invalidateQueries({ queryKey: ['admin-users'] }) }

  const toggleSelect = (id: string) => setSelected((prev) => { const n = new Set(prev); n.has(id) ? n.delete(id) : n.add(id); return n })
  const toggleAll = () => {
    const selectable = filtered.filter((u) => u.id !== currentUser?.id)
    if (selected.size >= selectable.length) setSelected(new Set())
    else setSelected(new Set(selectable.map((u) => u.id)))
  }

  const onBulkDelete = async () => {
    setSavingId('bulk')
    try {
      const targets = filtered.filter((u) => selected.has(u.id))
      for (const u of targets) {
        if (u.member?.id) { try { await deleteMember(u.member.id) } catch (_) {} }
        await deleteAdminUser(u.id)
      }
      notify.success(`${targets.length} user${targets.length > 1 ? 's' : ''} deleted.`)
      setBulkDeleting(false)
      await reload()
    } catch (err) { notify.error((err as { message?: string })?.message || 'Delete failed.') }
    finally { setSavingId(null) }
  }

  const onToggleStatus = async (u: UserRow) => {
    if (u.id === currentUser?.id) { notify.error("You cannot deactivate your own account."); return }
    setSavingId(u.id)
    try {
      await updateAdminUserStatus(u.id, !u.active)
      await reload()
      notify.success(`${u.username} ${u.active ? 'deactivated' : 'activated'}.`)
    } catch (err) { notify.error((err as { message?: string })?.message || 'Failed.') }
    finally { setSavingId(null) }
  }

  const toggleSort = (col: 'username' | 'email' | 'createdAt') => {
    if (sortBy === col) setSortDir((d) => d === 'asc' ? 'desc' : 'asc')
    else { setSortBy(col); setSortDir('asc') }
  }

  const sortArrow = (col: string) => sortBy === col ? (sortDir === 'asc' ? ' \u2191' : ' \u2193') : ''
  const allChecked = selected.size > 0 && selected.size >= filtered.filter((u) => u.id !== currentUser?.id).length

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div className="space-y-1">
          <h1 className="text-2xl font-bold text-[rgb(var(--text-primary))] flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-brand-50 dark:bg-brand-900/30">
              <Users size={20} className="text-brand-600 dark:text-brand-400" />
            </div>
            User Administration
          </h1>
          <p className="text-sm text-[rgb(var(--text-muted))]">
            Manage user accounts, system roles, and membership tier assignments.
          </p>
        </div>
      </div>

      {/* Toolbar: search + bulk actions */}
      <div className="flex items-center gap-3 flex-wrap">
        <div className="relative w-64">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-[rgb(var(--text-muted))]" />
          <input type="text" value={search} onChange={(e) => setSearch(e.target.value)}
            placeholder="Filter users..." className="input pl-9 py-2 text-sm" aria-label="Filter users" />
        </div>
        {selected.size > 0 && (
          <>
            <div className="h-5 w-px bg-[rgb(var(--border-subtle))]" />
            <span className="text-sm font-medium text-brand-600 dark:text-brand-400">
              {selected.size} selected
            </span>
            <button type="button" onClick={() => setBulkDeleting(true)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-900/20 transition-colors">
              <Trash2 size={13} /> Delete
            </button>
            <button type="button" onClick={() => setSelected(new Set())}
              className="text-xs text-[rgb(var(--text-muted))] hover:text-[rgb(var(--text-secondary))] transition-colors">
              Clear
            </button>
          </>
        )}
      </div>

      {/* Loading */}
      {isLoading && (
        <div className="card p-16 flex flex-col items-center justify-center gap-3">
          <Loader2 size={28} className="animate-spin text-brand-600" />
          <p className="text-sm text-[rgb(var(--text-muted))]">Loading users...</p>
        </div>
      )}

      {/* Error */}
      {isError && (
        <div role="alert" className="card p-5 border border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/10">
          <div className="flex items-start gap-3">
            <ShieldAlert size={18} className="text-red-500 mt-0.5 flex-shrink-0" aria-hidden="true" />
            <div>
              <p className="text-sm font-medium text-red-800 dark:text-red-300">Failed to load users</p>
              <p className="text-xs mt-0.5 text-red-600/80 dark:text-red-400/80">{(error as Error)?.message}</p>
            </div>
          </div>
        </div>
      )}

      {/* Table */}
      {!isLoading && !isError && data && (
        <div className="space-y-3">
          <div className="card overflow-hidden border border-[rgb(var(--border-subtle))] shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-[rgb(var(--surface-2))] border-b border-[rgb(var(--border-subtle))]">
                    {isAdmin && (
                      <th className="pl-4 pr-2 py-3 w-10">
                        <button type="button" onClick={toggleAll}
                          className={`w-[18px] h-[18px] rounded border-[1.5px] inline-flex items-center justify-center transition-all ${
                            allChecked
                              ? 'bg-brand-600 border-brand-600 text-white'
                              : 'border-[rgb(var(--border-strong))] text-transparent hover:border-brand-500'
                          }`} aria-label="Select all">
                          <Check size={11} strokeWidth={3} />
                        </button>
                      </th>
                    )}
                    <th scope="col" aria-sort={sortBy === 'username' ? (sortDir === 'asc' ? 'ascending' : 'descending') : 'none'}
                      className="px-4 py-3 text-left text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider cursor-pointer select-none hover:text-[rgb(var(--text-primary))] transition-colors"
                      onClick={() => toggleSort('username')}>
                      User{sortArrow('username')}
                    </th>
                    <th scope="col" aria-sort={sortBy === 'email' ? (sortDir === 'asc' ? 'ascending' : 'descending') : 'none'}
                      className="px-4 py-3 text-left text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider cursor-pointer select-none hover:text-[rgb(var(--text-primary))] transition-colors"
                      onClick={() => toggleSort('email')}>
                      Email{sortArrow('email')}
                    </th>
                    <th scope="col" className="px-4 py-3 text-left text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider">Roles</th>
                    <th scope="col" className="px-4 py-3 text-left text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider">Tier</th>
                    <th scope="col" className="px-4 py-3 text-left text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider">Provider</th>
                    <th scope="col" className="px-4 py-3 text-left text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider">Status</th>
                    <th scope="col" className="px-4 py-3 text-right text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[rgb(var(--border-subtle))]">
                  {filtered.length === 0 && (
                    <tr>
                      <td colSpan={isAdmin ? 8 : 7} className="px-4 py-16 text-center">
                        <div className="inline-flex p-3 rounded-full bg-[rgb(var(--surface-3))] mb-3">
                          <Users size={22} className="text-[rgb(var(--text-muted))]" />
                        </div>
                        <p className="text-sm text-[rgb(var(--text-muted))]">No users match your search.</p>
                      </td>
                    </tr>
                  )}
                  {filtered.map((u) => {
                    const isSelf = u.id === currentUser?.id
                    return (
                      <tr key={u.id} className={`hover:bg-[rgb(var(--surface-2)/0.8)] transition-colors ${selected.has(u.id) ? 'bg-brand-50/40 dark:bg-brand-900/10' : ''}`}>
                        {isAdmin && (
                          <td className="pl-4 pr-2 py-3">
                            {!isSelf ? (
                              <button type="button" onClick={() => toggleSelect(u.id)}
                                className={`w-[18px] h-[18px] rounded border-[1.5px] inline-flex items-center justify-center transition-all ${
                                  selected.has(u.id)
                                    ? 'bg-brand-600 border-brand-600 text-white'
                                    : 'border-[rgb(var(--border-strong))] text-transparent hover:border-brand-500'
                                }`} aria-label={`Select ${u.username}`}>
                                <Check size={11} strokeWidth={3} />
                              </button>
                            ) : <span className="w-[18px] h-[18px] block" />}
                          </td>
                        )}
                        <td className="px-4 py-3">
                          <div className="font-medium text-[13px] text-[rgb(var(--text-primary))]">
                            {u.firstName || u.lastName ? `${u.firstName ?? ''} ${u.lastName ?? ''}`.trim() : u.username}
                            {isSelf && <span className="ml-1.5 text-[10px] text-brand-500 dark:text-brand-400">(you)</span>}
                          </div>
                          <div className="text-xs text-[rgb(var(--text-muted))]">@{u.username}</div>
                        </td>
                        <td className="px-4 py-3 text-[13px] text-[rgb(var(--text-secondary))]">{u.email}</td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-1">
                            {(u.roles || []).filter((r) => !LEGACY_TIER_ROLES.has(r)).map((r) => (
                              <RoleChip key={r} role={r} />
                            ))}
                          </div>
                        </td>
                        <td className="px-4 py-3"><TierBadge member={u.member} categories={categories} /></td>
                        <td className="px-4 py-3"><ProviderBadge provider={u.provider} /></td>
                        <td className="px-4 py-3"><StatusBadge active={u.active} /></td>
                        <td className="px-4 py-3 text-right">
                          <div className="inline-flex items-center gap-0.5">
                            <button type="button" onClick={() => setEditingUser(u)} disabled={!isAdmin}
                              className="p-1.5 rounded-md text-[rgb(var(--text-muted))] hover:text-brand-600 hover:bg-brand-50 dark:hover:text-brand-400 dark:hover:bg-brand-900/30 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                              title="Edit user" aria-label={`Edit ${u.username}`}>
                              <Pencil size={14} />
                            </button>
                            <button type="button" onClick={() => onToggleStatus(u)}
                              disabled={!isAdmin || savingId === u.id || isSelf}
                              className="p-1.5 rounded-md text-[rgb(var(--text-muted))] hover:text-amber-600 hover:bg-amber-50 dark:hover:text-amber-400 dark:hover:bg-amber-900/30 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                              title={isSelf ? "Can't deactivate yourself" : u.active ? 'Deactivate' : 'Activate'}>
                              {savingId === u.id ? <Loader2 size={14} className="animate-spin" /> : u.active ? <ToggleRight size={14} /> : <ToggleLeft size={14} />}
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

          {/* Footer */}
          <div className="flex items-center justify-between">
            <p className="text-xs text-[rgb(var(--text-muted))]">
              {filtered.length} of {data.totalElements} user{data.totalElements === 1 ? '' : 's'}.
              Page {data.number + 1} of {Math.max(1, data.totalPages)}.
            </p>
            <div className="flex items-center gap-1">
              <button type="button" disabled={data.first} onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="p-1.5 rounded-lg border border-[rgb(var(--border-subtle))] text-[rgb(var(--text-muted))] hover:bg-[rgb(var(--surface-3))] disabled:opacity-30 disabled:cursor-not-allowed transition-colors">
                <ChevronLeft size={15} />
              </button>
              <button type="button" disabled={data.last} onClick={() => setPage((p) => p + 1)}
                className="p-1.5 rounded-lg border border-[rgb(var(--border-subtle))] text-[rgb(var(--text-muted))] hover:bg-[rgb(var(--surface-3))] disabled:opacity-30 disabled:cursor-not-allowed transition-colors">
                <ChevronRight size={15} />
              </button>
            </div>
          </div>
        </div>
      )}

      {editingUser && <EditUserModal user={editingUser} allRoles={allRoles} categories={categories} onClose={() => setEditingUser(null)} onSaved={() => { setEditingUser(null); reload() }} />}
      {bulkDeleting && (
        <BulkDeleteModal
          count={selected.size}
          users={filtered.filter((u) => selected.has(u.id))}
          saving={savingId === 'bulk'}
          onCancel={() => setBulkDeleting(false)}
          onConfirm={onBulkDelete}
        />
      )}
    </div>
  )
}

/* ── Bulk Delete Modal ───────────────────────────────────────────── */

function BulkDeleteModal({ count, users, saving, onCancel, onConfirm }: {
  count: number; users: UserRow[]; saving: boolean; onCancel: () => void; onConfirm: () => void
}) {
  const withMembers = users.filter((u) => u.member?.id).length
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true">
      <div className="absolute inset-0 bg-gray-950/95 backdrop-blur-md" onClick={() => !saving && onCancel()} />
      <div className="relative bg-[rgb(var(--surface-1))] rounded-2xl w-full max-w-md shadow-2xl border border-[rgb(var(--border-subtle))]">
        <div className="flex items-center gap-3 px-6 py-4 border-b border-[rgb(var(--border-subtle))]">
          <div className="p-2 rounded-lg bg-red-50 dark:bg-red-900/30">
            <Trash2 size={16} className="text-red-600 dark:text-red-400" />
          </div>
          <div>
            <h2 className="text-base font-semibold text-[rgb(var(--text-primary))]">
              Delete {count} user{count > 1 ? 's' : ''}
            </h2>
            <p className="text-xs text-[rgb(var(--text-muted))]">This action cannot be undone</p>
          </div>
        </div>

        <div className="px-6 py-4 space-y-3">
          <p className="text-sm text-[rgb(var(--text-secondary))]">
            Permanently delete the selected user{count > 1 ? 's' : ''}?
            {withMembers > 0 && ` ${withMembers} membership record${withMembers > 1 ? 's' : ''} will also be removed.`}
          </p>
          {count <= 10 && (
            <div className="max-h-40 overflow-auto space-y-1">
              {users.map((u) => (
                <div key={u.id} className="text-xs text-[rgb(var(--text-secondary))] flex items-center gap-2 px-3 py-2 bg-[rgb(var(--surface-2))] rounded-lg border border-[rgb(var(--border-subtle))]">
                  <span className="font-medium">@{u.username}</span>
                  <span className="text-[rgb(var(--text-muted))] truncate">{u.email}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="flex items-center justify-end gap-2 px-6 py-4 border-t border-[rgb(var(--border-subtle))] bg-[rgb(var(--surface-2))] rounded-b-xl">
          <button type="button" disabled={saving} onClick={onCancel}
            className="px-4 py-2 rounded-lg text-sm font-medium text-[rgb(var(--text-secondary))] bg-[rgb(var(--surface-1))] border border-[rgb(var(--border-strong))] hover:bg-[rgb(var(--surface-3))] transition-colors">
            Cancel
          </button>
          <button type="button" disabled={saving} onClick={onConfirm}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium text-white bg-red-600 hover:bg-red-700 shadow-sm disabled:opacity-50 transition-colors">
            {saving ? <><Loader2 size={14} className="animate-spin" /> Deleting...</> : `Delete ${count} user${count > 1 ? 's' : ''}`}
          </button>
        </div>
      </div>
    </div>
  )
}

/* ── Edit User Modal ─────────────────────────────────────────────── */

function EditUserModal({ user, allRoles, categories, onClose, onSaved }: {
  user: UserRow; allRoles: RoleRecord[]; categories: CategoryRecord[];
  onClose: () => void; onSaved: () => void | Promise<void>
}) {
  const [tab, setTab] = useState<'profile' | 'roles' | 'provider' | 'security'>('profile')
  const initialRoles = useMemo(
    () => new Set((user.roles || []).filter((r) => !LEGACY_TIER_ROLES.has(r))),
    [user.roles],
  )
  const [selectedRoles, setSelectedRoles] = useState<Set<string>>(initialRoles)
  const [categoryCode, setCategoryCode] = useState<string>(user.member?.categoryCode || '')
  const [tierCode, setTierCode] = useState<string>(user.member?.tierCode || '')
  const [provider, setProvider] = useState<string>(user.provider || 'LOCAL')
  const [firstName, setFirstName] = useState<string>(user.firstName || '')
  const [lastName, setLastName] = useState<string>(user.lastName || '')
  const [email, setEmail] = useState<string>(user.email || '')
  const [phoneNumber, setPhoneNumber] = useState<string>(user.phoneNumber || '')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [sendingReset, setSendingReset] = useState(false)
  const [saving, setSaving] = useState(false)

  const isFederated = (user.provider === 'GOOGLE' || user.provider === 'APPLE')

  const toggleRole = (r: string) => setSelectedRoles((prev) => { const n = new Set(prev); n.has(r) ? n.delete(r) : n.add(r); return n })

  const activeCategories = useMemo(() => categories.filter((c) => c.enabled), [categories])
  const activeTiers = useMemo(() => {
    const cat = categories.find((c) => c.code === categoryCode)
    return (cat?.tiers || []).filter((t) => t.enabled)
  }, [categories, categoryCode])

  const rolesChanged = useMemo(() => {
    if (initialRoles.size !== selectedRoles.size) return true
    for (const r of initialRoles) if (!selectedRoles.has(r)) return true
    return false
  }, [initialRoles, selectedRoles])

  const tierChanged =
    Boolean(categoryCode) && Boolean(tierCode) &&
    (categoryCode !== (user.member?.categoryCode || '') || tierCode !== (user.member?.tierCode || ''))

  const saveRolesAndTier = async () => {
    if (selectedRoles.size === 0) { notify.error('Must have at least one role.'); return }
    if ((categoryCode || tierCode) && (!categoryCode || !tierCode)) {
      notify.error('Select both a membership category and tier.'); return
    }
    if (!rolesChanged && !tierChanged) { await onSaved(); return }
    setSaving(true)
    try {
      const ops: Promise<unknown>[] = []
      if (rolesChanged) ops.push(updateAdminUserRoles(user.id, Array.from(selectedRoles)))
      if (tierChanged) ops.push(setAdminUserMembership(user.id, categoryCode, tierCode))
      await Promise.all(ops)
      notify.success('User updated.')
      await onSaved()
    } catch (err) { notify.error((err as { message?: string })?.message || 'Failed.') }
    finally { setSaving(false) }
  }

  const saveProfile = async () => {
    const payload: { firstName?: string; lastName?: string; email?: string; phoneNumber?: string } = {}
    const trimmedFirst = firstName.trim()
    const trimmedLast = lastName.trim()
    const trimmedEmail = email.trim()
    const trimmedPhone = phoneNumber.trim()
    if (!trimmedFirst) { notify.error('First name cannot be empty.'); return }
    if (!trimmedLast) { notify.error('Last name cannot be empty.'); return }
    if (!trimmedEmail) { notify.error('Email cannot be empty.'); return }
    if (trimmedFirst !== (user.firstName || '')) payload.firstName = trimmedFirst
    if (trimmedLast !== (user.lastName || '')) payload.lastName = trimmedLast
    if (trimmedEmail !== (user.email || '')) payload.email = trimmedEmail
    if (trimmedPhone !== (user.phoneNumber || '')) payload.phoneNumber = trimmedPhone
    if (Object.keys(payload).length === 0) { await onSaved(); return }
    setSaving(true)
    try {
      await updateAdminUserProfile(user.id, payload)
      notify.success('Profile updated.')
      await onSaved()
    } catch (err) { notify.error((err as { message?: string })?.message || 'Failed.') }
    finally { setSaving(false) }
  }

  const saveProvider = async () => {
    setSaving(true)
    try {
      await updateAdminUserProvider(user.id, provider)
      notify.success('Provider updated.')
      await onSaved()
    } catch (err) { notify.error((err as { message?: string })?.message || 'Failed.') }
    finally { setSaving(false) }
  }

  const savePassword = async () => {
    if (isFederated) { notify.error(`Password is managed by ${user.provider}.`); return }
    if (newPassword.length < 8) { notify.error('Password must be at least 8 characters.'); return }
    if (!/[A-Za-z]/.test(newPassword) || !/\d/.test(newPassword)) {
      notify.error('Password must contain at least one letter and one number.'); return
    }
    if (newPassword !== confirmPassword) { notify.error('Passwords do not match.'); return }
    setSaving(true)
    try {
      await setAdminUserPassword(user.id, newPassword)
      notify.success('Password updated.')
      setNewPassword(''); setConfirmPassword('')
    } catch (err) { notify.error((err as { message?: string })?.message || 'Failed.') }
    finally { setSaving(false) }
  }

  const sendResetLink = async () => {
    if (isFederated) { notify.error(`User signs in via ${user.provider} — no local password.`); return }
    setSendingReset(true)
    try {
      await sendAdminUserResetLink(user.id)
      notify.success('Password reset link sent.')
    } catch (err) { notify.error((err as { message?: string })?.message || 'Failed.') }
    finally { setSendingReset(false) }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="edit-user-title">
      <div className="absolute inset-0 bg-gray-950/95 backdrop-blur-md" onClick={() => !saving && onClose()} />
      <div className="relative bg-[rgb(var(--surface-1))] rounded-2xl w-full max-w-lg shadow-2xl border border-[rgb(var(--border-subtle))]">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-[rgb(var(--border-subtle))]">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-brand-50 dark:bg-brand-900/30">
              <UserCog size={16} className="text-brand-600 dark:text-brand-400" aria-hidden="true" />
            </div>
            <div>
              <h2 id="edit-user-title" className="text-base font-semibold text-[rgb(var(--text-primary))]">Edit User</h2>
              <p className="text-xs text-[rgb(var(--text-muted))]">@{user.username} &middot; {user.email}</p>
            </div>
          </div>
          <button type="button" onClick={() => !saving && onClose()} disabled={saving}
            className="p-2 rounded-lg text-[rgb(var(--text-muted))] hover:text-[rgb(var(--text-secondary))] hover:bg-[rgb(var(--surface-3))] transition-colors" aria-label="Close">
            <X size={18} />
          </button>
        </div>

        {/* Tabs */}
        <div className="flex gap-0 px-6 border-b border-[rgb(var(--border-subtle))]">
          {([
            { key: 'profile' as const, label: 'Profile', icon: User },
            { key: 'roles' as const, label: 'Roles & Tier', icon: KeyRound },
            { key: 'security' as const, label: 'Security', icon: Lock },
            { key: 'provider' as const, label: 'Provider', icon: ShieldCheck },
          ]).map(({ key, label, icon: Icon }) => (
            <button key={key} type="button" onClick={() => setTab(key)}
              className={`flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium border-b-2 -mb-px transition-colors ${
                tab === key
                  ? 'border-brand-600 text-brand-600 dark:text-brand-400 dark:border-brand-400'
                  : 'border-transparent text-[rgb(var(--text-muted))] hover:text-[rgb(var(--text-secondary))]'
              }`}>
              <Icon size={14} /> {label}
            </button>
          ))}
        </div>

        {/* Content */}
        <div className="px-6 py-5 max-h-[60vh] overflow-y-auto">
          {tab === 'profile' && (
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-[11px] font-medium text-[rgb(var(--text-muted))] mb-1.5">First name</label>
                  <input type="text" value={firstName} onChange={(e) => setFirstName(e.target.value)}
                    className="input text-sm" maxLength={50} />
                </div>
                <div>
                  <label className="block text-[11px] font-medium text-[rgb(var(--text-muted))] mb-1.5">Last name</label>
                  <input type="text" value={lastName} onChange={(e) => setLastName(e.target.value)}
                    className="input text-sm" maxLength={50} />
                </div>
              </div>
              <div>
                <label className="block text-[11px] font-medium text-[rgb(var(--text-muted))] mb-1.5">Email</label>
                <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                  disabled={isFederated}
                  className="input text-sm disabled:opacity-60 disabled:cursor-not-allowed" maxLength={50} />
                {isFederated && (
                  <p className="mt-1 text-[11px] text-[rgb(var(--text-muted))] flex items-center gap-1">
                    <Lock size={10} /> Managed by {user.provider === 'GOOGLE' ? 'Google' : 'Apple'} — change at the identity provider.
                  </p>
                )}
              </div>
              <div>
                <label className="block text-[11px] font-medium text-[rgb(var(--text-muted))] mb-1.5">Phone number</label>
                <input type="tel" value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)}
                  placeholder="+1 (555) 555-5555"
                  className="input text-sm" maxLength={15} />
              </div>
              <div className="pt-2 grid grid-cols-2 gap-3 text-[11px] text-[rgb(var(--text-muted))]">
                <div>
                  <span className="uppercase tracking-wider font-semibold">Username</span>
                  <p className="mt-0.5 text-[rgb(var(--text-secondary))] font-mono">@{user.username}</p>
                </div>
                <div>
                  <span className="uppercase tracking-wider font-semibold">User ID</span>
                  <p className="mt-0.5 text-[rgb(var(--text-secondary))] font-mono truncate" title={user.id}>{user.id}</p>
                </div>
              </div>
            </div>
          )}

          {tab === 'roles' && (() => {
            const systemRoles = allRoles.filter((r) => r.category !== 'Platform' && !LEGACY_TIER_ROLES.has(r.name))
            return (
              <div className="space-y-6">
                <div>
                  <h4 className="flex items-center gap-1.5 text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider mb-2.5">
                    <ShieldCheck size={12} /> System Roles
                  </h4>
                  <div className="space-y-1.5">
                    {systemRoles.map((r) => {
                      const checked = selectedRoles.has(r.name)
                      return (
                        <button key={r.name} type="button" onClick={() => toggleRole(r.name)}
                          className={`w-full flex items-center gap-3 p-3 rounded-lg border text-left transition-all ${
                            checked
                              ? 'border-brand-500 bg-brand-50/50 dark:bg-brand-900/20'
                              : 'border-[rgb(var(--border-subtle))] hover:border-[rgb(var(--border-strong))]'
                          }`}>
                          <div className={`w-5 h-5 rounded border-[1.5px] inline-flex items-center justify-center flex-shrink-0 transition-all ${
                            checked ? 'bg-brand-600 border-brand-600 text-white' : 'border-[rgb(var(--border-strong))] text-transparent'
                          }`}>
                            <Check size={11} strokeWidth={3} />
                          </div>
                          <RoleChip role={r.name} className="min-w-[88px] flex-shrink-0" />
                          <div className="flex-1 min-w-0">
                            <p className="text-xs font-medium text-[rgb(var(--text-secondary))]">{r.displayName || r.name}</p>
                            {r.description && <p className="text-[11px] text-[rgb(var(--text-muted))] mt-0.5 truncate">{r.description}</p>}
                          </div>
                        </button>
                      )
                    })}
                  </div>
                </div>

                <div>
                  <h4 className="flex items-center gap-1.5 text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider mb-2.5">
                    <Crown size={12} /> Membership Tier
                  </h4>
                  {activeCategories.length === 0 ? (
                    <p className="text-xs text-[rgb(var(--text-muted))] italic">No enabled categories configured in Membership Governance.</p>
                  ) : (
                    <div className="space-y-3">
                      <div>
                        <label className="block text-[11px] font-medium text-[rgb(var(--text-muted))] mb-1.5">Category</label>
                        <div className="flex flex-wrap gap-1.5">
                          {activeCategories.map((c) => {
                            const active = categoryCode === c.code
                            return (
                              <button key={c.code} type="button"
                                onClick={() => {
                                  if (c.code === categoryCode) return
                                  setCategoryCode(c.code)
                                  const sameCat = user.member?.categoryCode === c.code
                                  setTierCode(sameCat ? (user.member?.tierCode || '') : '')
                                }}
                                className={`px-3 py-1.5 rounded-md text-xs font-medium border transition-colors ${
                                  active
                                    ? 'bg-brand-600 border-brand-600 text-white'
                                    : 'bg-[rgb(var(--surface-1))] border-[rgb(var(--border-subtle))] text-[rgb(var(--text-secondary))] hover:border-brand-400 dark:hover:border-brand-500'
                                }`}>
                                {c.displayName || c.code}
                              </button>
                            )
                          })}
                        </div>
                      </div>
                      <div>
                        <label className="block text-[11px] font-medium text-[rgb(var(--text-muted))] mb-1.5">Tier</label>
                        {!categoryCode ? (
                          <p className="text-[11px] text-[rgb(var(--text-muted))]">Select a category first.</p>
                        ) : activeTiers.length === 0 ? (
                          <p className="text-[11px] text-[rgb(var(--text-muted))]">No enabled tiers in this category.</p>
                        ) : (
                          <div className="flex flex-wrap gap-1.5">
                            {activeTiers.map((t) => {
                              const active = tierCode === t.tierCode
                              return (
                                <button key={t.tierCode} type="button"
                                  onClick={() => setTierCode(t.tierCode)}
                                  className={`px-3 py-1.5 rounded-md text-xs font-medium border transition-colors ${
                                    active
                                      ? 'bg-amber-500 border-amber-500 text-white'
                                      : 'bg-[rgb(var(--surface-1))] border-[rgb(var(--border-subtle))] text-[rgb(var(--text-secondary))] hover:border-amber-400 dark:hover:border-amber-500'
                                  }`}>
                                  {t.displayName || t.tierCode}
                                </button>
                              )
                            })}
                          </div>
                        )}
                      </div>
                      <p className="text-[11px] text-[rgb(var(--text-muted))]">
                        One membership tier per user. Tiers are sourced from Membership Governance.
                      </p>
                    </div>
                  )}
                </div>
              </div>
            )
          })()}

          {tab === 'security' && (
            <div className="space-y-6">
              <div>
                <h4 className="flex items-center gap-1.5 text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider mb-3">
                  <Lock size={12} /> Set new password
                </h4>
                {isFederated ? (
                  <p className="text-xs text-[rgb(var(--text-muted))] italic p-3 rounded-lg border border-dashed border-[rgb(var(--border-subtle))]">
                    Password is managed by {user.provider === 'GOOGLE' ? 'Google' : 'Apple'}. This user signs in via their identity provider.
                  </p>
                ) : (
                  <div className="space-y-3">
                    <div>
                      <label className="block text-[11px] font-medium text-[rgb(var(--text-muted))] mb-1.5">New password</label>
                      <input type="password" autoComplete="new-password" value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        minLength={8} maxLength={64}
                        className="input text-sm" />
                      <p className="mt-1 text-[11px] text-[rgb(var(--text-muted))]">
                        At least 8 characters, with letters and numbers.
                      </p>
                    </div>
                    <div>
                      <label className="block text-[11px] font-medium text-[rgb(var(--text-muted))] mb-1.5">Confirm new password</label>
                      <input type="password" autoComplete="new-password" value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        minLength={8} maxLength={64}
                        className="input text-sm" />
                    </div>
                    <button type="button" onClick={savePassword}
                      disabled={saving || !newPassword || !confirmPassword}
                      className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-medium text-white bg-brand-600 hover:bg-brand-700 shadow-sm disabled:opacity-50 transition-colors">
                      {saving ? <Loader2 size={13} className="animate-spin" /> : <Lock size={13} />}
                      Update password
                    </button>
                    <p className="text-[11px] text-amber-600 dark:text-amber-400">
                      Share the new password with the user securely (out-of-band). They should change it after signing in.
                    </p>
                  </div>
                )}
              </div>

              <div className="pt-5 border-t border-[rgb(var(--border-subtle))]">
                <h4 className="flex items-center gap-1.5 text-xs font-semibold text-[rgb(var(--text-muted))] uppercase tracking-wider mb-3">
                  <Mail size={12} /> Send reset link
                </h4>
                {isFederated ? (
                  <p className="text-xs text-[rgb(var(--text-muted))] italic p-3 rounded-lg border border-dashed border-[rgb(var(--border-subtle))]">
                    Not applicable — federated account.
                  </p>
                ) : (
                  <div className="space-y-3">
                    <p className="text-xs text-[rgb(var(--text-muted))]">
                      Emails <span className="font-mono text-[rgb(var(--text-secondary))]">{user.email}</span> a single-use password reset link. The user sets their own password via the link.
                    </p>
                    <button type="button" onClick={sendResetLink} disabled={sendingReset}
                      className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-medium text-[rgb(var(--text-secondary))] bg-[rgb(var(--surface-1))] border border-[rgb(var(--border-strong))] hover:bg-[rgb(var(--surface-3))] disabled:opacity-50 transition-colors">
                      {sendingReset ? <Loader2 size={13} className="animate-spin" /> : <Mail size={13} />}
                      Send reset email
                    </button>
                  </div>
                )}
              </div>
            </div>
          )}

          {tab === 'provider' && (
            <div className="space-y-2">
              <p className="text-xs text-[rgb(var(--text-muted))] mb-2">
                Select how this user authenticates.
              </p>
              {[
                { v: 'LOCAL', l: 'Local', d: 'Username & password', Icon: Lock },
                { v: 'GOOGLE', l: 'Google', d: 'Google OAuth2 SSO', Icon: Globe },
                { v: 'APPLE', l: 'Apple', d: 'Apple ID SSO', Icon: Smartphone },
              ].map(({ v, l, d, Icon }) => {
                const active = provider === v
                return (
                  <button key={v} type="button" onClick={() => setProvider(v)}
                    className={`w-full flex items-center gap-3 p-3.5 rounded-lg border text-left transition-all ${
                      active
                        ? 'border-brand-500 bg-brand-50/50 dark:bg-brand-900/20'
                        : 'border-[rgb(var(--border-subtle))] hover:border-[rgb(var(--border-strong))]'
                    }`}>
                    <div className={`w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0 ${
                      active ? 'bg-brand-100 dark:bg-brand-900/40' : 'bg-[rgb(var(--surface-3))]'
                    }`}>
                      <Icon size={16} className={active ? 'text-brand-600 dark:text-brand-400' : 'text-[rgb(var(--text-muted))]'} />
                    </div>
                    <div className="flex-1">
                      <span className="text-sm font-medium text-[rgb(var(--text-primary))] block">{l}</span>
                      <span className="text-xs text-[rgb(var(--text-muted))]">{d}</span>
                    </div>
                    <div className={`w-5 h-5 rounded-full border-[1.5px] inline-flex items-center justify-center flex-shrink-0 transition-all ${
                      active ? 'bg-brand-600 border-brand-600 text-white' : 'border-[rgb(var(--border-strong))] text-transparent'
                    }`}>
                      <Check size={10} strokeWidth={3} />
                    </div>
                  </button>
                )
              })}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-2 px-6 py-4 border-t border-[rgb(var(--border-subtle))] bg-[rgb(var(--surface-2))] rounded-b-xl">
          <button type="button" onClick={onClose} disabled={saving}
            className="px-4 py-2 rounded-lg text-sm font-medium text-[rgb(var(--text-secondary))] bg-[rgb(var(--surface-1))] border border-[rgb(var(--border-strong))] hover:bg-[rgb(var(--surface-3))] transition-colors">
            {tab === 'security' ? 'Close' : 'Cancel'}
          </button>
          {tab !== 'security' && (
            <button type="button" disabled={saving}
              onClick={tab === 'profile' ? saveProfile : tab === 'roles' ? saveRolesAndTier : saveProvider}
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium text-white bg-brand-600 hover:bg-brand-700 shadow-sm disabled:opacity-50 transition-colors">
              {saving ? <><Loader2 size={14} className="animate-spin" /> Saving...</> : 'Save Changes'}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
