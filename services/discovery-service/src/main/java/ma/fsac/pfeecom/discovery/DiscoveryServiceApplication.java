package ma.fsac.pfeecom.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Point d'entrée du Service Discovery (Netflix Eureka).
 *
 * <p>Ce service joue le rôle de registre central pour l'architecture microservices.
 * Il permet aux différents services (Product, Customer, Cart, Order) de s'enregistrer
 * et de se découvrir dynamiquement sans dépendre d'adresses IP statiques.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServiceApplication.class, args);
    }
}