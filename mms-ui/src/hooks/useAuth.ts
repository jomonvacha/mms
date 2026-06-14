import { createContext, useContext } from 'react'

export type AuthProvider = 'LOCAL' | 'GOOGLE' | 'APPLE'

export interface MmsUser {
  id: string
  username: string
  email: string
  firstName?: string
  lastName?: string
  phoneNumber?: string
  roles?: string[]
  active?: boolean
  hasPassword?: boolean
  /** Identity provider that owns this account. */
  provider?: AuthProvider
  /** True if the user has clicked the email verification link. */
  emailVerified?: boolean
  /** True if TOTP 2FA is enabled. */
  twoFactorEnabled?: boolean
}

export interface AuthContextValue {
  user: MmsUser | null
  loading: boolean
  error: string | null
  refreshMe: () => Promise<void>
  idleWarning: boolean
  idleCountdown: number
  extendSession: () => void
  confirmSignout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue>({
  user: null,
  loading: true,
  error: null,
  refreshMe: async () => {},
  idleWarning: false,
  idleCountdown: 0,
  extendSession: () => {},
  confirmSignout: async () => {},
})

export function useAuth() {
  return useContext(AuthContext)
}
