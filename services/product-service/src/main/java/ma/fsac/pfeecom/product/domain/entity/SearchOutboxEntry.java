package ma.fsac.pfeecom.product.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entree de la file d'indexation Elasticsearch (table product_search_outbox).
 *
 * <p>Ecrite dans la meme transaction que la modification du produit : rend la
 * synchronisation de l'index fiable meme si Elasticsearch est indisponible au
 * moment de l'ecriture. Aucune cle etrangere (un produit supprime doit aussi
 * pouvoir etre desindexe).</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Entity
@Table(name = "product_search_outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SearchOutboxEntry {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
