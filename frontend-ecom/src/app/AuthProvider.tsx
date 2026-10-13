import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { api } from '../lib/api'
import keycloak from '../lib/auth'
import {
  isAdmin,
  isAuthenticated,
  login as kcLogin,
  logout as kcLogout,
  refreshAccessToken,
  register as kcRegister,
  tokenCustomerId,
  tokenDisplayName,
  tokenSubject,
} from '../lib/auth'

interface AuthState {
  authenticated: boolean
  username: string
  displayName: string
  admin: boolean
  /** Identifiant client porte par le jeton (null pour un admin sans profil client). */
  customerId: number | null
  hasCustomer: boolean
  /** Vrai pendant la creation automatique du profil client (nouvel inscrit). */
  provisioning: boolean
  /** Message d'erreur si le provisionnement a echoue (profil non cree). */
  provisionError: string
  login: (redirectPath?: string) => void
  register: (redirectPath?: string) => void
  logout: () => void
  retryProvision: () => void
}

const AuthContext = createContext<AuthState | null>(null)

/**
 * Expose l'identite issue du jeton Keycloak, reactive aux evenements de session
 * (connexion, rafraichissement, deconnexion) sans jamais reimplementer
 * l'authentification.
 *
 * <p>Un utilisateur fraichement inscrit n'a pas encore de profil client : le
 * fournisseur demande alors au serveur de le provisionner (a partir du seul
 * claim {@code sub}), rafraichit le jeton pour faire apparaitre le claim
 * {@code customer_id}, puis re-rend l'interface.</p>
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [version, setVersion] = useState(0)
  const [provisioning, setProvisioning] = useState(false)
  const [provisionError, setProvisionError] = useState('')
  // Evite de relancer le provisionnement a chaque rafraichissement de jeton.
  const attemptedFor = useRef<string | null>(null)

  useEffect(() => {
    const refresh = () => setVersion((v) => v + 1)
    keycloak.onAuthSuccess = refresh
    keycloak.onAuthRefreshSuccess = refresh
    keycloak.onAuthLogout = refresh
    keycloak.onTokenExpired = () => {
      void keycloak.updateToken(30).catch(() => undefined)
    }
    return () => {
      keycloak.onAuthSuccess = undefined
      keycloak.onAuthRefreshSuccess = undefined
      keycloak.onAuthLogout = undefined
      keycloak.onTokenExpired = undefined
    }
  }, [])

  // Provisionnement automatique du profil pour un client connecte sans profil.
  useEffect(() => {
    // version sert de signal d'invalidation (evenements de session Keycloak).
    void version
    if (!isAuthenticated() || isAdmin() || tokenCustomerId() !== null) return
    const sub = tokenSubject()
    if (!sub || attemptedFor.current === sub) return
    attemptedFor.current = sub
    setProvisioning(true)
    setProvisionError('')
    void (async () => {
      try {
        await api.provisionCustomer()
        await refreshAccessToken()
        setVersion((v) => v + 1)
      } catch (err) {
        attemptedFor.current = null
        setProvisionError((err as Error).message)
      } finally {
        setProvisioning(false)
      }
    })()
  }, [version])

  const retryProvision = useCallback(() => {
    attemptedFor.current = null
    setVersion((v) => v + 1)
  }, [])

  const value = useMemo<AuthState>(() => {
    void version
    const customerId = tokenCustomerId()
    return {
      authenticated: isAuthenticated(),
      username: tokenSubject(),
      displayName: tokenDisplayName(),
      admin: isAdmin(),
      customerId,
      hasCustomer: customerId !== null,
      provisioning,
      provisionError,
      login: kcLogin,
      register: kcRegister,
      logout: kcLogout,
      retryProvision,
    }
  }, [version, provisioning, provisionError, retryProvision])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth doit etre utilise dans <AuthProvider>')
  return ctx
}
