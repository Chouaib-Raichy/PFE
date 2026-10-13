package ma.fsac.pfeecom.order.domain.entity;

/**
 * Statut de paiement d'une commande.
 *
 * <p>Une commande en paiement à la livraison est créée {@code PENDING} : elle
 * n'est jamais marquée payée à la création, faute de traitement de paiement
 * en ligne.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public enum PaymentStatus {
    PENDING,
    PAID
}
