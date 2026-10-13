package ma.fsac.pfeecom.order.service.impl;

import lombok.RequiredArgsConstructor;
import ma.fsac.pfeecom.order.client.CustomerInfo;
import ma.fsac.pfeecom.order.client.CustomerServiceClient;
import ma.fsac.pfeecom.order.client.ProductInfo;
import ma.fsac.pfeecom.order.client.ProductServiceClient;
import ma.fsac.pfeecom.order.config.SequenceGenerator;
import ma.fsac.pfeecom.order.domain.entity.Order;
import ma.fsac.pfeecom.order.domain.entity.OrderItem;
import ma.fsac.pfeecom.order.domain.entity.OrderStatus;
import ma.fsac.pfeecom.order.domain.entity.PaymentStatus;
import ma.fsac.pfeecom.order.dto.request.OrderRequest;
import ma.fsac.pfeecom.order.dto.response.OrderItemResponse;
import ma.fsac.pfeecom.order.dto.response.OrderResponse;
import ma.fsac.pfeecom.order.exception.ResourceNotFoundException;
import ma.fsac.pfeecom.order.repository.OrderRepository;
import ma.fsac.pfeecom.order.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Implémentation du service métier des commandes.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String RESOURCE = "Commande";

    private final OrderRepository orderRepository;
    private final CustomerServiceClient customerServiceClient;
    private final ProductServiceClient productServiceClient;
    private final SequenceGenerator sequenceGenerator;

    @Override
    public Page<OrderResponse> findAll(Pageable pageable) {
        return orderRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    public Page<OrderResponse> findByCustomer(Long customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable).map(this::toResponse);
    }

    @Override
    public OrderResponse findById(Long id) {
        return toResponse(getOrder(id));
    }

    @Override
    public OrderResponse create(OrderRequest request) {
        CustomerInfo customer = customerServiceClient.getCustomer(request.customerId());
        Order order = Order.builder()
                .id(sequenceGenerator.nextSeq("orders"))
                .customerId(customer.id())
                .customerName(customer.firstName() + " " + customer.lastName())
                .customerEmail(customer.email())
                .status(OrderStatus.CONFIRMED)
                .paymentMethod(request.paymentMethod())
                .paymentStatus(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        request.items().forEach(item -> order.addItem(toItem(item)));
        return toResponse(orderRepository.save(order));
    }

    private OrderItem toItem(OrderRequest.OrderItemRequest request) {
        ProductInfo product = productServiceClient.getProduct(request.productId());
        return OrderItem.builder()
                .productId(product.id())
                .productName(product.name())
                .unitPrice(product.price())
                .quantity(request.quantity())
                .build();
    }

    private Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getProductId(), item.getProductName(), item.getUnitPrice(),
                        item.getQuantity(), item.getSubtotal()))
                .toList();
        BigDecimal total = items.stream()
                .map(OrderItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderResponse(
                order.getId(), order.getCustomerId(), order.getCustomerName(), order.getCustomerEmail(),
                order.getStatus(), order.getPaymentMethod(), order.getPaymentStatus(),
                items, total, order.getCreatedAt());
    }
}