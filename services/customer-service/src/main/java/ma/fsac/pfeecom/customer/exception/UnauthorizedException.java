package ma.fsac.pfeecom.customer.exception;

/**
 * Appelant non authentifié sur une opération exigeant une identité vérifiée.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
