package ma.fsac.pfeecom.product.dto.response;

/**
 * Resultat d'une reindexation complete (reconciliation depuis PostgreSQL).
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record ReindexResponse(
        long indexed
) {
}
