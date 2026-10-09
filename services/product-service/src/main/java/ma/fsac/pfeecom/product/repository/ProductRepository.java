package ma.fsac.pfeecom.product.repository;

import ma.fsac.pfeecom.product.domain.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Repository JPA pour l'entité Product.
 *
 * <p>Fournit les opérations CRUD ainsi que des requêtes métier
 * (recherche, filtrage par catégorie, fourchette de prix).</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Recherche des produits actifs correspondant à un mot-clé (nom ou description).
     */
    @Query("""
            SELECT p FROM Product p
            WHERE p.active = true
              AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Product> searchActive(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Récupère tous les produits actifs d'une catégorie.
     */
    List<Product> findByActiveTrueAndCategoryId(Long categoryId);

    /**
     * Filtre les produits actifs par fourchette de prix.
     */
    List<Product> findByActiveTrueAndPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);

    /**
     * Vérifie l'existence d'un produit actif par son identifiant.
     */
    boolean existsByIdAndActiveTrue(Long id);
}