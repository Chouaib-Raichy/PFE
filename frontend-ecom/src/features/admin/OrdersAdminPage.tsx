import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAsync } from '../../lib/useAsync'
import { api } from '../../lib/api'
import { formatDateTime, formatPrice } from '../../lib/format'
import { Breadcrumbs } from '../../ui/Breadcrumbs'
import { Badge } from '../../ui/Badge'
import { Icon } from '../../ui/Icon'
import { EmptyState } from '../../ui/EmptyState'
import { Skeleton } from '../../ui/Skeleton'
import { orderLabel, orderTone } from '../orders/OrdersPage'
import type { OrderStatus } from '../../types'

type StatusFilter = 'ALL' | OrderStatus

export function OrdersAdminPage() {
  const { data, loading, error, reload } = useAsync(() => api.listOrders(0, 100), [])

  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<StatusFilter>('ALL')

  const orders = useMemo(() => data?.content ?? [], [data])
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    return orders.filter((o) => {
      if (status !== 'ALL' && o.status !== status) return false
      if (!q) return true
      return (
        String(o.id).includes(q) ||
        o.customerName.toLowerCase().includes(q) ||
        o.customerEmail.toLowerCase().includes(q)
      )
    })
  }, [orders, query, status])

  const counts = useMemo(
    () => ({
      all: orders.length,
      confirmed: orders.filter((o) => o.status === 'CONFIRMED').length,
      cancelled: orders.filter((o) => o.status === 'CANCELLED').length,
    }),
    [orders],
  )

  return (
    <section>
      <div className="page-head">
        <Breadcrumbs items={[{ label: 'Administration', to: '/admin_panel' }, { label: 'Commandes' }]} />
        <h1 className="mt-4">Commandes</h1>
        <p className="muted mt-2">{loading ? 'Chargement…' : `${filtered.length} commande(s)`}</p>
      </div>

      <div className="admin-toolbar">
        <div className="search-input admin-search">
          <span className="icon">
            <Icon name="search" size={16} />
          </span>
          <input
            className="input"
            placeholder="Rechercher (client, e-mail, n°)…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Rechercher une commande"
          />
        </div>
        <div className="chips" role="tablist" aria-label="Filtrer par statut">
          <button
            className={`chip${status === 'ALL' ? ' active' : ''}`}
            onClick={() => setStatus('ALL')}
            role="tab"
            aria-selected={status === 'ALL'}
          >
            Toutes ({counts.all})
          </button>
          <button
            className={`chip${status === 'CONFIRMED' ? ' active' : ''}`}
            onClick={() => setStatus('CONFIRMED')}
            role="tab"
            aria-selected={status === 'CONFIRMED'}
          >
            Confirmées ({counts.confirmed})
          </button>
          <button
            className={`chip${status === 'CANCELLED' ? ' active' : ''}`}
            onClick={() => setStatus('CANCELLED')}
            role="tab"
            aria-selected={status === 'CANCELLED'}
          >
            Annulées ({counts.cancelled})
          </button>
        </div>
      </div>

      {loading && (
        <div className="card card-pad stack gap-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} height={42} />
          ))}
        </div>
      )}

      {!loading && error && (
        <EmptyState tone="error" message={error} actionLabel="Réessayer" onAction={reload} />
      )}

      {!loading && !error && filtered.length === 0 && (
        <EmptyState
          icon="receipt"
          title="Aucune commande"
          message="Aucune commande ne correspond à ces critères."
        />
      )}

      {!loading && !error && filtered.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>N°</th>
                <th>Client</th>
                <th>Date</th>
                <th>Articles</th>
                <th>Total</th>
                <th>Paiement</th>
                <th>Statut</th>
                <th style={{ textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((o) => (
                <tr key={o.id}>
                  <td className="semibold">#{o.id}</td>
                  <td style={{ whiteSpace: 'normal' }}>
                    <div className="semibold">{o.customerName}</div>
                    <div className="muted xs">{o.customerEmail}</div>
                  </td>
                  <td className="muted">{formatDateTime(o.createdAt)}</td>
                  <td>{o.items.length}</td>
                  <td className="semibold">{formatPrice(o.total)}</td>
                  <td>
                    <div className="stack gap-1">
                      <span className="muted xs">À la livraison</span>
                      <Badge tone={o.paymentStatus === 'PAID' ? 'success' : 'neutral'}>
                        {o.paymentStatus === 'PAID' ? 'Payée' : 'En attente'}
                      </Badge>
                    </div>
                  </td>
                  <td>
                    <Badge tone={orderTone(o.status)}>{orderLabel(o.status)}</Badge>
                  </td>
                  <td>
                    <div className="actions">
                      <Link to={`/admin_panel/orders/${o.id}`} className="icon-btn" aria-label={`Voir la commande #${o.id}`} title="Détails">
                        <Icon name="chevronRight" size={16} />
                      </Link>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <p className="muted small mt-6">
        <Icon name="clock" size={14} /> Le statut des commandes n'est pas modifiable depuis cette console :
        l'API de commandes expose uniquement la consultation.
      </p>
    </section>
  )
}
