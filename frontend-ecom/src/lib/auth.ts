import Keycloak from 'keycloak-js'

/**
 * Client OIDC (Authorization Code + PKCE) partagé par l'application.
 * Le navigateur s'exécute toujours sur l'hôte : une seule URL Keycloak
 * (http://localhost:8181) est donc valable en développement comme en Docker.
 */
const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8181',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'pfeecom',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'pfeecom-frontend',
})

export default keycloak

/** Initialise la session sans forcer la connexion (navigation invite autorisee). */
export async function initAuth(): Promise<boolean> {
  return keycloak.init({
    onLoad: 'check-sso',
    pkceMethod: 'S256',
    checkLoginIframe: false,
    silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
  })
}

/** Jeton d'accès courant, rafraîchi si expiré sous peu. */
export async function getAccessToken(): Promise<string> {
  await keycloak.updateToken(30)
  return keycloak.token ?? ''
}

/** Force le renouvellement du jeton (utilisé après un 401). */
export async function refreshAccessToken(): Promise<string> {
  await keycloak.updateToken(-1)
  return keycloak.token ?? ''
}

export function isAdmin(): boolean {
  return keycloak.hasRealmRole('ADMIN')
}

/** Identifiant de connexion (preferred_username). */
export function tokenSubject(): string {
  const claims = keycloak.tokenParsed as Record<string, unknown> | undefined
  return String(claims?.preferred_username ?? '')
}

/** Nom complet issu du jeton (fallback : identifiant de connexion). */
export function tokenDisplayName(): string {
  const claims = keycloak.tokenParsed as Record<string, unknown> | undefined
  const name = claims?.name
  return name ? String(name) : tokenSubject()
}

/** Identifiant client lié à l'utilisateur (claim « customer_id »). */
export function tokenCustomerId(): number | null {
  const claims = keycloak.tokenParsed as Record<string, unknown> | undefined
  const raw = claims?.customer_id
  const parsed = raw === undefined || raw === null ? NaN : Number(raw)
  return Number.isFinite(parsed) ? parsed : null
}

/** Vrai si une session Keycloak valide est ouverte. */
export function isAuthenticated(): boolean {
  return keycloak.authenticated === true
}

/** Destination de retour : chemin courant par defaut, jamais une URL absolue
 *  fournie par un tiers (protection contre les redirections ouvertes). */
function safeRedirect(redirectPath?: string): string {
  if (redirectPath && redirectPath.startsWith('/') && !redirectPath.startsWith('//')) {
    return window.location.origin + redirectPath
  }
  return window.location.origin + window.location.pathname + window.location.search
}

/** Redirige vers la page de connexion Keycloak (Authorization Code + PKCE). */
export function login(redirectPath?: string): void {
  void keycloak.login({ redirectUri: safeRedirect(redirectPath) })
}

/** Redirige vers l'inscription Keycloak (realm doit l'autoriser). */
export function register(redirectPath?: string): void {
  void keycloak.register({ redirectUri: safeRedirect(redirectPath) })
}

export function logout(): void {
  void keycloak.logout({ redirectUri: window.location.origin })
}
