package ma.fsac.pfeecom.customer.domain.entity;

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

import java.time.LocalDateTime;

/**
 * Entité JPA représentant un client du e-commerce (PostgreSQL).
 *
 * <p>L'unicité de l'email est garantie par la clé technique {@code email_key}
 * (email normalisé en minuscules) portant une contrainte UNIQUE en base. L'email
 * d'origine est conservé tel que saisi par l'utilisateur. Les identifiants
 * restent des {@code Long} générés par la séquence {@code customer_seq}.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Entity
@Table(name = "customers")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_seq")
    @SequenceGenerator(name = "customer_seq", sequenceName = "customer_seq", allocationSize = 1)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 150)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 150)
    private String lastName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "email_key", nullable = false, unique = true, length = 150)
    private String emailKey;

    /**
     * Identifiant immuable de l'utilisateur Keycloak (claim {@code sub}).
     * Clé d'identité du provisionnement automatique : unique, nullable pour les
     * profils de démonstration rattachés via l'attribut {@code customer_id}.
     */
    @Column(name = "keycloak_sub", unique = true, length = 64)
    private String keycloakSub;

    @Column(length = 30)
    private String phone;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
