package ma.fsac.pfeecom.cart.client;

/**
 * Sous-ensemble des informations client nécessaires au panier.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record CustomerInfo(
        Long id,
        String firstName,
        String lastName,
        String email
) {}