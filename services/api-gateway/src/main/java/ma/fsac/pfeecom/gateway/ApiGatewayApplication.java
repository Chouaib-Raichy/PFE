package ma.fsac.pfeecom.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Point d'entrée de l'API Gateway (Spring Cloud Gateway).
 *
 * <p>L'API Gateway constitue le point d'entrée unique vers l'ensemble des microservices.
 * Il assure le routage dynamique, la gestion CORS, et centralise les préoccupations transverses.
 * Grâce à la découverte de services (Eureka), les routes utilisent les noms des services
 * plutôt que des adresses IP/DNS en dur.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}