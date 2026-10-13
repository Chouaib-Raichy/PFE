package ma.fsac.pfeecom.cart.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Ligne de panier embarquée dans le document {@link Cart}.
 *
 * <p>À la différence de l'ancienne entité relationnelle, cette classe n'est pas
 * un document MongoDB autonome : elle vit à l'intérieur du panier.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    private Long id;

    private Long productId;

    private String productName;

    private BigDecimal unitPrice;

    private Integer quantity;

    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}