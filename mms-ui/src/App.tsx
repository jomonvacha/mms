import RoutesConfig from './routes'
import Navbar from './components/Navbar'
import SessionTimeoutModal from './components/SessionTimeoutModal'
import { useAuth } from './hooks/useAuth'
import { MMS_ADMIN_ROLES } from './pages/Members'

export default function App() {
  return (
    <div className="flex flex-col min-h-screen">
      <Navbar />
      <main className="flex-1 py-6">
        <div className="container mx-auto px-4 max-w-7xl">
          <RoutesConfig />
        </div>
      </main>
      <SessionTimeoutModal />
      <Footer />
    </div>
  )
}

// Footer is hidden for signed-in non-admin users, mirroring the navbar —
// their whole surface is the MemberLandingCard, and chrome that advertises
// "MMS — Member Management System" only reinforces they're in the wrong app.
function Footer() {
  const { user } = useAuth()
  const isPrivileged = (user?.roles || []).some((r) => MMS_ADMIN_ROLES.includes(r))
  if (user && !isPrivileged) return null
  return (
    <footer className="mt-auto py-3 border-t border-gray-200 dark:border-gray-700">
      <div className="container mx-auto px-4 text-center text-sm text-gray-500 dark:text-gray-400">
        MMS — Member Management System
      </div>
    </footer>
  )
}
