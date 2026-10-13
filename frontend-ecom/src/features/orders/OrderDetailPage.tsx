import { Link, useParams } from 'react-router-dom'
import { useAsync } from '../../lib/useAsync'
import { api } from '../../lib/api'
import { formatDateTime, formatPrice } from '../../lib/format'
import { Breadcrumbs } from '../../ui/Breadcrumbs'
import { Badge } from '../../ui/Badge'
import { Button } from '../../ui/Button'
import { Skeleton } from '../../ui/Skeleton'
import { EmptyState } from '../../ui/EmptyState'
import { orderLabel, orderTone } from './OrdersPage'

export function OrderDetailPage({ admin = false }: { admin?: boolean }) {
  const { id } = useParams()
  const orderId = Number(id)
  const { data, loading, error, reload } = useAsync(() => api.getOrder(orderId), [orderId])
  const listPath = admin ? '/admin_panel/orders' : '/orders'

  if (loading) {
    return (
      <div className="card card-pad stack gap-4">
        <Skeleton width="30%" height={24} />
        <Skeleton width="100%" height={120} />
        <Skeleton width="40%" height={24} />
      </div>
    )
  }

  if (error || !data) {
    return (
      <EmptyState
        tone="error"
        title="Commande introuvable"
        message={error || "Cette commande n'existe pas ou est inaccessible."}
        actionLabel={admin ? 'Retour aux commandes' : 'Voir mes commandes'}
        onAction={reload}
      />
    )
  }

  const order = data
  const paid = order.paymentStatus === 'PAID'

  return (
    <section>
      <Breadcrumbs
        items={[
          admin
            ? { label: 'Administration', to: '/admin_panel' }
            : { label: 'Accueil', to: '/' },
          { label: 'Commandes', to: listPath },
          { label: `Commande #${order.id}` },
        ]}
      />

      <div className="card card-pad mt-6">
        <div className="between wrap gap-4">
          <div>
            <h1 className="mb-2">Commande #{order.id}</h1>
            <span className="muted small">
              Passée le {formatDateTime(order.createdAt)} — {order.customerName} · {order.customerEmail}
            </span>
          </div>
          <Badge tone={orderTone(order.status)}>{orderLabel(order.status)}</Badge>
        </div>
        <div className="row gap-2 mt-4 wrap">
          <span className="muted small">Paiement :</span>
          <Badge tone="neutral">Paiement à la livraison — الدفع عند الاستلام</Badge>
          <Badge tone={paid ? 'success' : 'neutral'}>
            {paid ? 'Payée' : 'En attente (à la livraison)'}
          </Badge>
        </div>
      </div>

      <div className="card card-pad mt-6">
        <h2 style={{ fontSize: 'var(--fs-lg)' }} className="mb-4">
          Articles
        </h2>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Produit</th>
                <th>Prix unitaire</th>
                <th>Quantité</th>
                <th>Total</th>
              </tr>
            </thead>
            <tbody>
              {order.items.map((item) => (
                <tr key={item.productId}>
                  <td style={{ whiteSpace: 'normal' }}>{item.productName}</td>
                  <td>{formatPrice(item.unitPrice)}</td>
                  <td>× {item.quantity}</td>
                  <td className="semibold">{formatPrice(item.subtotal)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="between mt-6 wrap gap-4">
          <Link to={listPath}>
            <Button variant="outline">{admin ? '← Retour aux commandes' : '← Retour à mes commandes'}</Button>
          </Link>
          <div className="row gap-4">
            <span className="muted">Total</span>
            <strong className="price" style={{ fontSize: 'var(--fs-2xl)' }}>
              {formatPrice(order.total)}
            </strong>
          </div>
        </div>
      </div>
    </section>
  )
}
      