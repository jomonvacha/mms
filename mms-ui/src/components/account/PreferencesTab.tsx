import { Sun, Moon, Monitor, Globe, Bell, User, Loader2 } from 'lucide-react'
import { notify } from '../Toast'
import { useTheme } from '../../hooks/useTheme'
import type { Theme } from '../../hooks/useTheme'
import { updatePreferences as apiUpdatePreferences, type PrefsRecord } from '../../api/client'

interface PreferencesTabProps {
  prefs: PrefsRecord
  setPrefs: (p: PrefsRecord) => void
  submitting: boolean
  setSubmitting: (v: boolean) => void
  onClose: () => void
}

export default function PreferencesTab({ prefs, setPrefs, submitting, setSubmitting, onClose }: PreferencesTabProps) {
  const { setTheme } = useTheme()

  const submitPreferences = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    try {
      const payload = {
        theme: prefs.theme || 'system',
        language: prefs.language || 'en',
        emailNotifications: Boolean(prefs.emailNotifications),
        navbarDisplay: prefs.navbarDisplay || 'avatar',
        notificationPrefs: prefs.notificationPrefs,
      }
      const saved = await apiUpdatePreferences(payload as Record<string, unknown>) as PrefsRecord
      if (saved) {
        setPrefs({ theme: saved.theme || 'system', language: saved.language || 'en', emailNotifications: Boolean(saved.emailNotifications), navbarDisplay: saved.navbarDisplay || 'avatar', notificationPrefs: saved.notificationPrefs })
        try { window.dispatchEvent(new CustomEvent('mms:prefsUpdated', { detail: saved })) } catch (_) {}
      }
      if (saved?.theme) {
        let effective: Theme = 'light'
        if (saved.theme === 'dark') effective = 'dark'
        else if (saved.theme === 'system') effective = window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
        setTheme(effective)
      }
      if (saved?.language) try { localStorage.setItem('mms_lang', saved.language) } catch (_) {}
      notify.success('Preferences saved.')
    } catch (err) {
      const e = err as { data?: { message?: string }; message?: string }
      notify.error(e?.data?.message || e?.message || 'Preferences update failed.')
    } finally {
      setSubmitting(false)
    }
  }

  const toggleNotif = (category: string, channel: string, value: boolean) => {
    const matrix = { ...(prefs.notificationPrefs || {}) }
    matrix[category] = { ...(matrix[category] || {}), [channel]: value }
    setPrefs({ ...prefs, notificationPrefs: matrix })
  }

  const themeOptions: { value: string; label: string; icon: React.ElementType }[] = [
    { value: 'system', label: 'System', icon: Monitor },
    { value: 'light', label: 'Light', icon: Sun },
    { value: 'dark', label: 'Dark', icon: Moon },
  ]

  return (
    <form onSubmit={submitPreferences} className="space-y-5">
      <div className="card p-4 space-y-3">
        <h3 className="text-sm font-semibold text-[rgb(var(--text-secondary))] flex items-center gap-2">
          <Sun size={15} /> Theme
        </h3>
        <div className="flex gap-2">
          {themeOptions.map(({ value, label, icon: Icon }) => (
            <button
              key={value}
              type="button"
              onClick={() => setPrefs({ ...prefs, theme: value })}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm border transition-colors ${
                prefs.theme === value
                  ? 'border-brand-500 bg-brand-50 text-brand-700 dark:bg-brand-900/30 dark:text-brand-400 dark:border-brand-400'
                  : 'border-[rgb(var(--border-strong))] text-[rgb(var(--text-secondary))] hover:bg-[rgb(var(--surface-3))]'
              }`}
            >
              <Icon size={14} /> {label}
            </button>
          ))}
        </div>
      </div>

      <div className="card p-4 space-y-3">
        <h3 className="text-sm font-semibold text-[rgb(var(--text-secondary))] flex items-center gap-2">
          <Globe size={15} /> Language
        </h3>
        <select
          className="input w-auto"
          value={prefs.language}
          onChange={(e) => setPrefs({ ...prefs, language: e.target.value })}
        >
          <option value="en">English</option>
          <option value="es">Español</option>
          <option value="fr">Français</option>
        </select>
      </div>

      <div className="card p-4">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-sm font-semibold text-[rgb(var(--text-secondary))] flex items-center gap-2">
              <Bell size={15} /> Email Notifications
            </h3>
            <p className="text-xs text-[rgb(var(--text-muted))] mt-0.5">Receive account and activity updates</p>
          </div>
          <label className="relative inline-flex items-center cursor-pointer">
            <input
              type="checkbox"
              className="sr-only peer"
              checked={Boolean(prefs.emailNotifications)}
              onChange={(e) => setPrefs({ ...prefs, emailNotifications: e.target.checked })}
            />
            <div className="w-10 h-5 bg-[rgb(var(--surface-3))] rounded-full peer peer-checked:bg-brand-600 transition-colors after:content-[''] after:absolute after:top-0.5 after:left-0.5 after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:after:translate-x-5" />
          </label>
        </div>
      </div>

      {prefs.notificationPrefs && Object.keys(prefs.notificationPrefs).length > 0 && (
        <div className="card p-4 space-y-3">
          <h3 className="text-sm font-semibold text-[rgb(var(--text-secondary))] flex items-center gap-2">
            <Bell size={15} /> Notification preferences
          </h3>
          <p className="text-xs text-[rgb(var(--text-muted))]">Choose how you&apos;re notified, per category.</p>
          <div className="space-y-2">
            {Object.entries(prefs.notificationPrefs).map(([category, channels]) => (
              <div key={category} className="flex items-center justify-between gap-3 py-1">
                <span className="text-sm capitalize text-[rgb(var(--text-secondary))]">{category}</span>
                <div className="flex gap-4">
                  {Object.entries(channels).map(([channel, on]) => (
                    <label key={channel} className="flex items-center gap-1.5 text-xs text-[rgb(var(--text-muted))] cursor-pointer">
                      <input
                        type="checkbox"
                        className="accent-brand-600"
                        checked={Boolean(on)}
                        onChange={(e) => toggleNotif(category, channel, e.target.checked)}
                      />
                      <span className="capitalize">{channel}</span>
                    </label>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="card p-4 space-y-3">
        <h3 className="text-sm font-semibold text-[rgb(var(--text-secondary))] flex items-center gap-2">
          <User size={15} /> Navbar display
        </h3>
        <p className="text-xs text-[rgb(var(--text-muted))]">What to show in the navigation bar user button</p>
        <div className="flex gap-2">
          {[
            { value: 'avatar', label: 'Avatar' },
            { value: 'initials', label: 'Initials' },
            { value: 'name', label: 'Name' },
          ].map(({ value, label }) => (
            <button
              key={value}
              type="button"
              onClick={() => setPrefs({ ...prefs, navbarDisplay: value })}
              className={`px-3 py-1.5 rounded-lg text-sm border transition-colors ${
                prefs.navbarDisplay === value
                  ? 'border-brand-500 bg-brand-50 text-brand-700 dark:bg-brand-900/30 dark:text-brand-400 dark:border-brand-400'
                  : 'border-[rgb(var(--border-strong))] text-[rgb(var(--text-secondary))] hover:bg-[rgb(var(--surface-3))]'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      <div className="flex gap-2 justify-end pt-2">
        <button type="button" className="btn-secondary text-sm" disabled={submitting} onClick={onClose}>Cancel</button>
        <button type="submit" className="btn-primary text-sm" disabled={submitting}>
          {submitting ? <><Loader2 size={16} className="animate-spin" />Saving…</> : 'Save preferences'}
        </button>
      </div>
    </form>
  )
}
