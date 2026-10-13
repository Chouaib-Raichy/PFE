import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAsync } from '../../lib/useAsync'
import { api } from '../../lib/api'
import { useCart } from '../../app/CartProvider'
import { useToast } from '../../ui/Toast'
import { ProductCard } from '../../components/ProductCard'
import { Skeleton } from '../../ui/Skeleton'
import { EmptyState } from '../../ui/EmptyState'
import { Pagination } from '../../ui/Pagination'
import { Breadcrumbs } from '../../ui/Breadcrumbs'
import type { Product } from '../../types'

const PAGE_SIZE = 12

export function CatalogPage() {
  const [params, setParams] = useSearchParams()
  const categoryParam = params.get('categoryId')
  const activeCategoryId = categoryParam ? Number(categoryParam) : 'all'
  const [page, setPage] = useState(0)

  const { data, loading, error, reload } = useAsync(async () => {
    const [categories, products] = await Promise.all([api.listCategories(), api.listProducts(0, 100)])
    return { categories, products: products.content.filter((p) => p.active) }
  }, [])

  const filtered = useMemo(() => {
    if (!data) return []
    return activeCategoryId === 'all'
      ? data.products
      : data.products.filter((p) => p.categoryId === activeCategoryId)
  }, [data, activeCategoryId])

  const totalPages = Math.ceil(filtered.length / PAGE_SIZE)
  const pageItems = filtered.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE)
  const activeCategory = data?.categories.find((c) => c.id === activeCategoryId)

  const selectCategory = (id: number | 'all') => {
    setPage(0)
    if (id === 'all') setParams({})
    else setParams({ categoryId: String(id) })
  }

  return (
    <section>
      <div className="page-head">
        <Breadcrumbs items={[{ label: 'Accueil', to: '/' }, { label: 'Catalogue' }]} />
        <h1 className="mt-4">{activeCategory ? activeCategory.name : 'Catalogue'}</h1>
        <p className="muted mt-2">
          {loading ? 'Chargement…' : `${filtered.length} produit(s)`}
          {activeCategory?.description ? ` — ${activeCategory.description}` : ''}
        </p>
      </div>

      {data && data.categories.length > 0 && (
        <div className="chips mb-6" role="tablist" aria-label="Filtrer par catégorie">
          <button
            className={`chip${activeCategoryId === 'all' ? ' active' : ''}`}
            onClick={() => selectCategory('all')}
            role="tab"
            aria-selected={activeCategoryId === 'all'}
          >
            Toutes
          </button>
          {data.categories.map((c) => (
            <button
              key={c.id}
              className={`chip${activeCategoryId === c.id ? ' active' : ''}`}
              onClick={() => selectCategory(c.id)}
              role="tab"
              aria-selected={activeCategoryId === c.id}
            >
              {c.name}
            </button>
          ))}
        </div>
      )}

      {loading && (
        <div className="product-grid">
          {Array.from({ length: 8 }).map((_, i) => (
            <div className="card" key={i}>
              <Skeleton height={180} radius="0" />
              <div className="card-pad stack gap-3">
                <Skeleton width="40%" height={12} />
                <Skeleton width="80%" height={18} />
                <Skeleton width="30%" height={18} />
              </div>
            </div>
          ))}
        </div>
      )}

      {!loading && error && (
        <EmptyState tone="error" message={error} actionLabel="Réessayer" onAction={reload} />
      )}

      {!loading && !error && pageItems.length === 0 && (
        <EmptyState
          title="Aucun produit"
          message="Aucun produit ne correspond à cette catégorie pour le moment."
          actionLabel={activeCategoryId !== 'all' ? 'Voir tout le catalogue' : undefined}
          onAction={activeCategoryId !== 'all' ? () => selectCategory('all') : undefined}
        />
      )}

      {!loading && !error && pageItems.length > 0 && <CatalogGrid products={pageItems} />}

      <Pagination page={page} totalPages={totalPages} onChange={setPage} />

      {data && data.categories.length > 0 && activeCategoryId === 'all' && (
        <p className="center muted small mt-8">
          Astuce : filtrez par catégorie ci-dessus ou utilisez la{' '}
          <Link to="/search" style={{ color: 'var(--gold-strong)' }}>
            recherche
          </Link>
          .
        </p>
      )}
    </section>
  )
}

function CatalogGrid({ products }: { products: Product[] }) {
  const { addItem } = useCart()
  const { notify } = useToast()

  const handleAdd = async (product: Product) => {
    await addItem(product, 1)
    notify(`« ${product.name} » ajouté au panier`)
  }

  return (
    <div className="product-grid">
      {products.map((p) => (
        <ProductCard key={p.id} product={p} onAdd={(prod) => void handleAdd(prod)} />
      ))}
    </div>
  )
}
