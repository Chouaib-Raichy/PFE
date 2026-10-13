import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from './AuthProvider'

/**
 * Garde de navigation (UX uniquement). L'autorisation réelle reste côté serveur :
 * le gateway n'expose la création/modification/suppression qu'aux jetons ADMIN.
 */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const { admin } = useAuth()
  if (!admin) return <Navigate to="/403" replace />
  return <>{children}</>
}