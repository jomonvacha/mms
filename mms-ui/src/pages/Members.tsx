import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  Loader2, Users, Shield, LayoutDashboard, Settings, Lock, Globe,
  AlertCircle, Layers, Ticket, ArrowUpRight, Bell, Code, Sparkles,
  LogOut, UserCircle2,
} from 'lucide-react'
import { getMyFeatures, type FeatureRecord, getMemberByUserId } from '../api/client'
import { useAuth, type MmsUser } from '../hooks/useAuth'

// Prod falls back to same-origin so an unset env doesn't silently redirect
// users to a dead localhost URL; dev defaults to the IDFY UI's dev port.
export const IDFY_HOME_URL =
  import.meta.env.VITE_IDFY_URL || (import.meta.env.DEV ? 'http://localhost:3000' : '/')

// Roles with any admin-style surface inside MMS. A user carrying at least one
// of these sees the full "My Access" dashboard; everyone else (plain members,
// legacy tier-only roles, unassigned) sees the minimal landing card — MMS
// isn't where their day-to-day work happens.
export const MMS_ADMIN_ROLES = ['ROLE_ADMIN', 'ROLE_MODERATOR', 'ROLE_MANAGER']

const ICON_MAP: Record<string, React.ElementType> = {
  LayoutDashboard, Users, Shield, Settings, Lock, Globe, AlertCircle, Layers, Ticket, Sparkles,
}

// Feature code → in-app route. Mirrors MMS_ROLE_FEATURES in DataInitializer.java
// and the RoleProtectedRoute guards in routes.tsx.
const FEATURE_ROUTES: Record<string, string> = {
  view_dashboard: '/',
  manage_users: '/admin/users',
  manage_roles: '/admin/features',
  manage_features: '/admin/features',
  manage_settings: '/admin/settings',
  manage_locale: '/admin/locale',
  manage_rules: '/admin/rules',
  manage_governance: '/admin/governance',
  manage_invites: '/admin/invites',
  manage_models: '/admin/models',
}

const CATEGORY_ICONS: Record<string, React.ElementType> = {
  Administration: Shield,
  General: LayoutDashboard,
  Communication: Bell,
  Integration: Code,
  Members: Users,
}

const ROLE_CHIP: Record<string, string> = {
  ROLE_ADMIN: 'chip chip--danger',
  ROLE_MODERATOR: 'chip chip--warn',
  ROLE_MANAGER: 'chip chip--warn',
  ROLE_MEMBER: 'chip chip--brand',
}

function FeatureIcon({ name }: { name?: string }) {
  const Icon = (name && ICON_MAP[name]) || Shield
  return <Icon size={20} strokeWidth={1.75} />
}

export default function Members() {
  const { user } = useAuth()
  const userRoles = user?.roles || []
  const isMmsAdmin = userRoles.some((r) => MMS_ADMIN_ROLES.includes(r))

  // Skip the feature/membership queries entirely for non-admin users — they
  // never render the full dashboard, so fetching the data would just be wasted
  // bandwidth and an unnecessary surface for backend failures.
  const {
    data: features = [],
    isLoading: featuresLoading,
    isError: featuresErrored,
    error: featuresError,
    refetch: refetchFeatures,
  } = useQuery({
    queryKey: ['my-features', user?.id],
    queryFn: getMyFeatures,
    enabled: Boolean(user) && isMmsAdmin,
  })

  const { data: myMember } = useQuery({
    queryKey: ['my-member', user?.id],
    queryFn: () => user?.id ? getMemberByUserId(user.id).catch(() => null) : null,
    enabled: Boolean(user) && isMmsAdmin,
  })

  const grouped = useMemo(() => {
    const map = new Map<string, FeatureRecord[]>()
    for (const f of features) {
      const cat = f.category || 'General'
      if (!map.has(cat)) map.set(cat, [])
      map.get(cat)!.push(f)
    }
    return Array.from(map.entries())
  }, [features])

  // Non-admin users (plain members, legacy tier-only roles, unassigned) get
  // a minimal "signed in" card with two clear choices instead of the admin
  // dashboard. Their actual workspace is IDFY — MMS has nothing for them.
  if (user && !isMmsAdmin) {
    return <MemberLandingCard user={user} />
  }

  const memberRecord = myMember as { membershipType?: string; categoryCode?: string; tierCode?: string } | null
  const memberType = memberRecord?.membershipType
  const categoryCode = memberRecord?.categoryCode
  const tierCode = memberRecord?.tierCode

  // TODO remove after one release cycle — legacy Platform Access roles are
  // cleaned up by the backend seeder, this filter is a transitional safety net.
  const visibleRoles = userRoles.filter(
    (r) => !r.startsWith('ROLE_BASIC') && !r.startsWith('ROLE_PREMIUM') && !r.startsWith('ROLE_ENTERPRISE')
  )

  return (
    <div className="space-y-8 py-2">
      {/* Page header */}
      <header className="flex flex-col gap-2">
        <div className="page-eyebrow">
          <Shield size={12} strokeWidth={2.25} />
          Access Control
        </div>
        <h1 className="page-title">My Access</h1>
        <p className="page-subtitle max-w-2xl">
          Features and capabilities available to you based on your roles and membership.
          Click a tile to jump straight to the corresponding admin surface.
        </p>
      </header>

      {/* Access summary — three balanced cells with a hairline accent. */}
      <section aria-label="Access summary" className="stat-panel">
        <div className="grid grid-cols-1 sm:grid-cols-3 divide-y sm:divide-y-0 sm:divide-x stat-divider">
          <div className="stat-cell">
            <div className="stat-label">
              <Shield size={12} strokeWidth={2.25} /> Your Roles
            </div>
            <div className="stat-value flex-wrap gap-1.5">
              {visibleRoles.length > 0 ? (
                visibleRoles.map((r) => (
                  <span key={r} className={ROLE_CHIP[r] || 'chip chip--neutral'}>
                    {r.replace(/^ROLE_/, '')}
                  </span>
                ))
              ) : (
                <span className="text-sm text-gray-400 dark:text-gray-500 font-normal">No roles assigned</span>
              )}
            </div>
          </div>

          <div className="stat-cell">
            <div className="stat-label">
              <Layers size={12} strokeWidth={2.25} /> Membership
            </div>
            <div className="stat-value flex-wrap gap-1.5">
              {categoryCode && tierCode ? (
                <>
                  <span className="chip chip--brand">{categoryCode}</span>
                  <span className="text-gray-300 dark:text-gray-600 font-light">/</span>
                  <span className="chip chip--warn">{tierCode}</span>
                </>
              ) : memberType ? (
                <span className="chip chip--neutral">{memberType}</span>
              ) : (
                <span className="text-sm text-gray-400 dark:text-gray-500 font-normal">None</span>
              )}
            </div>
          </div>

          <div className="stat-cell">
            <div className="stat-label">
              <Sparkles size={12} strokeWidth={2.25} /> Features
            </div>
            {/* Three-state rendering — a loading or errored query must not
                silently render as "0 available", which is indistinguishable
                from a genuine zero-features account and would mis-signal the
                user's actual access level. */}
            <div className="stat-value" aria-live="polite">
              {featuresLoading ? (
                <>
                  <Loader2 size={22} className="animate-spin text-gray-400 dark:text-gray-500" aria-hidden="true" />
                  <span className="ml-2 text-xs font-normal text-gray-400 dark:text-gray-500">loading…</span>
                </>
              ) : featuresErrored ? (
                <>
                  <span className="text-[28px] leading-none font-bold tabular-nums text-gray-300 dark:text-gray-600">—</span>
                  <span className="ml-2 text-xs font-normal text-red-500 dark:text-red-400">unavailable</span>
                </>
              ) : (
                <>
                  <span className="text-[28px] leading-none font-bold tabular-nums text-brand-600 dark:text-brand-400">
                    {features.length}
                  </span>
                  <span className="ml-2 text-xs font-normal text-gray-400 dark:text-gray-500">
                    available
                  </span>
                </>
              )}
            </div>
          </div>
        </div>
      </section>

      {/* Loading */}
      {featuresLoading && (
        <div className="stat-panel p-12 flex items-center justify-center">
          <Loader2 size={28} className="animate-spin text-brand-600 dark:text-brand-400" />
        </div>
      )}

      {/* Error — shown instead of silently rendering an empty page. Without this,
          a failed /my-features call and a genuine zero-features account look
          identical to the user (and only the latter should redirect to IDFY). */}
      {featuresErrored && (
        <div role="alert" className="stat-panel p-8 border border-red-200 dark:border-red-800 bg-red-50/40 dark:bg-red-900/10">
          <div className="flex items-start gap-3">
            <AlertCircle size={20} className="text-red-500 mt-0.5 flex-shrink-0" aria-hidden="true" />
            <div className="flex-1 min-w-0">
              <p className="text-sm font-semibold text-red-800 dark:text-red-300">Couldn't load your access</p>
              <p className="text-xs mt-0.5 text-red-600/80 dark:text-red-400/80">
                {(featuresError as Error)?.message || 'Something went wrong. Please try again.'}
              </p>
            </div>
            <button type="button" onClick={() => refetchFeatures()}
              className="flex-shrink-0 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold
                         bg-red-100 text-red-800 hover:bg-red-200
                         dark:bg-red-800/40 dark:text-red-200 dark:hover:bg-red-800/60
                         focus:outline-none focus:ring-2 focus:ring-red-500 transition-colors">
              Retry
            </button>
          </div>
        </div>
      )}

      {/* Feature groups */}
      {grouped.map(([category, feats]) => {
        const CategoryIcon = CATEGORY_ICONS[category] || Shield
        return (
          <section key={category} aria-label={category} className="space-y-3">
            <div className="flex items-center gap-2">
              <h2 className="section-heading">
                <span className="section-heading__icon">
                  <CategoryIcon size={14} strokeWidth={2} />
                </span>
                {category}
              </h2>
              <span className="section-count">{feats.length}</span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-3">
              {feats.map((f) => {
                const route = FEATURE_ROUTES[f.code]
                const content = (
                  <>
                    <div className="feature-tile__icon">
                      <FeatureIcon name={f.icon} />
                    </div>
                    <div className="feature-tile__body">
                      <h3 className="feature-tile__title">{f.name}</h3>
                      {f.description && (
                        <p className="feature-tile__desc">{f.description}</p>
                      )}
                    </div>
                    {route && (
                      <ArrowUpRight size={16} strokeWidth={2} className="feature-tile__arrow" />
                    )}
                  </>
                )
                return route ? (
                  <Link key={f.id} to={route} className="feature-tile">
                    {content}
                  </Link>
                ) : (
                  <div key={f.id} className="feature-tile">
                    {content}
                  </div>
                )
              })}
            </div>
          </section>
        )
      })}
    </div>
  )
}

/**
 * Landing card for non-admin users. Mirrors the "You're signed in" card in
 * SignIn.tsx by design — the same two actions (continue / switch accounts)
 * show up in both places so users get a consistent mental model regardless
 * of where they land.
 */
function MemberLandingCard({ user }: { user: MmsUser }) {
  const displayName =
    [user.firstName, user.lastName].filter(Boolean).join(' ').trim() ||
    user.username ||
    user.email
  return (
    <div className="flex items-center justify-center py-8">
      <div className="w-full max-w-md">
        <div className="card p-8 space-y-6">
          <div className="text-center">
            <div className="w-12 h-12 rounded-full bg-brand-100 dark:bg-brand-900/50 flex items-center justify-center mx-auto mb-3">
              <UserCircle2 size={24} className="text-brand-600 dark:text-brand-400" />
            </div>
            <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">You're signed in</h1>
            <p className="mt-2 text-sm text-gray-600 dark:text-gray-400">
              as <span className="font-medium text-gray-800 dark:text-gray-200">{displayName}</span>
              {user.email && displayName !== user.email && (
                <span className="text-gray-500 dark:text-gray-400"> · {user.email}</span>
              )}
            </p>
          </div>

          <div className="flex flex-col gap-2">
            <a href={IDFY_HOME_URL} className="btn-primary w-full justify-center">
              <ArrowUpRight size={16} /> Continue to app
            </a>
            <Link to="/signout" className="btn-secondary w-full justify-center" replace>
              <LogOut size={16} /> Sign out and use a different account
            </Link>
          </div>
        </div>
      </div>
    </div>
  )
}
