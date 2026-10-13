import { Link, useNavigate } from 'react-router-dom'
import { useCart } from '../../app/CartProvider'
import { useAuth } from '../../app/AuthProvider'
import { useToast } from '../../ui/Toast'
import { formatPrice } from '../../lib/format'
import { Breadcrumbs } from '../../ui/Breadcrumbs'
import { Badge } from '../../ui/Badge'
import { Button } from '../../ui/Button'
import { Icon } from '../../ui/Icon'
import { EmptyState } from '../../ui/EmptyState'
import { Spinner } from '../../ui/Spinner'

export function CartPage() {
  const { cart, count, loading, placing, serverBacked, addItem, removeItem, clear, placeOrder } =
    useCart()
  const { authenticated, login } = useAuth()
  const { notify } = useToast()
  const navigate = useNavigate()

  const handleOrder = async () => {
    const order = await placeOrder()
    if (order) {
      notify(
        `Commande #${order.id} confirmée — paiement à la livraison — ${formatPrice(order.total)}`,
      )
      navigate(`/orders/${order.id}`)
    }
  }

  return (
    <section>
      <div className="page-head">
        <Breadcrumbs items={[{ label: 'Accueil', to: '/' }, { label: 'Panier' }]} />
        <h1 className="mt-4">Mon panier</h1>
        <p className="muted mt-2">{loading ? 'Chargement…' : `${count} article(s)`}</p>
      </div>

      {loading ? (
        <div className="center" style={{ padding: 'var(--sp-16) 0' }}>
          <Spinner large label="Chargement du panier" />
        </div>
      ) : cart.items.length === 0 ? (
        <EmptyState
          icon="cart"
          title="Votre panier est vide"
          message="Parcourez le catalogue et ajoutez vos premiers articles."
          actionLabel="Explorer le catalogue"
          onAction={() => navigate('/catalog')}
        />
      ) : (
        <div className="cart-layout">
          <div className="cart-lines">
            {cart.items.map((item) => (
              <div className="cart-line" key={item.itemId}>
                <span className="cart-line-thumb">
                  <Icon name="image" size={22} />
                </span>
                <div className="stack gap-1">
                  <Link to={`/products/${item.productId}`} className="semibold">
                    {item.productName}
                  </Link>
                  <span className="muted small">{formatPrice(item.unitPrice)} / unité</span>
                </div>
                <div className="row gap-2">
                  <div className="qty">
                    <button
                      onClick={() =>
                        void addItem({
                          id: item.productId,
                          name: item.productName,
                          price: item.unitPrice,
                          imageUrl: null,
                        })
                      }
                      aria-label={`Augmenter la quantité de ${item.productName}`}
                    >
                      <Icon name="plus" size={15} />
                    </button>
                    <span>{item.quantity}</span>
                    <button
                      onClick={() => void removeItem(item.itemId)}
                      aria-label={`Réduire la quantité de ${item.productName}`}
                    >
                      <Icon name="minus" size={15} />
                    </button>
                  </div>
                  <Button
                    variant="danger"
                    size="sm"
                    onClick={() => void removeItem(item.itemId)}
                    aria-label={`Retirer ${item.productName}`}
                  >
                    <Icon name="trash" size={15} />
                  </Button>
                </div>
                <span className="price price-small">{formatPrice(item.subtotal)}</span>
              </div>
            ))}

            <div className="between mt-6">
              <Button variant="danger" onClick={() => void clear()}>
                <Icon name="trash" size={16} />
                Vider le panier
              </Button>
            </div>
          </div>

          <aside className="card card-pad cart-summary" aria-label="Résumé de la commande">
            <h3 className="mb-4">Récapitulatif</h3>
            <div className="summary-row">
              <span>Articles</span>
              <span>{count}</span>
            </div>
            <div className="summary-row">
              <span>Livraison</span>
              <Badge tone="gold">Gratuite</Badge>
            </div>
            <div className="summary-row">
              <span>Paiement</span>
              <span className="semibold small">Paiement à la livraison — الدفع عند الاستلام</span>
            </div>
            <div className="summary-total">
              <span>Total</span>
              <span>{formatPrice(cart.total)}</span>
            </div>

            {serverBacked ? (
              <>
                <Button
                  variant="primary"
                  size="lg"
                  block
                  className="mt-6"
                  loading={placing}
                  onClick={() => void handleOrder()}
                >
                  {placing ? 'Commande en cours…' : `Passer commande — ${formatPrice(cart.total)}`}
                </Button>
                <p className="muted xs center mt-4">
                  Le panier est lié à votre compte : seuls vous et les administrateurs y accèdent.
                </p>
              </>
            ) : authenticated ? (
              <p className="muted small mt-6">
                Aucun profil client n'est associé à votre compte. Contactez un administrateur pour
                lier votre profil avant de commander.
              </p>
            ) : (
              <>
                <Button
                  variant="primary"
                  size="lg"
                  block
                  className="mt-6"
                  onClick={() => login('/cart')}
                >
                  Se connecter pour commander
                </Button>
                <p className="muted xs center mt-4">
                  Votre panier sera conservé et fusionné avec votre compte après connexion.
                </p>
              </>
            )}
          </aside>
        </div>
      )}
    </section>
  )
}
