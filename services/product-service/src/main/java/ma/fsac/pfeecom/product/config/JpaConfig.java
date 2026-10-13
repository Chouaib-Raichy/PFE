package ma.fsac.pfeecom.product.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Configuration JPA du service produit.
 *
 * <p>Active l'audit automatique des dates (createdAt / updatedAt) portées par
 * {@code AuditingEntityListener}.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
