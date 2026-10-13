package ma.fsac.pfeecom.order.client;

import lombok.RequiredArgsConstructor;
import ma.fsac.pfeecom.order.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Client du Product-Service : résolution de l'instance via Eureka/LoadBalancer
 * puis appel REST pour récupérer un produit.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Component
@RequiredArgsConstructor
public class ProductServiceClient {

    private final RestClient.Builder restClientBuilder;

    public ProductInfo getProduct(Long productId) {
        try {
            ProductInfo info = restClientBuilder.build()
                    .get()
                    .uri("http://product-service/api/products/{id}", productId)
                    .retrieve()
                    .body(ProductInfo.class);
            if (info == null || info.id() == null || info.name() == null || info.price() == null) {
                throw ResourceNotFoundException.of("Produit", productId);
            }
            return info;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                throw ResourceNotFoundException.of("Produit", productId);
            }
            throw ex;
        }
    }
}