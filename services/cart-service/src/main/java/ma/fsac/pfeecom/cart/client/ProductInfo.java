package ma.fsac.pfeecom.cart.client;

import java.math.BigDecimal;

/**
 * Sous-ensemble des informations produit nécessaires au panier.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record ProductInfo(
        Long id,
        String name,
        BigDecimal price
) {}