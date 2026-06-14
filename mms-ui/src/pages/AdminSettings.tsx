import { useMemo } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Settings, Loader2, ShieldAlert, Ticket } from 'lucide-react'
import { notify } from '../components/Toast'
import { listAppSettings, updateAppSetting, type AppSetting } from '../api/client'

const BOOLEAN_KEYS = new Set(['signup.inviteRequired'])

function isBoolValue(v: string) { return ['true', 'false', '1', '0', 'yes', 'no'].includes(v.toLowerCase()) }
function parseBool(v: string) { return v === 'true' || v === '1' || v.toLowerCase() === 'yes' }

const META: Record<string, { label: string; help: string; icon: React.ElementType }> = {
  'signup.inviteRequired': {
    label: 'Require invite code for IDFY signup',
    help: 'When enabled, every new signup must supply a valid admin-issued invite code. Turn off to allow open self-registration.',
    icon: Ticket,
  },
}

export default function AdminSettings() {
  const qc = useQueryClient()
  const { data: settings = [], isLoading, isError, error } = useQuery({
    queryKey: ['app-settings'],
    queryFn: listAppSettings,
  })

  const toggle = useMutation({
    mutationFn: ({ setting, next }: { setting: AppSetting; next: boolean }) =>
      updateAppSetting(setting.key, next ? 'true' : 'false'),
    onSuccess: (_d, vars) => {
      const meta = META[vars.setting.key]
      notify.success(`${meta?.label || vars.setting.key} ${vars.next ? 'enabled' : 'disabled'}.`)
      qc.invalidateQueries({ queryKey: ['app-settings'] })
    },
    onError: (err) => notify.error((err as { message?: string })?.message || 'Failed.'),
  })

  const grouped = useMemo(() => {
    const bools = settings.filter((s) => BOOLEAN_KEYS.has(s.key) || isBoolValue(s.value))
    const others = settings.filter((s) => !BOOLEAN_KEYS.has(s.key) && !isBoolValue(s.value))
    return { bools, others }
  }, [settings])

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2.5">
          <div className="p-2 rounded-lg bg-brand-50 dark:bg-brand-900/30">
            <Settings size={20} className="text-brand-600 dark:text-brand-400" />
          </div>
          Platform Settings
        </h1>
        <p className="text-sm text-gray-500 dark:text-gray-400">
          Admin-controlled toggles that take effect immediately, without a redeploy.
        </p>
      </div>

      {isLoading && (
        <div className="card p-16 flex flex-col items-center justify-center gap-3">
          <Loader2 size={28} className="animate-spin text-brand-600" />
          <p className="text-sm text-gray-500">Loading settings...</p>
        </div>
      )}

      {isError && (
        <div className="card p-5 border border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/10">
          <div className="flex items-start gap-3">
            <ShieldAlert size={18} className="text-red-500 mt-0.5 flex-shrink-0" />
            <div>
              <p className="text-sm font-medium text-red-800 dark:text-red-300">Failed to load settings</p>
              <p className="text-xs mt-0.5 text-red-600/80 dark:text-red-400/80">{(error as Error)?.message}</p>
            </div>
          </div>
        </div>
      )}

      {!isLoading && !isError && (
        <div className="card divide-y divide-gray-100 dark:divide-gray-700/60">
          {grouped.bools.length === 0 && (
            <div className="p-8 text-center">
              <p className="text-sm text-gray-500 dark:text-gray-400">No toggleable settings configured yet.</p>
            </div>
          )}
          {grouped.bools.map((s) => {
            const meta = META[s.key] || { label: s.key, help: s.description || '', icon: Settings }
            const Icon = meta.icon
            const value = parseBool(s.value)
            const busy = toggle.isPending && toggle.variables?.setting.key === s.key
            return (
              <div key={s.key} className="flex items-start gap-4 p-5">
                <div className="mt-0.5 w-9 h-9 rounded-lg bg-brand-50 dark:bg-brand-900/30 flex items-center justify-center flex-shrink-0">
                  <Icon size={16} className="text-brand-600 dark:text-brand-400" />
                </div>
                <div className="flex-1 min-w-0">
                  <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">{meta.label}</h3>
                  <p className="mt-1 text-xs text-gray-500 dark:text-gray-400 leading-relaxed">{meta.help}</p>
                  <p className="mt-2 text-[11px] text-gray-400 dark:text-gray-500 font-mono">{s.key}</p>
                </div>
                <button type="button" disabled={busy}
                  onClick={() => toggle.mutate({ setting: s, next: !value })}
                  className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors flex-shrink-0 ${
                    value ? 'bg-brand-600' : 'bg-gray-300 dark:bg-gray-600'
                  } ${busy ? 'opacity-60 cursor-not-allowed' : ''}`}
                  aria-pressed={value} aria-label={meta.label}>
                  <span className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${
                    value ? 'translate-x-6' : 'translate-x-1'
                  }`} />
                </button>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
