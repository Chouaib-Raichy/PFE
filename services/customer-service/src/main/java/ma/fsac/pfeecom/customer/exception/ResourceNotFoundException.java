package ma.fsac.pfeecom.customer.exception;

/**
 * Exception métier levée lorsqu'un client n'existe pas.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String resource, Long id) {
        return new ResourceNotFoundException(String.format("%s introuvable avec l'identifiant : %d", resource, id));
    }
}