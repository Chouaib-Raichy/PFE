package ma.fsac.pfeecom.product.search;

import java.math.BigDecimal;

/**
 * Document de recherche indexe dans Elasticsearch (source de verite : PostgreSQL).
 *
 * <p>L'identifiant du document est l'id du produit (indexation idempotente).
 * Les dates sont stockees en millisecondes epoch (mapping {@code epoch_millis})
 * pour un aller-retour deterministe, sans dependance a un module de date cote
 * client Elasticsearch.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record ProductSearchDocument(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        String imageUrl,
        Boolean active,
        Long categoryId,
        String categoryName,
        Long createdAt,
        Long updatedAt
) {
}
