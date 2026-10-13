package ma.fsac.pfeecom.cart.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

/**
 * Contrat d'accès aux produits gérés par le Product-Service.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public interface ProductClient {

    @GetExchange("/api/products/{id}")
    ProductInfo getProduct(@PathVariable Long id);
}