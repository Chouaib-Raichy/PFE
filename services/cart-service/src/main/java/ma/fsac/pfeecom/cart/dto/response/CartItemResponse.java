package ma.fsac.pfeecom.cart.dto.response;

import java.math.BigDecimal;

/**
 * DTO de réponse représentant une ligne de panier.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record CartItemResponse(
        Long itemId,
        Long productId,
        String productName,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal subtotal
) {}