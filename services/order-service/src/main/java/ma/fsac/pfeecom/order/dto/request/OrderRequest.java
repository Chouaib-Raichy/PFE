package ma.fsac.pfeecom.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import ma.fsac.pfeecom.order.domain.entity.PaymentMethod;

import java.util.List;

/**
 * DTO de requête pour la création d'une commande.
 *
 * <p>Le mode de paiement est explicite et validé côté serveur : seul
 * {@link PaymentMethod#CASH_ON_DELIVERY} est accepté (toute autre valeur est
 * rejetée par la désérialisation).</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record OrderRequest(

        @NotNull(message = "Le client est obligatoire")
        Long customerId,

        @NotNull(message = "Le mode de paiement est obligatoire")
        PaymentMethod paymentMethod,

        @NotEmpty(message = "La commande doit contenir au moins une ligne")
        @Valid
        List<OrderItemRequest> items
) {

    public record OrderItemRequest(
            @NotNull(message = "Le produit est obligatoire")
            Long productId,

            @NotNull(message = "La quantité est obligatoire")
            @Min(value = 1, message = "La quantité doit être au moins 1")
            @Max(value = 999, message = "La quantité ne doit pas dépasser 999")
            Integer quantity
    ) {}
}