package ma.fsac.pfeecom.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Point d'entrée du Serveur de Configuration centralisé (Spring Cloud Config).
 *
 * <p>Ce service distribue la configuration des autres microservices à partir du
 * dépôt {@code config-repo/} (profil {@code native}). Il est volontairement
 * autonome : il ne s'enregistre pas auprès d'Eureka et n'importe pas de
 * configuration distante, ce qui évite toute dépendance circulaire au démarrage.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
