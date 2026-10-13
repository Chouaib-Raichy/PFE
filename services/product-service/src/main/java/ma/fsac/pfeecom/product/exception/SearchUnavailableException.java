package ma.fsac.pfeecom.product.exception;

/**
 * Levee lorsqu'une operation de recherche/indexation necessite Elasticsearch,
 * qui est indisponible. Traduite en 503 par le gestionnaire global ; les
 * endpoints de consultation storefront, eux, se replient sur PostgreSQL.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public class SearchUnavailableException extends RuntimeException {

    public SearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public SearchUnavailableException(String message) {
        super(message);
    }
}
