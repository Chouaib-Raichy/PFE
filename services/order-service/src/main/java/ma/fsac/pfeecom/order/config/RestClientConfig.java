package ma.fsac.pfeecom.order.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Client REST équilibré par Spring Cloud LoadBalancer pour la découverte
 * de services (lb via Eureka) lors des appels inter-services.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Configuration
public class RestClientConfig {

    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}