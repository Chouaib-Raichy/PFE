package ma.fsac.pfeecom.order.dto.response;

import ma.fsac.pfeecom.order.domain.entity.OrderStatus;
import ma.fsac.pfeecom.order.domain.entity.PaymentMethod;
import ma.fsac.pfeecom.order.domain.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de réponse exposant une commande via l'API REST.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record OrderResponse(
        Long id,
        Long customerId,
        String customerName,
        String customerEmail,
        OrderStatus status,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        List<OrderItemResponse> items,
        BigDecimal total,
        LocalDateTime createdAt
) {}