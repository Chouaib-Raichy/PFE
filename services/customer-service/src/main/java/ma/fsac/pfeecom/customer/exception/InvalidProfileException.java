package ma.fsac.pfeecom.customer.exception;

/**
 * Données d'inscription insuffisantes pour créer un profil client.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public class InvalidProfileException extends RuntimeException {

    public InvalidProfileException(String message) {
        super(message);
    }
}
