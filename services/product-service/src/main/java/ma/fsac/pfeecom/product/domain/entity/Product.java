package ma.fsac.pfeecom.product.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entité JPA représentant un produit (PostgreSQL).
 *
 * <p>La colonne {@code category_id} porte une véritable clé étrangère vers
 * {@code categories(id)} (créée par Flyway). {@code category_name} reste une
 * copie dénormalisée pour l'affichage, cohérente avec le contrat REST.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Entity
@Table(name = "products")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    @SequenceGenerator(name = "product_seq", sequenceName = "product_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    /**
     * Reference INTERNE de l'objet image dans le stockage S3/MinIO (jamais
     * exposee au client). Permet de remplacer/supprimer proprement l'objet.
     */
    @Column(name = "image_key", length = 300)
    private String imageKey;

    /** Référence vers la catégorie (clé étrangère vers categories.id). */
    @Column(name = "category_id")
    private Long categoryId;

    /** Copie du nom de la catégorie pour l'affichage, cohérente avec le front-end. */
    @Column(name = "category_name", length = 100)
    private String categoryName;

    @Column
    private Boolean active;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
