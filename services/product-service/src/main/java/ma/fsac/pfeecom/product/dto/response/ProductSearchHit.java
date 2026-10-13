package ma.fsac.pfeecom.product.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Resultat de recherche : meme contrat que {@link ProductResponse} (compatible
 * avec la carte produit du front-end) enrichi des extraits surlignes.
 *
 * <p>{@code highlights} peut etre {@code null} (repli PostgreSQL ou aucun terme
 * surligne). Les termes surlignes sont encadres par {@code <em>...</em>},
 * interpretes cote front-end par un decoupage sur ces seules balises (sans
 * {@code dangerouslySetInnerHTML}) : aucune injection HTML possible, le texte
 * est rendu comme du contenu React echappe.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record ProductSearchHit(
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
        LocalDateTime updatedAt,
        Map<String, String> highlights
) {
}
