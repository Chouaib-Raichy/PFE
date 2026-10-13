package ma.fsac.pfeecom.product.dto.response;

/**
 * Suggestion d'autocompletion : informations minimales affichees dans la liste
 * deroulante du champ de recherche.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record SuggestItem(
        Long id,
        String name,
        String categoryName,
        String imageUrl
) {
}
