package ma.fsac.pfeecom.cart.service;

import ma.fsac.pfeecom.cart.dto.request.AddItemRequest;
import ma.fsac.pfeecom.cart.dto.request.MergeCartRequest;
import ma.fsac.pfeecom.cart.dto.response.CartResponse;

/**
 * Contrat du service métier du panier.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public interface CartService {

    CartResponse getCart(Long customerId);

    CartResponse addItem(Long customerId, AddItemRequest request);

    CartResponse mergeCart(Long customerId, MergeCartRequest request);

    CartResponse removeItem(Long customerId, Long itemId);

    void clearCart(Long customerId);
}