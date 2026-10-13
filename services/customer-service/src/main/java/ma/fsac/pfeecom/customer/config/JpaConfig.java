package ma.fsac.pfeecom.customer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Configuration JPA du service client.
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
