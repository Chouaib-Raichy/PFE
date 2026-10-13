package ma.fsac.pfeecom.product.exception;

/**
 * Exception levee lorsqu'un fichier image soumis est invalide (format non
 * supporte, type declare incoherent avec le contenu, fichier vide). Traduite en
 * HTTP 400.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public class InvalidImageException extends RuntimeException {

    public InvalidImageException(String message) {
        super(message);
    }
}
