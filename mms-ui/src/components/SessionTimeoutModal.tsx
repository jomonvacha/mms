import { useAuth } from '../hooks/useAuth'
import { AlertCircle } from 'lucide-react'

export default function SessionTimeoutModal() {
  const { idleWarning, idleCountdown, extendSession, confirmSignout } = useAuth()
  if (!idleWarning) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-gray-950/95 backdrop-blur-md" />
      <div className="relative bg-white dark:bg-gray-800 rounded-xl w-full max-w-sm shadow-2xl border border-gray-200 dark:border-gray-700 p-6 space-y-4">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-lg bg-amber-50 dark:bg-amber-900/30">
            <AlertCircle size={18} className="text-amber-600 dark:text-amber-400" />
          </div>
          <div>
            <h2 className="text-base font-semibold text-gray-900 dark:text-gray-100">Session Timeout</h2>
            <p className="text-xs text-gray-500 dark:text-gray-400">Inactivity detected</p>
          </div>
        </div>
        <p className="text-sm text-gray-600 dark:text-gray-300">
          Your session is about to expire due to inactivity.
        </p>
        <p className="text-sm text-gray-600 dark:text-gray-300">
          You will be signed out in{' '}
          <strong className="text-gray-900 dark:text-gray-100">{idleCountdown}</strong> seconds.
        </p>
        <div className="flex gap-2 justify-end">
          <button className="btn-secondary text-sm" onClick={confirmSignout}>
            Sign Out Now
          </button>
          <button className="btn-primary text-sm" onClick={extendSession}>
            Stay Signed In
          </button>
        </div>
      </div>
    </div>
  )
}
