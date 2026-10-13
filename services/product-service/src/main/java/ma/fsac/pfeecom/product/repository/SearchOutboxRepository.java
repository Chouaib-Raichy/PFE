package ma.fsac.pfeecom.product.repository;

import ma.fsac.pfeecom.product.domain.entity.SearchOutboxEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acces a la file d'indexation Elasticsearch.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Repository
public interface SearchOutboxRepository extends JpaRepository<SearchOutboxEntry, Long> {

    /**
     * Ajoute (ou ignore) un produit a resynchroniser. Upsert idempotent : un
     * produit deja en file (plusieurs ecritures avant traitement) n'est pas
     * duplique.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO product_search_outbox (product_id)
            VALUES (:productId)
            ON CONFLICT (product_id) DO NOTHING
            """, nativeQuery = true)
    void enqueue(@Param("productId") Long productId);
}
