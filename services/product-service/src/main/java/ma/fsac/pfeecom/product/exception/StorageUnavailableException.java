package ma.fsac.pfeecom.product.exception;

/**
 * Exception levee lorsque le stockage objet (S3/MinIO) est indisponible ou
 * desactive. Traduite en HTTP 503 : l'operation peut etre retentee.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public class StorageUnavailableException extends RuntimeException {

    public StorageUnavailableException(String message) {
        super(message);
    }

    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
