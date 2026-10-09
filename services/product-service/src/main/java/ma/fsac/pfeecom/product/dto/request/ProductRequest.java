package ma.fsac.pfeecom.product.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO de requête pour la création/modification d'un produit.
 *
 * <p>Utilisé dans les endpoints REST pour valider les données entrantes.
 * Sépare clairement la couche API de l'entité JPA (bonne pratique DTO).</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record ProductRequest(

        @NotBlank(message = "Le nom du produit est obligatoire")
        @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères")
        String name,

        @Size(max = 1000, message = "La description ne doit pas dépasser 1000 caractères")
        String description,

        @NotNull(message = "Le prix est obligatoire")
        @DecimalMin(value = "0.01", message = "Le prix doit être supérieur à 0")
        BigDecimal price,

        @NotNull(message = "La quantité en stock est obligatoire")
        @Min(value = 0, message = "Le stock ne peut être négatif")
        Integer stockQuantity,

        String imageUrl,

        Boolean active,

        Long categoryId
) {}