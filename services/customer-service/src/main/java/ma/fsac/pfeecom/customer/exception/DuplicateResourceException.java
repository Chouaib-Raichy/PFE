package ma.fsac.pfeecom.customer.exception;

/**
 * Exception métier levée lors d'un conflit de ressource (ex. email déjà utilisé).
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}