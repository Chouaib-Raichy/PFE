package ma.fsac.pfeecom.cart.exception;

/**
 * Exception métier levée quand une ressource (panier, produit...) n'existe pas.
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

    public static ResourceNotFoundException customer(Long customerId) {
        return new ResourceNotFoundException(String.format("Aucun panier trouvé pour le client : %d", customerId));
    }
}