package ma.fsac.pfeecom.product.dto.response;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de rÃ©ponse exposant les informations d'un produit via l'API REST.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        String imageUrl,
        Boolean active,
        Long categoryId,
        String categoryName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements Serializable {}
