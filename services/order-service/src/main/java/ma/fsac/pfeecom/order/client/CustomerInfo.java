package ma.fsac.pfeecom.order.client;

/**
 * Sous-ensemble des informations client nécessaires à une commande.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record CustomerInfo(
        Long id,
        String firstName,
        String lastName,
        String email
) {}