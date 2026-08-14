import { useState, useEffect, useMemo } from 'react'
import { Link, NavLink } from 'react-router-dom'
import {
  LogIn, Sun, Moon, User, LogOut, Settings, KeyRound, ShieldCheck, Smartphone,
  AlertCircle, Shield, ChevronDown, Menu, X,
} from 'lucide-react'
import { useAuth } from '../hooks/useAuth'
import type { AuthProvider as AuthProviderType } from '../hooks/useAuth'
import { useTheme } from '../hooks/useTheme'
import { getMyAvatarBlob, getPreferences } from '../api/client'
import AccountModal from './AccountModal'

function isFederated(p?: AuthProviderType): boolean { return p === 'GOOGLE' || p === 'APPLE' }
function providerLabel(p?: AuthProviderType): string {
  if (p === 'GOOGLE') return 'Google'
  if (p === 'APPLE') return 'Apple'
  return ''
}

type NavItem = {
  to: string
  label: string
  icon: typeof Shield
  show: boolean
  end?: boolean
}

export default function Navbar() {
  const { user, loading } = useAuth()
  const { theme, toggleTheme } = useTheme()
  const [menuOpen, setMenuOpen] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [initialTab, setInitialTab] = useState<'profile' | 'account' | 'security' | 'preferences'>('profile')
  const [avatarUrl, setAvatarUrl] = useState('')
  const [navDisplay, setNavDisplay] = useState<'avatar' | 'initials' | 'name'>('avatar')

  useEffect(() => {
    if (!user) return
    let cancelled = false
    getPreferences().then((p) => {
      if (!cancelled && p?.navbarDisplay) setNavDisplay(p.navbarDisplay as 'avatar' | 'initials' | 'name')
    }).catch(() => {})
    const onPrefsUpdated = (e: Event) => {
      const d = (e as CustomEvent).detail
      if (d?.navbarDisplay) setNavDisplay(d.navbarDisplay as 'avatar' | 'initials' | 'name')
    }
    window.addEventListener('mms:prefsUpdated', onPrefsUpdated)
    return () => { cancelled = true; window.removeEventListener('mms:prefsUpdated', onPrefsUpdated) }
  }, [user])

  const isPrivileged = useMemo(() => {
    const roles = user?.roles || []
    return roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MODERATOR') || roles.includes('ROLE_MANAGER')
  }, [user])

  const initials = useMemo(() => {
    const fn = (user?.firstName || '').trim()
    const ln = (user?.lastName || '').trim()
    const a = fn ? fn[0] : ''
    const b = ln ? ln[0] : ''
    if (a || b) return (a + b).toUpperCase()
    const un = (user?.username || user?.email || '').trim()
    return un ? un[0].toUpperCase() : '?'
  }, [user])

  useEffect(() => {
    let revokeUrl: string | undefined
    if (!user) { setAvatarUrl(''); return }
    const loadAvatar = () => {
      getMyAvatarBlob()
        .then((blob) => {
          if (revokeUrl) URL.revokeObjectURL(revokeUrl)
          const url = URL.createObjectURL(blob)
          revokeUrl = url
          setAvatarUrl(url)
        })
        .catch(() => setAvatarUrl(''))
    }
    loadAvatar()
    const onAvatarUpdated = () => loadAvatar()
    window.addEventListener('mms:avatarUpdated', onAvatarUpdated)
    return () => { if (revokeUrl) URL.revokeObjectURL(revokeUrl); window.removeEventListener('mms:avatarUpdated', onAvatarUpdated) }
  }, [user])

  // Close user menu on Escape or outside click
  useEffect(() => {
    if (!menuOpen) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setMenuOpen(false) }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [menuOpen])

  const openModal = (tab: 'profile' | 'account' | 'security' | 'preferences') => {
    setInitialTab(tab)
    setModalOpen(true)
    setMenuOpen(false)
  }

  // Navbar stays focused on daily-hot paths. The remaining admin surfaces
  // (Admin/Locale/Rules/Governance/Invites/Settings) are reached via the
  // My Access hub — the tiles double as description + launcher.
  const navItems: NavItem[] = [
    { to: '/',            label: 'My Access', icon: Shield,      show: Boolean(user), end: true },
    { to: '/admin/users', label: 'Users',     icon: ShieldCheck, show: isPrivileged },
  ]

  // Non-admin signed-in users get no navbar at all — their entire in-app
  // surface is the MemberLandingCard, which carries its own Sign out action.
  // Keeping the My Access nav link, account menu, theme toggle, etc. would
  // expose MMS internals (Profile/Account/Security/Preferences) that have no
  // purpose for a user who doesn't belong in this app.
  if (user && !isPrivileged) return null

  return (
    <>
      <nav className="nav-surface sticky top-0 z-40">
        <div className="container mx-auto px-4 max-w-7xl">
          <div className="flex items-center h-14 gap-3">
            {/* Brand */}
            <Link
              to="/"
              className="flex items-center gap-2 pr-2 rounded-md focus:outline-none focus-visible:ring-2 focus-visible:ring-[rgb(var(--nav-ring)/0.6)]"
              aria-label="MMS home"
            >
              <span className="inline-flex items-center justify-center w-7 h-7 rounded-md bg-brand-600/10 text-brand-600 dark:bg-brand-400/10 dark:text-brand-300 ring-1 ring-inset ring-brand-600/20 dark:ring-brand-400/20">
                <Shield size={16} strokeWidth={2.25} />
              </span>
              <span className="hidden sm:inline text-[15px] font-semibold tracking-tight text-[rgb(var(--nav-fg))]">MMS</span>
            </Link>

            {/* Divider */}
            <span className="hidden md:block h-5 w-px bg-[rgb(var(--nav-border))]" aria-hidden="true" />

            {/* Desktop nav */}
            <div className="hidden md:flex items-center gap-0.5 flex-1 min-w-0 overflow-x-auto">
              {navItems.filter((n) => n.show).map((item) => {
                const Icon = item.icon
                return (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.end}
                    className={({ isActive }) => `nav-link${isActive ? ' is-active' : ''}`}
                  >
                    <Icon size={15} strokeWidth={2} className="nav-link__icon" />
                    {item.label}
                  </NavLink>
                )
              })}
            </div>

            <div className="flex items-center gap-1 ml-auto">
              {/* Theme toggle */}
              <button
                type="button"
                onClick={toggleTheme}
                className="nav-icon-btn"
                aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
                title={theme === 'dark' ? 'Light mode' : 'Dark mode'}
              >
                {theme === 'dark' ? <Sun size={17} /> : <Moon size={17} />}
              </button>

              {/* User menu */}
              {loading ? (
                <div className="h-9 w-9 rounded-full bg-[rgb(var(--nav-hover-bg)/0.5)] animate-pulse" />
              ) : user ? (
                <div className="relative">
                  <button
                    type="button"
                    onClick={() => setMenuOpen((o) => !o)}
                    className="nav-avatar-btn"
                    aria-haspopup="menu"
                    aria-expanded={menuOpen}
                    aria-label="Account menu"
                  >
                    {navDisplay === 'name' ? (
                      <span className="text-sm font-medium text-[rgb(var(--nav-fg))] px-2">
                        {user.firstName || user.username}
                      </span>
                    ) : navDisplay === 'initials' ? (
                      <span className="w-7 h-7 rounded-full bg-brand-100 dark:bg-brand-900/50 flex items-center justify-center text-[11px] font-semibold text-brand-700 dark:text-brand-300 ring-1 ring-inset ring-brand-600/20 dark:ring-brand-400/20">
                        {initials}
                      </span>
                    ) : avatarUrl ? (
                      <span className="w-7 h-7 rounded-full overflow-hidden ring-1 ring-[rgb(var(--nav-border))]">
                        <img src={avatarUrl} alt="" className="w-full h-full object-cover" />
                      </span>
                    ) : (
                      <span className="w-7 h-7 rounded-full bg-brand-100 dark:bg-brand-900/50 flex items-center justify-center text-[11px] font-semibold text-brand-700 dark:text-brand-300 ring-1 ring-inset ring-brand-600/20 dark:ring-brand-400/20">
                        {initials}
                      </span>
                    )}
                    <ChevronDown
                      size={14}
                      className={`text-[rgb(var(--nav-fg-muted))] transition-transform duration-150 ${menuOpen ? 'rotate-180' : ''}`}
                      aria-hidden="true"
                    />
                  </button>

                  {menuOpen && (
                    <>
                      <div
                        className="fixed inset-0 z-10"
                        onClick={() => setMenuOpen(false)}
                        aria-hidden="true"
                      />
                      <div
                        role="menu"
                        className="absolute right-0 mt-2 w-64 card shadow-xl z-20 py-1 origin-top-right"
                      >
                        <div className="px-4 py-3 border-b border-[rgb(var(--border-subtle))]">
                          <p className="text-sm font-semibold truncate text-[rgb(var(--text-primary))]">
                            {user.firstName} {user.lastName}
                          </p>
                          <p className="text-xs truncate mt-0.5 text-[rgb(var(--text-muted))]">{user.email}</p>
                          <div className="flex items-center gap-1.5 mt-2 flex-wrap">
                            {isFederated(user.provider) && (
                              <span className="inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[10px] font-medium bg-emerald-50 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400">
                                <ShieldCheck size={9} /> {providerLabel(user.provider)}
                              </span>
                            )}
                            {user.emailVerified === false && (
                              <span className="inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[10px] font-medium bg-amber-50 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400">
                                <AlertCircle size={9} /> Unverified
                              </span>
                            )}
                            {user.twoFactorEnabled && (
                              <span className="inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[10px] font-medium bg-brand-50 text-brand-700 dark:bg-brand-900/30 dark:text-brand-400">
                                <ShieldCheck size={9} /> 2FA
                              </span>
                            )}
                          </div>
                        </div>
                        <div className="py-1">
                          <MenuItem icon={User}     label="Profile"     onClick={() => openModal('profile')} />
                          <MenuItem icon={KeyRound} label="Account"     onClick={() => openModal('account')} />
                          <MenuItem
                            icon={Smartphone}
                            label="Security"
                            onClick={() => openModal('security')}
                            trailing={user.twoFactorEnabled ? (
                              <span className="text-[10px] px-1.5 py-0.5 rounded bg-emerald-50 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400">2FA on</span>
                            ) : undefined}
                          />
                          <MenuItem icon={Settings} label="Preferences" onClick={() => openModal('preferences')} />
                        </div>
                        <div className="border-t border-[rgb(var(--border-subtle))] py-1">
                          <Link
                            to="/signout"
                            onClick={() => setMenuOpen(false)}
                            className="w-full flex items-center gap-2 px-4 py-2 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
                            role="menuitem"
                          >
                            <LogOut size={15} /> Sign out
                          </Link>
                        </div>
                      </div>
                    </>
                  )}
                </div>
              ) : (
                <NavLink
                  to="/signin"
                  className={({ isActive }) => `nav-link${isActive ? ' is-active' : ''}`}
                >
                  <LogIn size={15} strokeWidth={2} className="nav-link__icon" />
                  Sign In
                </NavLink>
              )}

              {/* Mobile menu toggle */}
              <button
                type="button"
                onClick={() => setMobileOpen((o) => !o)}
                className="nav-icon-btn md:hidden"
                aria-label={mobileOpen ? 'Close navigation' : 'Open navigation'}
                aria-expanded={mobileOpen}
              >
                {mobileOpen ? <X size={18} /> : <Menu size={18} />}
              </button>
            </div>
          </div>

          {/* Mobile menu */}
          {mobileOpen && (
            <div className="md:hidden pb-3 border-t border-[rgb(var(--nav-border))] pt-2 flex flex-col gap-0.5">
              {navItems.filter((n) => n.show).map((item) => {
                const Icon = item.icon
                return (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.end}
                    className={({ isActive }) => `nav-link w-full justify-start${isActive ? ' is-active' : ''}`}
                    onClick={() => setMobileOpen(false)}
                  >
                    <Icon size={15} strokeWidth={2} className="nav-link__icon" />
                    {item.label}
                  </NavLink>
                )
              })}
              {!user && (
                <NavLink
                  to="/signin"
                  className={({ isActive }) => `nav-link w-full justify-start${isActive ? ' is-active' : ''}`}
                  onClick={() => setMobileOpen(false)}
                >
                  <LogIn size={15} strokeWidth={2} className="nav-link__icon" />
                  Sign In
                </NavLink>
              )}
            </div>
          )}
        </div>
      </nav>

      <AccountModal isOpen={modalOpen} initialTab={initialTab} onClose={() => setModalOpen(false)} />
    </>
  )
}

function MenuItem({
  icon: Icon,
  label,
  onClick,
  trailing,
}: {
  icon: typeof Shield
  label: string
  onClick: () => void
  trailing?: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      role="menuitem"
      className="w-full flex items-center gap-2 px-4 py-2 text-sm text-[rgb(var(--text-secondary))] hover:bg-[rgb(var(--nav-hover-bg)/0.7)] transition-colors"
    >
      <Icon size={15} className="text-[rgb(var(--text-muted))]" />
      <span className="flex-1 text-left">{label}</span>
      {trailing ? <span className="ml-auto">{trailing}</span> : null}
    </button>
  )
}
