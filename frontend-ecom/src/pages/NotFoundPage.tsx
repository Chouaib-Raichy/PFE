import { Link } from 'react-router-dom'
import { Breadcrumbs } from '../ui/Breadcrumbs'
import { Button } from '../ui/Button'
import { EmptyState } from '../ui/EmptyState'

export function NotFoundPage() {
  return (
    <section>
      <div className="page-head">
        <Breadcrumbs items={[{ label: 'Accueil', to: '/' }, { label: 'Introuvable' }]} />
      </div>
      <EmptyState
        icon="search"
        title="Page introuvable"
        message="La page demandée n'existe pas ou a été déplacée."
        actionLabel="Retour à l'accueil"
        onAction={() => (window.location.href = '/')}
      />
      <div className="center mt-6">
        <Link to="/catalog">
          <Button variant="outline">Explorer le catalogue</Button>
        </Link>
      </div>
    </section>
  )
}