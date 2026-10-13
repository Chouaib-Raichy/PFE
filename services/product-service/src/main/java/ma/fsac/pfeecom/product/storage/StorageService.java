package ma.fsac.pfeecom.product.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Abstraction du stockage objet des images produit (implementation S3/MinIO).
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public interface StorageService {

    /**
     * Valide et stocke une image pour un produit.
     *
     * @return la cle de l'objet cree dans le bucket (reference interne)
     */
    String storeImage(Long productId, MultipartFile file);

    /**
     * Lit le contenu d'une image a partir de sa cle d'objet.
     *
     * @throws ma.fsac.pfeecom.product.exception.ResourceNotFoundException si l'objet n'existe pas
     * @throws ma.fsac.pfeecom.product.exception.StorageUnavailableException si le stockage est indisponible
     */
    StoredImage loadImage(String key);

    /**
     * Supprime un objet, au mieux : toute erreur est journalisee sans etre
     * propagee (une image orpheline ne doit pas faire echouer une operation metier).
     */
    void deleteImage(String key);
}
