package ma.fsac.pfeecom.cart.client;

import lombok.RequiredArgsConstructor;
import ma.fsac.pfeecom.cart.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Client du Customer-Service : résolution de l'instance via Eureka/LoadBalancer
 * puis appel REST pour valider l'existence d'un client.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Component
@RequiredArgsConstructor
public class CustomerServiceClient {

    private final RestClient.Builder restClientBuilder;

    public CustomerInfo getCustomer(Long customerId) {
        try {
            CustomerInfo info = restClientBuilder.build()
                    .get()
                    .uri("http://customer-service/api/customers/{id}", customerId)
                    .retrieve()
                    .body(CustomerInfo.class);
            if (info == null || info.id() == null || info.firstName() == null
                    || info.lastName() == null || info.email() == null) {
                throw ResourceNotFoundException.of("Client", customerId);
            }
            return info;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                throw ResourceNotFoundException.of("Client", customerId);
            }
            throw ex;
        }
    }
}