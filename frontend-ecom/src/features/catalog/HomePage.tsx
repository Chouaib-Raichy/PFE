import { Link } from 'react-router-dom'
import { useAsync } from '../../lib/useAsync'
import { api } from '../../lib/api'
import { useCart } from '../../app/CartProvider'
import { useToast } from '../../ui/Toast'
import { ProductCard } from '../../components/ProductCard'
import { Button } from '../../ui/Button'
import { Icon } from '../../ui/Icon'
import { Skeleton } from '../../ui/Skeleton'
import { EmptyState } from '../../ui/EmptyState'
import type { Product } from '../../types'

export function HomePage() {
  const { data, loading, error, reload } = useAsync(async () => {
    const [categories, products] = await Promise.all([api.listCategories(), api.listProducts(0, 100)])
    return { categories, products: products.content.filter((p) => p.active) }
  }, [])

  const featured = data?.products.slice(0, 8) ?? []

  return (
    <>
      <section className="hero">
        <span className="hero-eyebrow">Nouvelle collection</span>
        <h1>Des essentiels tech, choisis avec soin.</h1>
        <p>
          Catalogue, panier et commandes propulsés par une architecture microservices sécurisée
          (Spring Boot, Keycloak, PostgreSQL &amp; MongoDB).
        </p>
        <div className="hero-actions">
          <Link to="/catalog">
            <Button variant="primary" size="lg">
              Explorer le catalogue
              <Icon name="arrowRight" size={18} />
            </Button>
          </Link>
        </div>
      </section>

      {data && data.categories.length > 0 && (
        <section className="mb-8">
          <div className="chips">
            {data.categories.map((c) => (
              <Link key={c.id} to={`/catalog?categoryId=${c.id}`} className="chip">
                {c.name}
              </Link>
            ))}
          </div>
        </section>
      )}

      <section>
        <div className="section-head">
          <div>
            <span className="eyebrow">Sélection</span>
            <h2>Produits en vedette</h2>
          </div>
          <Link to="/catalog" className="nav-link">
            Tout voir <Icon name="chevronRight" size={16} />
          </Link>
        </div>

        {loading && (
          <div className="product-grid">
            {Array.from({ length: 4 }).map((_, i) => (
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

        {!loading && !error && featured.length === 0 && (
          <EmptyState title="Catalogue vide" message="Aucun produit n'est disponible pour le moment." />
        )}

        {!loading && !error && featured.length > 0 && (
          <ProductGrid products={featured} />
        )}
      </section>
    </>
  )
}

/** Grille de produits avec ajout au panier branché sur l'état partagé. */
export function ProductGrid({ products }: { products: Product[] }) {
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
