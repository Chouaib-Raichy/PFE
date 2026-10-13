import { useAsync } from '../../lib/useAsync'
import { api } from '../../lib/api'
import { Breadcrumbs } from '../../ui/Breadcrumbs'
import { CardLink } from '../../ui/CardLink'
import { Icon } from '../../ui/Icon'
import { Skeleton } from '../../ui/Skeleton'

const MODULES = [
  {
    to: '/admin_panel/products',
    title: 'Produits',
    desc: 'Créer, modifier, activer/désactiver et suivre les stocks.',
    icon: 'box' as const,
  },
  {
    to: '/admin_panel/categories',
    title: 'Catégories',
    desc: 'Gérer la classification du catalogue.',
    icon: 'tag' as const,
  },
  {
    to: '/admin_panel/customers',
    title: 'Clients',
    desc: 'Consulter et administrer les fiches clients.',
    icon: 'users' as const,
  },
  {
    to: '/admin_panel/orders',
    title: 'Commandes',
    desc: 'Suivre les commandes passées sur la boutique.',
    icon: 'receipt' as const,
  },
]

export function AdminHomePage() {
  const { data, loading, error, reload } = useAsync(async () => {
    const [products, categories, customers, orders] = await Promise.all([
      api.listProducts(0, 1),
      api.listCategories(),
      api.listCustomers(0, 1),
      api.listOrders(0, 1),
    ])
    return {
      products: products.totalElements,
      categories: categories.length,
      customers: customers.totalElements,
      orders: orders.totalElements,
    }
  }, [])

  return (
    <section>
      <div className="page-head">
        <Breadcrumbs items={[{ label: 'Accueil', to: '/' }, { label: 'Administration' }]} />
        <h1 className="mt-4">Espace d'administration</h1>
        <p className="muted mt-2">Pilotage du catalogue, des clients et des commandes.</p>
      </div>

      {error && (
        <div className="banner banner-error mb-6">
          <Icon name="alert" size={16} />
          <span className="grow">{error}</span>
          <button className="btn btn-sm btn-outline" onClick={reload}>
            Réessayer
          </button>
        </div>
      )}

      <div className="stat-grid">
        <Stat label="Produits" value={data?.products} icon="box" loading={loading} />
        <Stat label="Catégories" value={data?.categories} icon="tag" loading={loading} />
        <Stat label="Clients" value={data?.customers} icon="users" loading={loading} />
        <Stat label="Commandes" value={data?.orders} icon="receipt" loading={loading} />
      </div>

      <h2 className="mt-8 mb-4" style={{ fontSize: 'var(--fs-lg)' }}>
        Modules
      </h2>
      <div className="stat-grid">
        {MODULES.map((m) => (
          <CardLink key={m.to} to={m.to} title={m.title} desc={m.desc} icon={m.icon} />
        ))}
      </div>
    </section>
  )
}

function Stat({
  label,
  value,
  icon,
  loading,
}: {
  label: string
  value?: number
  icon: 'box' | 'tag' | 'users' | 'receipt'
  loading: boolean
}) {
  return (
    <div className="card card-pad stat">
      <span className="stat-icon">
        <Icon name={icon} size={18} />
      </span>
      <div>
        <div className="stat-value">
          {loading || value === undefined ? <Skeleton width={40} height={26} /> : value}
        </div>
        <div className="stat-label">{label}</div>
      </div>
    </div>
  )
}
