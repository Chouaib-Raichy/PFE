import { Breadcrumbs } from '../ui/Breadcrumbs'
import { Button } from '../ui/Button'
import { EmptyState } from '../ui/EmptyState'
import { useAuth } from '../app/AuthProvider'

export function ForbiddenPage() {
  const { logout } = useAuth()
  return (
    <section>
      <div className="page-head">
        <Breadcrumbs items={[{ label: 'Accueil', to: '/' }, { label: 'Accès refusé' }]} />
      </div>
      <EmptyState
        tone="error"
        icon="shield"
        title="Accès refusé"
        message="Vous n'avez pas les droits nécessaires pour consulter cette ressource."
        actionLabel="Retour à l'accueil"
        onAction={() => (window.location.href = '/')}
      />
      <div className="center mt-6">
        <Button variant="outline" onClick={logout}>
          Se déconnecter et changer de compte
        </Button>
      </div>
    </section>
  )
}