import type { AuthProvider } from '../../hooks/useAuth'

export function providerLabel(p?: AuthProvider): string {
  if (p === 'GOOGLE') return 'Google'
  if (p === 'APPLE') return 'Apple'
  return 'your identity provider'
}

export function isFederated(p?: AuthProvider): boolean {
  return p === 'GOOGLE' || p === 'APPLE'
}
