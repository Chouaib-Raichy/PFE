import { useEffect, type ReactNode } from 'react'
import { useLocation } from 'react-router-dom'
import { useAuth } from './AuthProvider'

/**
 * Garde de navigation : redirige un visiteur non authentifie vers Keycloak en
 * conservant la destination, puis le ramene au chemin initial.
 * L'autorisation reelle reste cote serveur (le gateway valide le jeton).
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { authenticated, login } = useAuth()
  const location = useLocation()
  const target = location.pathname + location.search

  useEffect(() => {
    if (!authenticated) login(target)
  }, [authenticated, login, target])

  if (!authenticated) {
    return (
      <div className="splash">
        <div className="brand">
          <span className="brand-mark">S</span>
          <span className="brand-name">ShopPFE</span>
        </div>
        <p className="muted small">Redirection vers la connexion…</p>
      </div>
    )
  }
  return <>{children}</>
}
