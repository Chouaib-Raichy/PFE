package ma.fsac.pfeecom.order.client;

import java.math.BigDecimal;

/**
 * Sous-ensemble des informations produit nécessaires à une commande.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record ProductInfo(
        Long id,
        String name,
        BigDecimal price
) {}