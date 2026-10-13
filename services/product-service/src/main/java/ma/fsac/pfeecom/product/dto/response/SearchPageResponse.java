package ma.fsac.pfeecom.product.dto.response;

import java.util.List;

/**
 * Page de resultats de recherche. Meme forme que {@code Page<T>} de Spring Data
 * (le front-end reutilise le meme modele) plus un drapeau {@code degraded}
 * indiquant que le repli PostgreSQL a ete utilise faute d'Elasticsearch.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record SearchPageResponse(
        List<ProductSearchHit> content,
        long totalElements,
        int totalPages,
        int number,
        int size,
        boolean degraded
) {
}
