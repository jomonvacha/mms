import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Globe, Plus, Trash2, Loader2, Languages, MapPin, Clock,
  ToggleLeft, ToggleRight,
} from 'lucide-react'
import { notify } from '../components/Toast'
import { listLocaleOptions, createLocaleOption, updateLocaleOption, deleteLocaleOption, type LocaleOption } from '../api/client'

type LocaleType = 'LANGUAGE' | 'COUNTRY' | 'TIMEZONE'

const TAB_CONFIG: { key: LocaleType; label: string; icon: typeof Globe; placeholder: string }[] = [
  { key: 'LANGUAGE', label: 'Languages', icon: Languages, placeholder: 'e.g. en' },
  { key: 'COUNTRY', label: 'Countries', icon: MapPin, placeholder: 'e.g. US' },
  { key: 'TIMEZONE', label: 'Timezones', icon: Clock, placeholder: 'e.g. America/New_York' },
]

export default function AdminLocale() {
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<LocaleType>('LANGUAGE')
  const [newCode, setNewCode] = useState('')
  const [newLabel, setNewLabel] = useState('')
  const [adding, setAdding] = useState(false)

  const { data: options, isLoading } = useQuery({
    queryKey: ['locale-options', activeTab],
    queryFn: () => listLocaleOptions(activeTab),
  })

  const { data: langCount } = useQuery({ queryKey: ['locale-options', 'LANGUAGE'], queryFn: () => listLocaleOptions('LANGUAGE'), select: (d) => d?.length ?? 0 })
  const { data: countryCount } = useQuery({ queryKey: ['locale-options', 'COUNTRY'], queryFn: () => listLocaleOptions('COUNTRY'), select: (d) => d?.length ?? 0 })
  const { data: tzCount } = useQuery({ queryKey: ['locale-options', 'TIMEZONE'], queryFn: () => listLocaleOptions('TIMEZONE'), select: (d) => d?.length ?? 0 })
  const counts: Record<LocaleType, number> = { LANGUAGE: langCount ?? 0, COUNTRY: countryCount ?? 0, TIMEZONE: tzCount ?? 0 }

  const reload = () => {
    queryClient.invalidateQueries({ queryKey: ['locale-options'] })
  }

  const currentTab = TAB_CONFIG.find(t => t.key === activeTab)!
  const enabledCount = (options || []).filter(o => o.enabled).length

  const handleAdd = async () => {
    if (!newCode.trim() || !newLabel.trim()) { notify.error('Code and label are required.'); return }
    setAdding(true)
    try {
      await createLocaleOption({ type: activeTab, code: newCode.trim(), label: newLabel.trim(), sortOrder: (options?.length || 0) + 1 })
      setNewCode(''); setNewLabel('')
      reload()
      notify.success('Option added.')
    } catch (err) { notify.error((err as { message?: string })?.message || 'Something went wrong.') }
    finally { setAdding(false) }
  }

  const handleToggle = async (opt: LocaleOption) => {
    try {
      await updateLocaleOption(opt.id, { enabled: !opt.enabled })
      reload()
      notify.success(`${opt.label} ${opt.enabled ? 'disabled' : 'enabled'}.`)
    } catch (err) { notify.error((err as { message?: string })?.message || 'Something went wrong.') }
  }

  const handleDelete = async (opt: LocaleOption) => {
    if (!confirm(`Delete "${opt.label}" (${opt.code})? This cannot be undone.`)) return
    try {
      await deleteLocaleOption(opt.id)
      reload()
      notify.success('Option removed.')
    } catch (err) { notify.error((err as { message?: string })?.message || 'Something went wrong.') }
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="space-y-1">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2.5">
          <div className="p-2 rounded-lg bg-brand-50 dark:bg-brand-900/30">
            <Globe size={20} className="text-brand-600 dark:text-brand-400" />
          </div>
          Locale Management
        </h1>
        <p className="text-sm text-gray-500 dark:text-gray-400">
          Manage available languages, countries, and timezones for user profiles.
        </p>
      </div>

      {/* Tabs */}
      <div className="border-b border-gray-200 dark:border-gray-700">
        <nav className="flex gap-0 -mb-px">
          {TAB_CONFIG.map(({ key, label, icon: Icon }) => (
            <button key={key} type="button" onClick={() => { setActiveTab(key); setNewCode(''); setNewLabel('') }}
              className={`group relative flex items-center gap-2 px-5 py-3 text-sm font-medium border-b-2 transition-all ${
                activeTab === key
                  ? 'border-brand-600 text-brand-600 dark:text-brand-400 dark:border-brand-400'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 dark:text-gray-400 dark:hover:text-gray-300'
              }`}>
              <Icon size={15} className={activeTab === key ? 'text-brand-500 dark:text-brand-400' : 'text-gray-400 group-hover:text-gray-500'} />
              {label}
              <span className={`inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 rounded-full text-[11px] font-semibold ${
                activeTab === key
                  ? 'bg-brand-100 text-brand-700 dark:bg-brand-900/50 dark:text-brand-300'
                  : 'bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-400'
              }`}>
                {counts[key]}
              </span>
            </button>
          ))}
        </nav>
      </div>

      {/* Add new inline form */}
      <div className="flex items-end gap-3">
        <div className="flex-1">
          <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1.5">Code</label>
          <input type="text" value={newCode} onChange={(e) => setNewCode(e.target.value)}
            placeholder={currentTab.placeholder}
            className="input text-sm font-mono" maxLength={50}
            onKeyDown={(e) => e.key === 'Enter' && handleAdd()} />
        </div>
        <div className="flex-1">
          <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1.5">Display Label</label>
          <input type="text" value={newLabel} onChange={(e) => setNewLabel(e.target.value)}
            placeholder="e.g. English" className="input text-sm" maxLength={100}
            onKeyDown={(e) => e.key === 'Enter' && handleAdd()} />
        </div>
        <button type="button" onClick={handleAdd} disabled={adding || !newCode.trim() || !newLabel.trim()}
          className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-lg bg-brand-600 text-white text-sm font-medium shadow-sm hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900 disabled:opacity-50 disabled:cursor-not-allowed transition-colors">
          {adding ? <Loader2 size={15} className="animate-spin" /> : <Plus size={15} />} Add
        </button>
      </div>

      {/* Table */}
      {isLoading ? (
        <div className="card p-16 flex flex-col items-center justify-center gap-3">
          <Loader2 size={28} className="animate-spin text-brand-600" />
          <p className="text-sm text-gray-500">Loading {currentTab.label.toLowerCase()}...</p>
        </div>
      ) : (
        <div className="space-y-3">
          <div className="card overflow-hidden border border-gray-200 dark:border-gray-700 shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-gray-50 dark:bg-gray-800/80 border-b border-gray-200 dark:border-gray-700">
                    <th className="px-4 py-3.5 text-left text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider min-w-[120px]">Code</th>
                    <th className="px-4 py-3.5 text-left text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">Label</th>
                    <th className="px-4 py-3.5 text-center text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider w-[100px]">Status</th>
                    <th className="px-4 py-3.5 text-right text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider w-[100px]">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 dark:divide-gray-700/60">
                  {(options || []).map((opt) => (
                    <tr key={opt.id} className="hover:bg-gray-50/80 dark:hover:bg-gray-800/40 transition-colors">
                      <td className="px-4 py-3.5">
                        <code className="text-xs font-mono px-1.5 py-0.5 rounded bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300">
                          {opt.code}
                        </code>
                      </td>
                      <td className="px-4 py-3.5 text-[13px] font-medium text-gray-900 dark:text-gray-100">{opt.label}</td>
                      <td className="px-4 py-3.5 text-center">
                        <button type="button" onClick={() => handleToggle(opt)}
                          title={opt.enabled ? 'Click to disable' : 'Click to enable'}
                          className="inline-flex items-center justify-center transition-colors">
                          {opt.enabled ? (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-emerald-50 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400 ring-1 ring-emerald-200 dark:ring-emerald-800 cursor-pointer hover:bg-emerald-100 dark:hover:bg-emerald-900/50">
                              <ToggleRight size={12} /> Enabled
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-400 ring-1 ring-gray-200 dark:ring-gray-600 cursor-pointer hover:bg-gray-200 dark:hover:bg-gray-600">
                              <ToggleLeft size={12} /> Disabled
                            </span>
                          )}
                        </button>
                      </td>
                      <td className="px-4 py-3.5 text-right">
                        <button type="button" onClick={() => handleDelete(opt)}
                          className="p-1.5 rounded-md text-gray-400 hover:text-red-600 hover:bg-red-50 dark:text-gray-500 dark:hover:text-red-400 dark:hover:bg-red-900/30 transition-colors"
                          title="Delete">
                          <Trash2 size={14} />
                        </button>
                      </td>
                    </tr>
                  ))}
                  {(!options || options.length === 0) && (
                    <tr>
                      <td colSpan={4} className="px-4 py-16 text-center">
                        <div className="inline-flex p-4 rounded-full bg-gray-100 dark:bg-gray-800 mb-3">
                          <currentTab.icon size={24} className="text-gray-400" />
                        </div>
                        <p className="text-sm text-gray-500 dark:text-gray-400">
                          No {currentTab.label.toLowerCase()} configured yet.
                        </p>
                        <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">
                          Add one above to get started.
                        </p>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {(options?.length ?? 0) > 0 && (
            <p className="text-xs text-gray-400 dark:text-gray-500">
              {options!.length} {currentTab.label.toLowerCase()} configured, {enabledCount} enabled.
              Click the status badge to toggle availability.
            </p>
          )}
        </div>
      )}
    </div>
  )
}
