package ma.fsac.pfeecom.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Point d'entrée du microservice Product-Service.
 *
 * <p>Responsable de la gestion du catalogue produits d'une plateforme e-commerce :
 * création, consultation, recherche, mise à jour et suppression de produits,
 * ainsi que la gestion des catégories.</p>
 *
 * <p>Ce service s'enregistre auprès d'Eureka (Service Discovery) pour permettre
 * son accès dynamique via l'API Gateway.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}