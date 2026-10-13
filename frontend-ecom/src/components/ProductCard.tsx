import { Link } from 'react-router-dom'
import type { Product } from '../types'
import { formatPrice } from '../lib/format'
import { Badge } from '../ui/Badge'
import { Button } from '../ui/Button'
import { Icon } from '../ui/Icon'
import { HighlightedText } from '../ui/HighlightedText'
import { ProductImage } from '../ui/ProductImage'

interface ProductCardProps {
  product: Product
  onAdd?: (product: Product) => void
  /** Extrait surligne du nom (recherche Elasticsearch), rendu en toute securite. */
  nameHighlight?: string
}

export function ProductCard({ product, onAdd, nameHighlight }: ProductCardProps) {
  const outOfStock = product.stockQuantity <= 0
  return (
    <article className="card card-hover product-card">
      <Link to={`/products/${product.id}`} className="product-thumb" aria-label={product.name}>
        <ProductImage src={product.imageUrl} alt={product.name} />
        <span className="stock-tag">
          {outOfStock ? (
            <Badge tone="danger">Rupture</Badge>
          ) : product.stockQuantity <= 5 ? (
            <Badge tone="gold">Plus que {product.stockQuantity}</Badge>
          ) : null}
        </span>
      </Link>
      <div className="product-body">
        {product.categoryName && <span className="product-cat">{product.categoryName}</span>}
        <Link to={`/products/${product.id}`}>
          <h3 className="product-name">
            {nameHighlight ? <HighlightedText text={nameHighlight} /> : product.name}
          </h3>
        </Link>
        {product.description && <p className="product-desc">{product.description}</p>}
        <div className="product-foot">
          <span className="price">{formatPrice(product.price)}</span>
          {onAdd && (
            <Button
              variant="primary"
              size="sm"
              disabled={outOfStock}
              onClick={() => onAdd(product)}
              aria-label={`Ajouter ${product.name} au panier`}
            >
              <Icon name="plus" size={15} />
              Ajouter
            </Button>
          )}
        </div>
      </div>
    </article>
  )
}
