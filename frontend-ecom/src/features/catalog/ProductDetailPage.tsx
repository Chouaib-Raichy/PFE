import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAsync } from '../../lib/useAsync'
import { api } from '../../lib/api'
import { useCart } from '../../app/CartProvider'
import { useToast } from '../../ui/Toast'
import { formatPrice } from '../../lib/format'
import { Breadcrumbs } from '../../ui/Breadcrumbs'
import { Badge } from '../../ui/Badge'
import { Button } from '../../ui/Button'
import { Icon } from '../../ui/Icon'
import { Skeleton } from '../../ui/Skeleton'
import { EmptyState } from '../../ui/EmptyState'
import { ProductImage } from '../../ui/ProductImage'

export function ProductDetailPage() {
  const { id } = useParams()
  const productId = Number(id)
  const { data, loading, error, reload } = useAsync(() => api.getProduct(productId), [productId])

  const { addItem } = useCart()
  const { notify } = useToast()
  const [qty, setQty] = useState(1)
  const [adding, setAdding] = useState(false)
  useEffect(() => setQty(1), [id])

  if (loading) {
    return (
      <div className="pdp">
        <Skeleton height={420} radius="var(--r-xl)" />
        <div className="stack gap-4">
          <Skeleton width="30%" height={14} />
          <Skeleton width="80%" height={32} />
          <Skeleton width="40%" height={28} />
          <Skeleton width="100%" height={80} />
        </div>
      </div>
    )
  }

  if (error || !data) {
    const notFound = error.includes('404') || /introuvable|not found/i.test(error)
    return (
      <EmptyState
        tone="error"
        title={notFound ? 'Produit introuvable' : 'Erreur de chargement'}
        message={notFound ? "Ce produit n'existe pas ou n'est plus disponible." : error}
        actionLabel="Retour au catalogue"
        onAction={reload}
      />
    )
  }

  const product = data
  const outOfStock = product.stockQuantity <= 0

  const handleAdd = async () => {
    setAdding(true)
    try {
      await addItem(product, qty)
      notify(`« ${product.name} » ajouté au panier (×${qty})`)
    } finally {
      setAdding(false)
    }
  }

  return (
    <section>
      <Breadcrumbs
        items={[
          { label: 'Accueil', to: '/' },
          { label: 'Catalogue', to: '/catalog' },
          { label: product.name },
        ]}
      />

      <div className="pdp mt-6">
        <div className="pdp-media">
          <ProductImage src={product.imageUrl} alt={product.name} iconSize={72} />
        </div>

        <div className="pdp-info">
          {product.categoryId && (
            <Link to={`/catalog?categoryId=${product.categoryId}`} className="product-cat">
              {product.categoryName ?? 'Catégorie'}
            </Link>
          )}
          <h1>{product.name}</h1>

          <div className="row gap-3">
            <span className="pdp-price">{formatPrice(product.price)}</span>
            {outOfStock ? (
              <Badge tone="danger">Rupture de stock</Badge>
            ) : (
              <Badge tone="success">En stock ({product.stockQuantity})</Badge>
            )}
          </div>

          {product.description && <p className="muted">{product.description}</p>}

          <div className="pdp-actions">
            <div className="qty">
              <button onClick={() => setQty((q) => Math.max(1, q - 1))} aria-label="Diminuer">
                <Icon name="minus" size={16} />
              </button>
              <span aria-live="polite">{qty}</span>
              <button
                onClick={() => setQty((q) => Math.min(product.stockQuantity || 1, q + 1))}
                aria-label="Augmenter"
              >
                <Icon name="plus" size={16} />
              </button>
            </div>
            <Button variant="primary" size="lg" loading={adding} disabled={outOfStock} onClick={() => void handleAdd()}>
              <Icon name="cart" size={18} />
              Ajouter au panier
            </Button>
            <Link to="/cart">
              <Button variant="outline" size="lg">
                Voir le panier
              </Button>
            </Link>
          </div>

          <dl className="spec-list">
            <div className="spec-row">
              <dt>Référence</dt>
              <dd className="mono">#{product.id}</dd>
            </div>
            {product.categoryName && (
              <div className="spec-row">
                <dt>Catégorie</dt>
                <dd>{product.categoryName}</dd>
              </div>
            )}
            <div className="spec-row">
              <dt>Disponibilité</dt>
              <dd>{product.stockQuantity} unité(s)</dd>
            </div>
          </dl>
        </div>
      </div>
    </section>
  )
}
