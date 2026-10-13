package ma.fsac.pfeecom.product.storage;

/**
 * Contenu binaire d'une image et son type MIME.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record StoredImage(byte[] data, String contentType) {}
