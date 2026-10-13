package ma.fsac.pfeecom.cart.service.impl;

import lombok.RequiredArgsConstructor;
import ma.fsac.pfeecom.cart.client.CustomerServiceClient;
import ma.fsac.pfeecom.cart.client.ProductServiceClient;
import ma.fsac.pfeecom.cart.config.SequenceGenerator;
import ma.fsac.pfeecom.cart.domain.entity.Cart;
import ma.fsac.pfeecom.cart.domain.entity.CartItem;
import ma.fsac.pfeecom.cart.dto.request.AddItemRequest;
import ma.fsac.pfeecom.cart.dto.request.MergeCartRequest;
import ma.fsac.pfeecom.cart.dto.response.CartItemResponse;
import ma.fsac.pfeecom.cart.dto.response.CartResponse;
import ma.fsac.pfeecom.cart.exception.ResourceNotFoundException;
import ma.fsac.pfeecom.cart.repository.CartRepository;
import ma.fsac.pfeecom.cart.service.CartService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Implémentation du service métier du panier.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ProductServiceClient productServiceClient;
    private final CustomerServiceClient customerServiceClient;
    private final SequenceGenerator sequenceGenerator;

    @Override
    public CartResponse getCart(Long customerId) {
        return toResponse(getCartOrThrow(customerId));
    }

    @Override
    public CartResponse addItem(Long customerId, AddItemRequest request) {
        customerServiceClient.getCustomer(customerId);
        Cart cart = getOrCreateCart(customerId);

        cart.getItems().stream()
                .filter(item -> item.getProductId().equals(request.productId()))
                .findFirst()
                .ifPresentOrElse(
                        item -> item.setQuantity(item.getQuantity() + request.quantity()),
                        () -> cart.addItem(toItem(cart, request))
                );
        return toResponse(cartRepository.save(cart));
    }

    @Override
    public CartResponse mergeCart(Long customerId, MergeCartRequest request) {
        customerServiceClient.getCustomer(customerId);
        Cart cart = getOrCreateCart(customerId);

        for (AddItemRequest incoming : request.items()) {
            cart.getItems().stream()
                    .filter(item -> item.getProductId().equals(incoming.productId()))
                    .findFirst()
                    .ifPresentOrElse(
                            item -> item.setQuantity(Math.max(item.getQuantity(), incoming.quantity())),
                            () -> cart.addItem(toItem(cart, incoming)));
        }
        return toResponse(cartRepository.save(cart));
    }

    @Override
    public CartResponse removeItem(Long customerId, Long itemId) {
        Cart cart = getCartOrThrow(customerId);
        boolean existed = cart.getItems().stream().anyMatch(item -> item.getId().equals(itemId));
        if (!existed) {
            throw ResourceNotFoundException.of("Ligne de panier", itemId);
        }
        cart.removeItem(itemId);
        return toResponse(cartRepository.save(cart));
    }

    @Override
    public void clearCart(Long customerId) {
        Cart cart = getCartOrThrow(customerId);
        cartRepository.delete(cart);
    }

    private CartItem toItem(Cart cart, AddItemRequest request) {
        var product = productServiceClient.getProduct(request.productId());
        return CartItem.builder()
                .id(cart.nextItemId())
                .productId(product.id())
                .productName(product.name())
                .unitPrice(product.price())
                .quantity(request.quantity())
                .build();
    }

    private Cart getCartOrThrow(Long customerId) {
        return cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> ResourceNotFoundException.customer(customerId));
    }

    private Cart getOrCreateCart(Long customerId) {
        return cartRepository.findByCustomerId(customerId).orElseGet(() -> {
            try {
                return cartRepository.save(Cart.builder()
                        .id(sequenceGenerator.nextSeq("carts"))
                        .customerId(customerId)
                        .build());
            } catch (DuplicateKeyException ex) {
                return cartRepository.findByCustomerId(customerId).orElseThrow(() -> ex);
            }
        });
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream().map(this::toItemResponse).toList();
        BigDecimal total = items.stream()
                .map(CartItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(cart.getCustomerId(), items, total);
    }

    private CartItemResponse toItemResponse(CartItem item) {
        return new CartItemResponse(
                item.getId(), item.getProductId(), item.getProductName(),
                item.getUnitPrice(), item.getQuantity(), item.getSubtotal());
    }
}