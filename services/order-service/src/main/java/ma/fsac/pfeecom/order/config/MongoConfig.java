package ma.fsac.pfeecom.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Configuration MongoDB du service commande.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}