import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ShieldCheck, Plus, Trash2, Loader2, Check, X, Lock, ToggleLeft, ArrowUp, ArrowDown } from 'lucide-react'
import { notify } from '../components/Toast'
import { listEnforcementRules, createEnforcementRule, updateEnforcementRule, deleteEnforcementRule, type EnforcementRule } from '../api/client'

const CATEGORIES = [
  { value: 'security', label: 'Security', color: 'text-red-600 bg-red-50 dark:bg-red-900/30 dark:text-red-400' },
  { value: 'formatting', label: 'Formatting', color: 'text-blue-600 bg-blue-50 dark:bg-blue-900/30 dark:text-blue-400' },
  { value: 'compliance', label: 'Compliance', color: 'text-purple-600 bg-purple-50 dark:bg-purple-900/30 dark:text-purple-400' },
  { value: 'style', label: 'Style', color: 'text-amber-600 bg-amber-50 dark:bg-amber-900/30 dark:text-amber-400' },
  { value: 'behavior', label: 'Behavior', color: 'text-emerald-600 bg-emerald-50 dark:bg-emerald-900/30 dark:text-emerald-400' },
]

function CategoryBadge({ category }: { category: string }) {
  const cat = CATEGORIES.find(c => c.value === category)
  return (
    <span className={`text-[10px] px-1.5 py-0.5 rounded-full font-medium uppercase ${cat?.color || 'text-gray-500 bg-gray-100'}`}>
      {cat?.label || category}
    </span>
  )
}

function RuleRow({ rule, onToggleActive, onChangeTier, onDelete }: {
  rule: EnforcementRule
  onToggleActive: () => void
  onChangeTier: (tier: string) => void
  onDelete: () => void
}) {
  const isSystem = rule.tier === 'SYSTEM'
  return (
    <div className={`flex items-start gap-4 p-4 ${!rule.active ? 'opacity-40' : ''}`}>
      <div className={`mt-1 flex-shrink-0 w-8 h-8 rounded-lg flex items-center justify-center ${isSystem ? 'bg-red-50 dark:bg-red-900/20' : 'bg-brand-50 dark:bg-brand-900/20'}`}>
        {isSystem ? <Lock size={14} className="text-red-500" /> : <ToggleLeft size={14} className="text-brand-500" />}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm text-[rgb(var(--text-primary))] leading-relaxed">{rule.text}</p>
        <div className="flex items-center gap-2 mt-1.5">
          <CategoryBadge category={rule.category} />
          {!isSystem && (
            <span className="text-[10px] text-gray-400">{rule.enabledByDefault ? 'On by default' : 'Off by default'}</span>
          )}
        </div>
      </div>
      <div className="flex items-center gap-1 flex-shrink-0">
        <button type="button" onClick={() => onChangeTier(isSystem ? 'OPTIONAL' : 'SYSTEM')}
          title={isSystem ? 'Demote to optional' : 'Promote to system'}
          className="p-1.5 rounded-lg text-gray-400 hover:text-brand-600 hover:bg-brand-50 dark:hover:bg-brand-900/20 transition-colors">
          {isSystem ? <ArrowDown size={14} /> : <ArrowUp size={14} />}
        </button>
        <button type="button" onClick={onToggleActive}
          title={rule.active ? 'Deactivate' : 'Activate'}
          className={`p-1.5 rounded-lg transition-colors ${rule.active ? 'text-emerald-600 hover:bg-emerald-50 dark:hover:bg-emerald-900/20' : 'text-gray-400 hover:bg-[rgb(var(--surface-3))]'}`}>
          {rule.active ? <Check size={14} /> : <X size={14} />}
        </button>
        <button type="button" onClick={onDelete} title="Delete"
          className="p-1.5 rounded-lg text-gray-400 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors">
          <Trash2 size={14} />
        </button>
      </div>
    </div>
  )
}

export default function AdminRules() {
  const queryClient = useQueryClient()
  const [newText, setNewText] = useState('')
  const [newTier, setNewTier] = useState('OPTIONAL')
  const [newCategory, setNewCategory] = useState('behavior')
  const [adding, setAdding] = useState(false)
  const [showAdd, setShowAdd] = useState(false)

  const { data: rules, isLoading } = useQuery({
    queryKey: ['enforcement-rules'],
    queryFn: listEnforcementRules,
  })

  const reload = () => queryClient.invalidateQueries({ queryKey: ['enforcement-rules'] })

  const handleAdd = async () => {
    if (!newText.trim()) { notify.error('Rule text is required.'); return }
    setAdding(true)
    try {
      await createEnforcementRule({ text: newText.trim(), tier: newTier, category: newCategory })
      setNewText('')
      setShowAdd(false)
      reload()
      notify.success('Rule added.')
    } catch (err) { notify.error((err as { message?: string })?.message || 'Something went wrong.') }
    finally { setAdding(false) }
  }

  const handleToggleActive = async (rule: EnforcementRule) => {
    try { await updateEnforcementRule(rule.id, { active: !rule.active }); reload() }
    catch (err) { notify.error((err as { message?: string })?.message || 'Something went wrong.') }
  }

  const handleChangeTier = async (rule: EnforcementRule, tier: string) => {
    try {
      await updateEnforcementRule(rule.id, { tier }); reload()
      notify.success(tier === 'SYSTEM' ? 'Promoted to system rule.' : 'Demoted to optional rule.')
    } catch (err) { notify.error((err as { message?: string })?.message || 'Something went wrong.') }
  }

  const handleDelete = async (id: string) => {
    try { await deleteEnforcementRule(id); reload(); notify.success('Rule removed.') }
    catch (err) { notify.error((err as { message?: string })?.message || 'Something went wrong.') }
  }

  const systemRules = (rules || []).filter(r => r.tier === 'SYSTEM')
  const optionalRules = (rules || []).filter(r => r.tier === 'OPTIONAL')

  return (
    <div className="space-y-8">
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div className="space-y-1">
          <h1 className="text-2xl font-bold text-[rgb(var(--text-primary))] flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-brand-50 dark:bg-brand-900/30">
              <ShieldCheck size={20} className="text-brand-600 dark:text-brand-400" />
            </div>
            Enforcement Rules
          </h1>
          <p className="text-sm text-[rgb(var(--text-muted))]">
            AI behavior rules applied to all personas across the platform.
          </p>
        </div>
        <button type="button" onClick={() => setShowAdd(!showAdd)}
          className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-lg bg-brand-600 text-white text-sm font-medium shadow-sm hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900 transition-colors">
          <Plus size={16} /> New rule
        </button>
      </div>

      {/* Add new rule (collapsible) */}
      {showAdd && (
        <div className="card p-5 space-y-4">
          <h3 className="text-sm font-semibold text-[rgb(var(--text-primary))]">Add Enforcement Rule</h3>
          <textarea value={newText} onChange={(e) => setNewText(e.target.value)}
            placeholder="Enter the rule text that will be injected into every persona's system prompt..."
            className="input text-sm min-h-[80px]" maxLength={500} autoFocus />
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="text-xs font-medium text-[rgb(var(--text-secondary))] mb-1.5 block">Visibility</label>
              <div className="flex gap-2">
                {[
                  { v: 'SYSTEM', l: 'System', d: 'Hidden from owners' },
                  { v: 'OPTIONAL', l: 'Optional', d: 'Owners can toggle' },
                ].map(({ v, l, d }) => (
                  <button key={v} type="button" onClick={() => setNewTier(v)}
                    className={`flex-1 p-2.5 rounded-lg text-left border transition-colors ${
                      newTier === v ? 'border-brand-500 bg-brand-50 dark:bg-brand-900/20 ring-1 ring-brand-500' :
                      'border-[rgb(var(--border-subtle))] hover:border-gray-300 dark:hover:border-gray-600'
                    }`}>
                    <span className="text-xs font-medium text-[rgb(var(--text-primary))] block">{l}</span>
                    <span className="text-[10px] text-[rgb(var(--text-muted))]">{d}</span>
                  </button>
                ))}
              </div>
            </div>
            <div>
              <label className="text-xs font-medium text-[rgb(var(--text-secondary))] mb-1.5 block">Category</label>
              <div className="flex gap-1.5 flex-wrap">
                {CATEGORIES.map(({ value, label, color }) => (
                  <button key={value} type="button" onClick={() => setNewCategory(value)}
                    className={`px-2.5 py-1.5 rounded-lg text-xs border transition-colors ${
                      newCategory === value ? 'border-brand-500 ring-1 ring-brand-500 ' + color :
                      'border-[rgb(var(--border-subtle))] text-gray-500 hover:border-gray-300 dark:hover:border-gray-600'
                    }`}>{label}</button>
                ))}
              </div>
            </div>
          </div>
          <div className="flex gap-2 justify-end pt-2 border-t border-[rgb(var(--border-subtle))]">
            <button type="button" onClick={() => setShowAdd(false)} className="btn-secondary text-sm">Cancel</button>
            <button type="button" onClick={handleAdd} disabled={adding || !newText.trim()} className="btn-primary text-sm">
              {adding ? <Loader2 size={16} className="animate-spin" /> : <Plus size={16} />} Add Rule
            </button>
          </div>
        </div>
      )}

      {isLoading ? (
        <div className="flex justify-center py-16"><Loader2 size={24} className="animate-spin text-brand-500" /></div>
      ) : (
        <>
          {/* System Rules */}
          <section className="space-y-3">
            <div className="flex items-center gap-3">
              <div className="flex items-center gap-2">
                <Lock size={14} className="text-red-500" />
                <h2 className="text-sm font-semibold text-[rgb(var(--text-primary))]">System Rules</h2>
              </div>
              <span className="text-xs text-gray-400">{systemRules.length}</span>
              <div className="flex-1 h-px bg-[rgb(var(--border-subtle))]" />
            </div>
            <p className="text-xs text-gray-500">Always enforced. Invisible to persona owners.</p>
            <div className="card overflow-hidden divide-y divide-[rgb(var(--border-subtle))]/60 border border-[rgb(var(--border-subtle))] shadow-sm">
              {systemRules.map((rule) => (
                <RuleRow key={rule.id} rule={rule}
                  onToggleActive={() => handleToggleActive(rule)}
                  onChangeTier={(tier) => handleChangeTier(rule, tier)}
                  onDelete={() => handleDelete(rule.id)} />
              ))}
              {systemRules.length === 0 && <p className="p-6 text-sm text-gray-400 text-center">No system rules configured.</p>}
            </div>
          </section>

          {/* Optional Rules */}
          <section className="space-y-3">
            <div className="flex items-center gap-3">
              <div className="flex items-center gap-2">
                <ToggleLeft size={14} className="text-brand-500" />
                <h2 className="text-sm font-semibold text-[rgb(var(--text-primary))]">Optional Rules</h2>
              </div>
              <span className="text-xs text-gray-400">{optionalRules.length}</span>
              <div className="flex-1 h-px bg-[rgb(var(--border-subtle))]" />
            </div>
            <p className="text-xs text-gray-500">Visible to persona owners. Enabled by default unless disabled per persona.</p>
            <div className="card overflow-hidden divide-y divide-[rgb(var(--border-subtle))]/60 border border-[rgb(var(--border-subtle))] shadow-sm">
              {optionalRules.map((rule) => (
                <RuleRow key={rule.id} rule={rule}
                  onToggleActive={() => handleToggleActive(rule)}
                  onChangeTier={(tier) => handleChangeTier(rule, tier)}
                  onDelete={() => handleDelete(rule.id)} />
              ))}
              {optionalRules.length === 0 && <p className="p-6 text-sm text-gray-400 text-center">No optional rules configured.</p>}
            </div>
          </section>
        </>
      )}
    </div>
  )
}
