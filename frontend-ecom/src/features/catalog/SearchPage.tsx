import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAsync } from '../../lib/useAsync'
import { api } from '../../lib/api'
import { useCart } from '../../app/CartProvider'
import { useToast } from '../../ui/Toast'
import { ProductCard } from '../../components/ProductCard'
import { Skeleton } from '../../ui/Skeleton'
import { EmptyState } from '../../ui/EmptyState'
import { Breadcrumbs } from '../../ui/Breadcrumbs'
import type { ProductSearchHit, SearchPage as SearchPageResult } from '../../types'

const PAGE_SIZE = 24

/**
 * Recherche serveur (Elasticsearch via le gateway, repli PostgreSQL transparent).
 * Les termes correspondants sont surlignes cote serveur ; le rendu est securise
 * (aucun HTML injecte, voir HighlightedText).
 */
export function SearchPage() {
  const [params] = useSearchParams()
  const q = (params.get('q') ?? '').trim()
  const navigate = useNavigate()

  const { data, loading, error, reload } = useAsync<SearchPageResult | null>(
    () => (q ? api.searchProducts(q, { size: PAGE_SIZE }) : Promise.resolve(null)),
    [q],
  )

  const results = data?.content ?? []
  const degraded = data?.degraded ?? false

  return (
    <section>
      <div className="page-head">
        <Breadcrumbs items={[{ label: 'Accueil', to: '/' }, { label: 'Recherche' }]} />
        <h1 className="mt-4">{q ? `Résultats pour « ${q} »` : 'Recherche'}</h1>
        <p className="muted mt-2">
          {loading ? 'Chargement…' : `${data?.totalElements ?? results.length} résultat(s)`}
        </p>
      </div>

      {degraded && !loading && !error && (
        <p className="notice notice-warn mb-6">
          Recherche simplifiée : le moteur de recherche est momentanément indisponible, les
          résultats proviennent du catalogue principal.
        </p>
      )}

      {loading && (
        <div className="product-grid">
          {Array.from({ length: 6 }).map((_, i) => (
            <div className="card" key={i}>
              <Skeleton height={180} radius="0" />
              <div className="card-pad stack gap-3">
                <Skeleton width="40%" height={12} />
                <Skeleton width="80%" height={18} />
              </div>
            </div>
          ))}
        </div>
      )}

      {!loading && error && (
        <EmptyState tone="error" message={error} actionLabel="Réessayer" onAction={reload} />
      )}

      {!loading && !error && q && results.length === 0 && (
        <EmptyState
          title="Aucun résultat"
          message={`Aucun produit ne correspond à « ${q} ». Essayez un autre terme.`}
          actionLabel="Voir tout le catalogue"
          onAction={() => navigate('/catalog')}
        />
      )}

      {!loading && !error && !q && (
        <EmptyState
          message="Saisissez un terme de recherche pour trouver un produit."
          actionLabel="Parcourir le catalogue"
          onAction={() => navigate('/catalog')}
        />
      )}

      {!loading && !error && results.length > 0 && <SearchGrid products={results} />}
    </section>
  )
}

function SearchGrid({ products }: { products: ProductSearchHit[] }) {
  const { addItem } = useCart()
  const { notify } = useToast()

  const handleAdd = async (product: ProductSearchHit) => {
    await addItem(product, 1)
    notify(`« ${product.name} » ajouté au panier`)
  }

  return (
    <div className="product-grid">
      {products.map((p) => (
        <ProductCard
          key={p.id}
          product={p}
          onAdd={(prod) => void handleAdd(prod as ProductSearchHit)}
          nameHighlight={p.highlights?.name}
        />
      ))}
    </div>
  )
}
