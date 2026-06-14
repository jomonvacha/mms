import { Navigate, useLocation } from 'react-router-dom'
import { Loader2, ShieldAlert } from 'lucide-react'
import { useAuth } from '../hooks/useAuth'

interface Props {
  children: React.ReactNode
  /** Roles that are allowed to access the route. The user only needs ONE of them. */
  roles: string[]
}

/**
 * Route guard that requires (1) an authenticated user AND (2) at least one of the
 * given roles. Unauthenticated visitors are redirected to /signin; authenticated
 * users without the right role get a friendly "no access" panel rather than a
 * silent redirect, so they understand why they can't see the page.
 */
export default function RoleProtectedRoute({ children, roles }: Props) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return (
      <div className="flex items-center justify-center py-24">
        <Loader2 size={32} className="animate-spin text-brand-600 dark:text-brand-400" />
      </div>
    )
  }

  if (!user) {
    return <Navigate to="/signin" replace state={{ from: location }} />
  }

  const userRoles = user.roles || []
  const allowed = roles.some((r) => userRoles.includes(r))
  if (!allowed) {
    return (
      <div className="flex items-center justify-center py-16">
        <div className="card p-8 max-w-md w-full text-center space-y-4">
          <div className="w-14 h-14 mx-auto rounded-full bg-amber-100 dark:bg-amber-900/40 flex items-center justify-center">
            <ShieldAlert size={28} className="text-amber-600 dark:text-amber-400" />
          </div>
          <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">Access denied</h2>
          <p className="text-sm text-gray-600 dark:text-gray-300">
            You don&apos;t have permission to view this page. This area requires one of the
            following roles: <span className="font-mono text-xs">{roles.join(', ')}</span>.
          </p>
          <p className="text-xs text-gray-500 dark:text-gray-400">
            If you believe this is a mistake, contact an administrator.
          </p>
        </div>
      </div>
    )
  }

  return <>{children}</>
}
