import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { X, User, KeyRound, Settings, ShieldCheck, Laptop } from 'lucide-react'
import { notify } from './Toast'
import AvatarCropper from './AvatarCropper'
import { useAuth } from '../hooks/useAuth'
import {
  updatePreferences as apiUpdatePreferences, uploadAvatar, getMyAvatarBlob as fetchAvatarBlob,
  getPreferences, type PrefsRecord,
} from '../api/client'
import { isFederated, providerLabel } from './account/shared'
import ProfileTab from './account/ProfileTab'
import AccountTab from './account/AccountTab'
import SecurityTab from './account/SecurityTab'
import SessionsTab from './account/SessionsTab'
import PreferencesTab from './account/PreferencesTab'

type Tab = 'profile' | 'account' | 'preferences' | 'security' | 'sessions'

interface Props {
  isOpen: boolean
  initialTab?: Tab
  onClose: () => void
}

function getTabbables(container: HTMLElement | null): HTMLElement[] {
  if (!container) return []
  const sel = [
    'a[href]', 'area[href]', 'input:not([disabled])', 'select:not([disabled])',
    'textarea:not([disabled])', 'button:not([disabled])', '[tabindex]:not([tabindex="-1"])',
  ].join(',')
  return Array.from(container.querySelectorAll<HTMLElement>(sel))
}

export default function AccountModal({ isOpen, initialTab = 'profile', onClose }: Props) {
  const { user } = useAuth()
  const [activeTab, setActiveTab] = useState<Tab>(initialTab)
  const [submitting, setSubmitting] = useState(false)
  const panelRef = useRef<HTMLDivElement>(null)
  const lastFocused = useRef<Element | null>(null)

  useEffect(() => { if (isOpen) setActiveTab(initialTab) }, [isOpen, initialTab])

  useEffect(() => {
    if (!isOpen) return
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = prev }
  }, [isOpen])

  useEffect(() => {
    if (isOpen) {
      lastFocused.current = document.activeElement
      const tabbables = getTabbables(panelRef.current)
      ;(tabbables[0] || panelRef.current)?.focus()
    } else if (lastFocused.current) {
      try { (lastFocused.current as HTMLElement).focus() } catch (_) {}
      lastFocused.current = null
    }
  }, [isOpen])

  const onKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (!isOpen) return
    if (e.key === 'Escape' && !submitting) { e.stopPropagation(); onClose() }
    if (e.key === 'Tab') {
      const tabbables = getTabbables(panelRef.current)
      if (!tabbables.length) return
      const first = tabbables[0], last = tabbables[tabbables.length - 1]
      if (e.shiftKey) { if (document.activeElement === first) { e.preventDefault(); last.focus() } }
      else { if (document.activeElement === last) { e.preventDefault(); first.focus() } }
    }
  }, [isOpen, submitting, onClose])

  // Avatar — fetched once per open so it's available for the header regardless of tab
  const [avatarUrl, setAvatarUrl] = useState('')
  const [uploadingAvatar, setUploadingAvatar] = useState(false)
  const [cropSrc, setCropSrc] = useState<string | null>(null) // image to crop before upload

  const initials = useMemo(() => {
    const fn = (user?.firstName || '').trim()
    const ln = (user?.lastName || '').trim()
    const a = fn ? fn[0] : '', b = ln ? ln[0] : ''
    if (a || b) return (a + b).toUpperCase()
    const un = (user?.username || user?.email || '').trim()
    return un ? un[0].toUpperCase() : '?'
  }, [user])

  useEffect(() => {
    let revokeUrl: string | undefined
    if (user && isOpen) {
      fetchAvatarBlob()
        .then((blob) => {
          const url = URL.createObjectURL(blob)
          revokeUrl = url
          setAvatarUrl(url)
        })
        .catch(() => setAvatarUrl(''))
    }
    return () => { if (revokeUrl) URL.revokeObjectURL(revokeUrl) }
  }, [user, isOpen])

  // Preferences — shared between the Profile tab (avatar → navbarDisplay sync)
  // and the Preferences tab, so it's loaded once here rather than per-tab.
  const [prefs, setPrefs] = useState<PrefsRecord>({ theme: 'system', language: 'en', emailNotifications: true, navbarDisplay: 'avatar' })

  useEffect(() => {
    let cancelled = false
    async function loadPrefs() {
      if (!isOpen) return
      try {
        const p = await getPreferences()
        if (cancelled || !p) return
        setPrefs({ theme: p.theme || 'system', language: p.language || 'en', emailNotifications: Boolean(p.emailNotifications), navbarDisplay: p.navbarDisplay || 'avatar', notificationPrefs: p.notificationPrefs })
      } catch (_) {}
    }
    loadPrefs()
    return () => { cancelled = true }
  }, [isOpen])

  // Open the cropper when a file is selected
  const handleAvatarFileSelect = (file: File) => {
    if (!file.type.startsWith('image/')) { notify.error('Only image files.'); return }
    if (file.size > 10 * 1024 * 1024) { notify.error('Max 10 MB.'); return }
    const reader = new FileReader()
    reader.onload = () => { if (typeof reader.result === 'string') setCropSrc(reader.result) }
    reader.readAsDataURL(file)
  }

  const handleRemoveAvatar = () => {
    setAvatarUrl('')
    notify.success('Avatar removed. Save to apply.')
  }

  // Called by AvatarCropper after crop is confirmed
  const handleCroppedAvatar = async (blob: Blob) => {
    setUploadingAvatar(true)
    setCropSrc(null)
    try {
      const file = new File([blob], 'avatar.jpg', { type: 'image/jpeg' })
      await uploadAvatar(file)
      const fresh = await fetchAvatarBlob()
      const url = URL.createObjectURL(fresh)
      setAvatarUrl((old) => { if (old?.startsWith('blob:')) URL.revokeObjectURL(old); return url })
      try { window.dispatchEvent(new CustomEvent('mms:avatarUpdated')) } catch (_) {}
      if (prefs.navbarDisplay !== 'avatar') {
        const updated = { ...prefs, navbarDisplay: 'avatar' }
        setPrefs(updated as PrefsRecord)
        try {
          await apiUpdatePreferences(updated as Record<string, unknown>)
          window.dispatchEvent(new CustomEvent('mms:prefsUpdated', { detail: updated }))
        } catch (_) {}
      }
      notify.success('Avatar updated.')
    } catch (_) { notify.error('Upload failed.') }
    finally { setUploadingAvatar(false) }
  }

  const SidebarItem = ({ tab, label, icon: Icon }: { tab: Tab; label: string; icon: React.ElementType }) => (
    <button
      type="button"
      onClick={() => setActiveTab(tab)}
      className={`w-full flex items-center gap-2.5 px-3 py-2.5 rounded-lg text-sm font-medium transition-all text-left ${
        activeTab === tab
          ? 'bg-brand-600 text-white shadow-sm dark:bg-brand-600'
          : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-gray-200'
      }`}
    >
      <Icon size={16} />
      {label}
    </button>
  )

  if (!isOpen) return null

  return (
    <div
      className="fixed inset-0 z-50 flex items-start md:items-center justify-center p-0 md:p-4 overflow-auto"
      role="dialog"
      aria-modal="true"
      aria-labelledby="accountSettingsTitle"
      onKeyDown={onKeyDown}
    >
      <div className="absolute inset-0 bg-gray-950/95 backdrop-blur-md" onClick={() => { if (!submitting) onClose() }} />
      <div
        ref={panelRef}
        tabIndex={-1}
        className="relative card w-full max-w-3xl rounded-none md:rounded-xl shadow-xl"
      >
        {/* Header — enterprise style with avatar + provider badge */}
        <div className="flex items-center gap-4 p-5 border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/40 md:rounded-t-xl">
          {user && (
            <div className="w-11 h-11 rounded-full bg-brand-100 dark:bg-brand-900/50 flex items-center justify-center text-lg font-semibold text-brand-600 dark:text-brand-400 flex-shrink-0 overflow-hidden">
              {avatarUrl ? <img src={avatarUrl} alt="" className="w-full h-full object-cover" /> : initials}
            </div>
          )}
          <div className="flex-1 min-w-0">
            <h2 id="accountSettingsTitle" className="text-base font-semibold text-gray-900 dark:text-gray-100">
              User Settings
            </h2>
            {user && (
              <div className="flex items-center gap-2 mt-0.5">
                <p className="text-sm text-gray-500 dark:text-gray-400 truncate">
                  {user.email}
                </p>
                {isFederated(user.provider) && (
                  <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-medium bg-emerald-50 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400 flex-shrink-0">
                    <ShieldCheck size={10} /> {providerLabel(user.provider)}
                  </span>
                )}
              </div>
            )}
          </div>
          <button type="button" onClick={() => { if (!submitting) onClose() }} disabled={submitting}
            className="p-2 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-200 dark:hover:text-gray-200 dark:hover:bg-gray-700 transition-colors flex-shrink-0"
            aria-label="Close">
            <X size={18} />
          </button>
        </div>

        {/* Body */}
        <div className="flex flex-col md:flex-row">
          {/* Sidebar — enterprise nav with subtle hover */}
          <aside className="md:w-48 border-b md:border-b-0 md:border-r border-gray-200 dark:border-gray-700 p-3 space-y-1 bg-gray-50/50 dark:bg-gray-800/20">
            <SidebarItem tab="profile" label="Profile" icon={User} />
            <SidebarItem tab="account" label="Account" icon={KeyRound} />
            <SidebarItem tab="security" label="Security" icon={ShieldCheck} />
            <SidebarItem tab="sessions" label="Sessions" icon={Laptop} />
            <SidebarItem tab="preferences" label="Preferences" icon={Settings} />
          </aside>

          {/* Content */}
          <section className="flex-1 p-6 overflow-auto max-h-[70vh]">
            {activeTab === 'profile' && (
              <ProfileTab
                avatarUrl={avatarUrl}
                uploadingAvatar={uploadingAvatar}
                initials={initials}
                submitting={submitting}
                setSubmitting={setSubmitting}
                onFileSelected={handleAvatarFileSelect}
                onRemoveAvatar={handleRemoveAvatar}
                onClose={onClose}
              />
            )}
            {activeTab === 'account' && (
              <AccountTab submitting={submitting} setSubmitting={setSubmitting} onClose={onClose} />
            )}
            {activeTab === 'security' && (
              <SecurityTab submitting={submitting} setSubmitting={setSubmitting} onClose={onClose} />
            )}
            {activeTab === 'sessions' && (
              <SessionsTab submitting={submitting} setSubmitting={setSubmitting} />
            )}
            {activeTab === 'preferences' && (
              <PreferencesTab prefs={prefs} setPrefs={setPrefs} submitting={submitting} setSubmitting={setSubmitting} onClose={onClose} />
            )}
          </section>
        </div>
      </div>

      {/* Avatar crop/reposition modal */}
      {cropSrc && (
        <AvatarCropper
          imageSrc={cropSrc}
          onCancel={() => setCropSrc(null)}
          onSave={handleCroppedAvatar}
        />
      )}
    </div>
  )
}
