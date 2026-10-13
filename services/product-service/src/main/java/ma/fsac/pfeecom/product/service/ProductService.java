package ma.fsac.pfeecom.product.service;

import ma.fsac.pfeecom.product.dto.request.ProductRequest;
import ma.fsac.pfeecom.product.dto.response.ProductResponse;
import ma.fsac.pfeecom.product.storage.StoredImage;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service métier de gestion des produits.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public interface ProductService {

    Page<ProductResponse> findAll(int page, int size);

    ProductResponse findById(Long id);

    ProductResponse create(ProductRequest request);

    ProductResponse update(Long id, ProductRequest request);

    void delete(Long id);

    /** Stocke une nouvelle image pour le produit (remplace l'ancienne au mieux). */
    ProductResponse uploadImage(Long id, MultipartFile file);

    /** Supprime l'image du produit (objet de stockage + references). */
    ProductResponse removeImage(Long id);

    /** Lit le contenu binaire de l'image courante du produit. */
    StoredImage loadImage(Long id);
}