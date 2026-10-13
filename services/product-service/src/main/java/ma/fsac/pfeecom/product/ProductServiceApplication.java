package ma.fsac.pfeecom.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point d'entrÃ©e du microservice Product-Service.
 *
 * <p>Responsable de la gestion du catalogue produits d'une plateforme e-commerce :
 * crÃ©ation, consultation, recherche, mise Ã  jour et suppression de produits,
 * ainsi que la gestion des catÃ©gories.</p>
 *
 * <p>Ce service s'enregistre auprÃ¨s d'Eureka (Service Discovery) pour permettre
 * son accÃ¨s dynamique via l'API Gateway.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableCaching
@EnableScheduling
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
