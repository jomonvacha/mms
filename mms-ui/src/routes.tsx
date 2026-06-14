import { Route, Routes, Navigate } from 'react-router-dom'
import SignIn from './pages/SignIn'
import SsoCheck from './pages/SsoCheck'
import Members from './pages/Members'
import SignOut from './pages/SignOut'
import AdminUsers from './pages/AdminUsers'
import AdminFeatures from './pages/AdminFeatures'
import AdminLocale from './pages/AdminLocale'
import AdminRules from './pages/AdminRules'
import AdminGovernance from './pages/AdminGovernance'
import AdminInvites from './pages/AdminInvites'
import AdminModels from './pages/AdminModels'
import AdminSettings from './pages/AdminSettings'
import NotFound from './pages/NotFound'
import ForgotPassword from './pages/ForgotPassword'
import ResetPassword from './pages/ResetPassword'
import VerifyEmail from './pages/VerifyEmail'
import ProtectedRoute from './components/ProtectedRoute'
import RoleProtectedRoute from './components/RoleProtectedRoute'

export default function RoutesConfig() {
  return (
    <Routes>
      {/* No home page — root redirects to My Access (auth required) */}
      <Route path="/" element={<ProtectedRoute><Members /></ProtectedRoute>} />
      <Route path="/signin" element={<SignIn />} />
      <Route path="/sso/check" element={<SsoCheck />} />
      <Route path="/signup" element={<Navigate to="/signin" replace />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route path="/verify-email" element={<VerifyEmail />} />
      <Route path="/members" element={<Navigate to="/" replace />} />
      <Route
        path="/admin/users"
        element={
          <RoleProtectedRoute roles={['ROLE_ADMIN', 'ROLE_MODERATOR', 'ROLE_MANAGER']}>
            <AdminUsers />
          </RoleProtectedRoute>
        }
      />
      <Route
        path="/admin/features"
        element={
          <RoleProtectedRoute roles={['ROLE_ADMIN', 'ROLE_MANAGER']}>
            <AdminFeatures />
          </RoleProtectedRoute>
        }
      />
      <Route
        path="/admin/locale"
        element={
          <RoleProtectedRoute roles={['ROLE_ADMIN']}>
            <AdminLocale />
          </RoleProtectedRoute>
        }
      />
      <Route
        path="/admin/rules"
        element={
          <RoleProtectedRoute roles={['ROLE_ADMIN']}>
            <AdminRules />
          </RoleProtectedRoute>
        }
      />
      <Route
        path="/admin/governance"
        element={
          <RoleProtectedRoute roles={['ROLE_ADMIN', 'ROLE_MANAGER']}>
            <AdminGovernance />
          </RoleProtectedRoute>
        }
      />
      <Route
        path="/admin/models"
        element={
          <RoleProtectedRoute roles={['ROLE_ADMIN', 'ROLE_MANAGER']}>
            <AdminModels />
          </RoleProtectedRoute>
        }
      />
      <Route
        path="/admin/invites"
        element={
          <RoleProtectedRoute roles={['ROLE_ADMIN']}>
            <AdminInvites />
          </RoleProtectedRoute>
        }
      />
      <Route
        path="/admin/settings"
        element={
          <RoleProtectedRoute roles={['ROLE_ADMIN']}>
            <AdminSettings />
          </RoleProtectedRoute>
        }
      />
      <Route path="/signout" element={<SignOut />} />
      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
