package ma.fsac.pfeecom.cart.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO de réponse représentant le panier d'un client.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record CartResponse(
        Long customerId,
        List<CartItemResponse> items,
        BigDecimal total
) {}