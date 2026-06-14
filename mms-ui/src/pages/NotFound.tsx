import { Link } from 'react-router-dom'
import { Home, MapPinOff } from 'lucide-react'

export default function NotFound() {
  return (
    <div className="flex items-center justify-center py-20">
      <div className="card p-10 max-w-md w-full text-center space-y-5">
        <div className="w-16 h-16 mx-auto rounded-full bg-brand-100 dark:bg-brand-900/40 flex items-center justify-center">
          <MapPinOff size={32} className="text-brand-600 dark:text-brand-400" />
        </div>
        <div>
          <p className="text-5xl font-bold text-gray-900 dark:text-gray-100 mb-1">404</p>
          <h1 className="text-lg font-semibold text-gray-900 dark:text-gray-100">Page not found</h1>
          <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">
            We couldn&apos;t find what you&apos;re looking for. The link may be broken or the page may have moved.
          </p>
        </div>
        <Link to="/" className="btn-primary inline-flex items-center gap-1.5 text-sm">
          <Home size={15} /> Go home
        </Link>
      </div>
    </div>
  )
}
