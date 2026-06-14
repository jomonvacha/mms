import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { Loader2 } from 'lucide-react'

/**
 * Silent SSO check endpoint.
 * Other platform UIs redirect here with ?redirect=RETURN_URL.
 * If the user is authenticated, redirects back with tokens.
 * If not, redirects back without tokens (no sign-in form shown).
 * This enables automatic session detection across platform UIs.
 */
export default function SsoCheck() {
  const { user, loading } = useAuth()
  const location = useLocation()
  const redirect = new URLSearchParams(location.search).get('redirect') || ''

  useEffect(() => {
    if (loading || !redirect) return

    if (user) {
      // User is authenticated — send tokens back
      try {
        const saved = JSON.parse(localStorage.getItem('platform_auth') || '{}')
        if (saved.accessToken) {
          const sep = redirect.includes('?') ? '&' : '?'
          window.location.href = `${redirect}${sep}token=${encodeURIComponent(saved.accessToken)}&refreshToken=${encodeURIComponent(saved.refreshToken || '')}`
          return
        }
      } catch (_) {}
    }

    // Not authenticated — redirect back without tokens
    window.location.href = redirect
  }, [user, loading, redirect])

  return (
    <div className="flex items-center justify-center py-24">
      <Loader2 size={24} className="animate-spin text-brand-500" />
    </div>
  )
}
