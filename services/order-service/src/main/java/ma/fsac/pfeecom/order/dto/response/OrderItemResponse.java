package ma.fsac.pfeecom.order.dto.response;

import java.math.BigDecimal;

/**
 * DTO de réponse représentant une ligne de commande.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record OrderItemResponse(
        Long productId,
        String productName,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal subtotal
) {}