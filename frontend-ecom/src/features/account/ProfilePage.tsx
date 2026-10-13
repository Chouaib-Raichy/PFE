import { useAsync } from '../../lib/useAsync'
import { api } from '../../lib/api'
import { useAuth } from '../../app/AuthProvider'
import { formatDateTime } from '../../lib/format'
import { Breadcrumbs } from '../../ui/Breadcrumbs'
import { Badge } from '../../ui/Badge'
import { Icon } from '../../ui/Icon'
import { Skeleton } from '../../ui/Skeleton'
import { EmptyState } from '../../ui/EmptyState'

export function ProfilePage() {
  const { username, displayName, admin, customerId } = useAuth()
  const { data, loading, error, reload } = useAsync(
    async () => (customerId === null ? null : api.getCustomer(customerId)),
    [customerId],
  )

  return (
    <section>
      <div className="page-head">
        <Breadcrumbs items={[{ label: 'Accueil', to: '/' }, { label: 'Mon compte' }]} />
        <h1 className="mt-4">Mon compte</h1>
      </div>

      <div className="profile-cta">
        <div className="row gap-4">
          <span className="big-avatar">{displayName ? displayName.slice(0, 1).toLocaleUpperCase() : '?'}</span>
          <div>
            <h2 className="mb-1">{displayName}</h2>
            <p className="muted small">Connecté en tant que {username}</p>
          </div>
        </div>
        {admin ? <Badge tone="gold">Administrateur</Badge> : <Badge tone="success">Client</Badge>}
      </div>

      <div className="card card-pad mt-6">
        <h2 style={{ fontSize: 'var(--fs-lg)' }} className="mb-4">
          Profil client
        </h2>

        {loading && (
          <div className="stack gap-3">
            <Skeleton width="50%" height={18} />
            <Skeleton width="70%" height={18} />
          </div>
        )}

        {error && (
          <EmptyState
            tone="error"
            title="Profil indisponible"
            message={error}
            actionLabel="Réessayer"
            onAction={reload}
          />
        )}

        {!loading && !error && !customerId && (
          <div className="stack gap-2">
            <p className="muted">Aucun profil client n'est lié à ce compte.</p>
            <p className="muted small">
              {displayName} est un compte administrateur : vous gérez la boutique, sans panier ni commande personnels.
            </p>
          </div>
        )}

        {!loading && !error && data && (
          <dl className="detail-list">
            <div className="detail-row">
              <dt>Nom</dt>
              <dd>
                {data.firstName} {data.lastName}
              </dd>
            </div>
            <div className="detail-row">
              <dt>Email</dt>
              <dd>{data.email}</dd>
            </div>
            <div className="detail-row">
              <dt>Téléphone</dt>
              <dd>{data.phone || '—'}</dd>
            </div>
            <div className="detail-row">
              <dt>ID client</dt>
              <dd className="mono">#{data.id}</dd>
            </div>
            <div className="detail-row">
              <dt>Membre depuis</dt>
              <dd>{formatDateTime(data.createdAt)}</dd>
            </div>
          </dl>
        )}
      </div>

      <div className="card card-pad mt-6 stack gap-3">
        <h2 style={{ fontSize: 'var(--fs-lg)' }}>Sécurité &amp; authentification</h2>
        <p className="muted" style={{ maxWidth: '70ch' }}>
          L'authentification est déléguée à <strong>Keycloak</strong> (OpenID Connect) : votre session est validée par le{' '}
          <span className="mono small">Spring Cloud Gateway</span>. La déconnexion met fin à la session chez le
          fournisseur d'identité.
        </p>
        <p className="row gap-2 muted small">
          <Icon name="shield" size={14} /> Connexion valide pour : <Badge tone="gold">{admin ? 'ADMIN' : 'USER'}</Badge>
        </p>
      </div>
    </section>
  )
}