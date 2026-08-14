import { Link } from 'react-router-dom'
import { Shield, ShieldCheck, Smartphone, Loader2, Settings, Users } from 'lucide-react'
import { useAuth } from '../hooks/useAuth'
import { useMemo } from 'react'

export default function Home() {
  const { user, loading } = useAuth()

  const isPrivileged = useMemo(() => {
    const roles = user?.roles || []
    return roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MODERATOR') || roles.includes('ROLE_MANAGER')
  }, [user])

  return (
    <div className="space-y-8">
      {/* Hero */}
      <div className="card p-8 md:p-12 text-center space-y-4">
        <div className="w-16 h-16 rounded-full bg-brand-100 dark:bg-brand-900/50 flex items-center justify-center mx-auto">
          <Shield size={32} className="text-brand-600 dark:text-brand-400" />
        </div>
        <h1 className="text-3xl font-bold text-[rgb(var(--text-primary))]">
          Welcome to MMS
        </h1>
        <p className="text-[rgb(var(--text-muted))] max-w-xl mx-auto">
          Member Management System — manage users, roles, features, and access control
          for the Roots platform.
        </p>
        {loading ? (
          <div className="flex justify-center">
            <Loader2 size={24} className="animate-spin text-brand-600 dark:text-brand-400" />
          </div>
        ) : user ? (
          <div className="flex gap-3 justify-center flex-wrap">
            <Link to="/members" className="btn-primary">
              <Shield size={16} /> My Access
            </Link>
            {isPrivileged && (
              <>
                <Link to="/admin/users" className="btn-secondary">
                  <Users size={16} /> Users
                </Link>
                <Link to="/admin/features" className="btn-secondary">
                  <Settings size={16} /> Roles &amp; Features
                </Link>
              </>
            )}
          </div>
        ) : (
          <div className="flex gap-3 justify-center">
            <Link to="/signin" className="btn-primary">Sign In</Link>
            <Link to="/signup" className="btn-secondary">Sign Up</Link>
          </div>
        )}
      </div>

      {/* Feature cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="card p-6 space-y-2">
          <div className="w-10 h-10 rounded-lg bg-brand-100 dark:bg-brand-900/50 flex items-center justify-center">
            <ShieldCheck size={20} className="text-brand-600 dark:text-brand-400" />
          </div>
          <h3 className="font-semibold text-[rgb(var(--text-primary))]">Authentication &amp; 2FA</h3>
          <p className="text-sm text-[rgb(var(--text-muted))]">
            JWT auth with refresh tokens, Google OAuth, TOTP two-factor, rate limiting, and password reset.
          </p>
        </div>
        <div className="card p-6 space-y-2">
          <div className="w-10 h-10 rounded-lg bg-brand-100 dark:bg-brand-900/50 flex items-center justify-center">
            <Shield size={20} className="text-brand-600 dark:text-brand-400" />
          </div>
          <h3 className="font-semibold text-[rgb(var(--text-primary))]">Roles &amp; Features</h3>
          <p className="text-sm text-[rgb(var(--text-muted))]">
            Dynamic role management with feature-based access control. Create custom roles and assign features via an admin matrix.
          </p>
        </div>
        <div className="card p-6 space-y-2">
          <div className="w-10 h-10 rounded-lg bg-brand-100 dark:bg-brand-900/50 flex items-center justify-center">
            <Smartphone size={20} className="text-brand-600 dark:text-brand-400" />
          </div>
          <h3 className="font-semibold text-[rgb(var(--text-primary))]">Enterprise Standard</h3>
          <p className="text-sm text-[rgb(var(--text-muted))]">
            Federated account management, Swagger API docs, email verification, and audit-ready user lifecycle.
          </p>
        </div>
      </div>
    </div>
  )
}
