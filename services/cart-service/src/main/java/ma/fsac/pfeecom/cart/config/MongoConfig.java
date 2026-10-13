package ma.fsac.pfeecom.cart.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Configuration MongoDB du service panier.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}