package ma.fsac.pfeecom.product.dto.response;

import java.util.List;

/**
 * Reponse de suggestions. Vide (jamais en erreur) si Elasticsearch est
 * indisponible : la degradation est signalee par {@code degraded} sans casser
 * l'interface de recherche.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record SuggestResponse(
        List<SuggestItem> suggestions,
        boolean degraded
) {
}
