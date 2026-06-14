import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { clearAuthTokens, signout } from '../api/client'

/**
 * Platform-wide sign-out. Clears MMS tokens on this UI and redirects.
 *
 * Cross-UI cleanup (idfy-ui etc.) is handled by each UI's own AuthProvider —
 * its visibilitychange listener clears local state when the access token
 * disappears from storage on tab refocus. An earlier version fired hidden
 * iframes at sibling UIs to force-clear their localStorage; that turned out
 * to be brittle (Safari doesn't fire onload/onerror reliably when the target
 * port has no listener, so the flow would hang on a fixed 1.5s safety timer
 * and the user would stare at a blank screen before the redirect kicked in).
 * The iframe dance is gone — signout is now a single in-flight request.
 */
export default function SignOut() {
  const location = useLocation()
  const redirect = new URLSearchParams(location.search).get('redirect') || ''

  useEffect(() => {
    // Fire-and-forget the server call — token blacklisting can finish in the
    // background. We redirect *immediately* after clearing local tokens so the
    // user never sees a hanging spinner, and so any flaky network/proxy layer
    // (e.g. Safari+Vite dev proxy combos that sometimes stall on POST) can't
    // block the UX.
    signout().catch(() => undefined)
    clearAuthTokens()

    const target = redirect
      ? `${redirect}${redirect.includes('?') ? '&' : '?'}signedOut=1`
      : '/signin'

    // replace() avoids leaving /signout in the back-stack — pressing Back from
    // /signin shouldn't re-trigger the sign-out flow.
    window.location.replace(target)
  }, [redirect])

  // Visible full-viewport spinner — the in-between moment when the React tree
  // is torn down for the hard navigation is very brief, but if for some reason
  // the API call stalls, this at least makes it clear the page is working.
  return (
    <div className="fixed inset-0 flex items-center justify-center bg-white dark:bg-gray-950 text-gray-600 dark:text-gray-300 gap-3">
      <Loader2 size={20} className="animate-spin" />
      <span className="text-sm font-medium">Signing you out…</span>
    </div>
  )
}
