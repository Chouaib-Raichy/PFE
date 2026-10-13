import { Link, useNavigate } from 'react-router-dom'
import { useAsync } from '../../lib/useAsync'
import { api } from '../../lib/api'
import { formatDateTime, formatPrice } from '../../lib/format'
import { Breadcrumbs } from '../../ui/Breadcrumbs'
import { Badge } from '../../ui/Badge'
import { Button } from '../../ui/Button'
import { Icon } from '../../ui/Icon'
import { Skeleton } from '../../ui/Skeleton'
import { EmptyState } from '../../ui/EmptyState'
import type { Order, OrderStatus } from '../../types'

export function orderTone(status: OrderStatus) {
  return status === 'CONFIRMED' ? 'success' : 'danger'
}

export function orderLabel(status: OrderStatus) {
  return status === 'CONFIRMED' ? 'Confirmée' : 'Annulée'
}

export function OrdersPage() {
  const { data, loading, error, reload } = useAsync(() => api.listOrders(0, 100), [])
  const orders = data?.content ?? []
  const navigate = useNavigate()

  return (
    <section>
      <div className="page-head">
        <Breadcrumbs items={[{ label: 'Accueil', to: '/' }, { label: 'Commandes' }]} />
        <h1 className="mt-4">Mes commandes</h1>
        <p className="muted mt-2">{loading ? 'Chargement…' : `${orders.length} commande(s)`}</p>
      </div>

      {loading && (
        <div className="stack gap-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <div className="card card-pad stack gap-3" key={i}>
              <Skeleton width="30%" height={18} />
              <Skeleton width="100%" height={60} />
            </div>
          ))}
        </div>
      )}

      {!loading && error && (
        <EmptyState tone="error" message={error} actionLabel="Réessayer" onAction={reload} />
      )}

      {!loading && !error && orders.length === 0 && (
        <EmptyState
          icon="receipt"
          title="Aucune commande"
          message="Vous n'avez pas encore passé de commande."
          actionLabel="Découvrir la boutique"
          onAction={() => navigate('/catalog')}
        />
      )}

      {!loading && !error && orders.length > 0 && (
        <div className="stack gap-5">
          {orders.map((order) => (
            <OrderCard key={order.id} order={order} />
          ))}
        </div>
      )}
    </section>
  )
}

export function OrderCard({ order }: { order: Order }) {
  return (
    <article className="card card-hover card-pad order-card">
      <div className="order-head">
        <div className="row gap-3">
          <strong>Commande #{order.id}</strong>
          <Badge tone={orderTone(order.status)}>{orderLabel(order.status)}</Badge>
        </div>
        <span className="muted small">
          <Icon name="clock" size={14} /> {formatDateTime(order.createdAt)}
        </span>
      </div>

      <ul className="order-items-list">
        {order.items.slice(0, 3).map((item) => (
          <li key={item.productId}>
            <span>
              {item.productName} × {item.quantity}
            </span>
            <span>{formatPrice(item.subtotal)}</span>
          </li>
        ))}
        {order.items.length > 3 && (
          <li>
            <span className="muted">+ {order.items.length - 3} autre(s) article(s)…</span>
          </li>
        )}
      </ul>

      <div className="order-foot">
        <span className="muted small">{order.customerName}</span>
        <div className="row gap-4">
          <strong className="price price-small">{formatPrice(order.total)}</strong>
          <Link to={`/orders/${order.id}`}>
            <Button variant="outline" size="sm">
              Détails <Icon name="chevronRight" size={14} />
            </Button>
          </Link>
        </div>
      </div>
    </article>
  )
}