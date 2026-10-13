package ma.fsac.pfeecom.cart.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * DTO de requête pour l'ajout d'un produit au panier.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record AddItemRequest(

        @NotNull(message = "Le produit est obligatoire")
        Long productId,

        @NotNull(message = "La quantité est obligatoire")
        @Min(value = 1, message = "La quantité doit être au moins 1")
        @Max(value = 999, message = "La quantité ne doit pas dépasser 999")
        Integer quantity
) {}