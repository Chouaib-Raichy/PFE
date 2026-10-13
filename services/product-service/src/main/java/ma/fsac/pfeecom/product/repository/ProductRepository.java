package ma.fsac.pfeecom.product.repository;

import ma.fsac.pfeecom.product.domain.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository JPA pour l'entité Product.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Indique si au moins un produit référence la catégorie donnée. */
    boolean existsByCategoryId(Long categoryId);

    /**
     * Recherche de repli (PostgreSQL) utilisee quand Elasticsearch est
     * indisponible : sous-chaine insensible a la casse sur nom / description /
     * categorie, produits actifs uniquement, filtre categorie optionnel.
     */
    @Query("""
            SELECT p FROM Product p
            WHERE (:q = '' OR lower(p.name) LIKE lower(concat('%', :q, '%'))
                   OR lower(coalesce(p.description, '')) LIKE lower(concat('%', :q, '%'))
                   OR lower(coalesce(p.categoryName, '')) LIKE lower(concat('%', :q, '%')))
              AND p.active = true
              AND (:categoryId IS NULL OR p.categoryId = :categoryId)
            """)
    Page<Product> searchFallback(@Param("q") String q,
                                 @Param("categoryId") Long categoryId,
                                 Pageable pageable);

    /** Suggestions de repli : produits actifs dont le nom contient le terme. */
    @Query("""
            SELECT p FROM Product p
            WHERE p.active = true
              AND lower(p.name) LIKE lower(concat('%', :q, '%'))
            ORDER BY p.name ASC
            """)
    List<Product> suggestFallback(@Param("q") String q, Pageable pageable);
}
