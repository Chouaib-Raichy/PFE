package ma.fsac.pfeecom.order.domain.entity;

/**
 * Modes de paiement supportés par la boutique.
 *
 * <p>Seul le paiement à la livraison (« Cash on Delivery ») est disponible :
 * aucune intégration de prestataire de paiement en ligne n'est proposée.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public enum PaymentMethod {
    CASH_ON_DELIVERY
}
