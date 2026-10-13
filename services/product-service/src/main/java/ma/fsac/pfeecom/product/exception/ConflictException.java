package ma.fsac.pfeecom.product.exception;

/**
 * Exception métier levée lorsqu'une opération entre en conflit avec l'état actuel
 * des données (par exemple la suppression d'une catégorie encore référencée).
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
