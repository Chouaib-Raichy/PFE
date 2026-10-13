package ma.fsac.pfeecom.order.service;

import ma.fsac.pfeecom.order.dto.request.OrderRequest;
import ma.fsac.pfeecom.order.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Contrat du service métier des commandes.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public interface OrderService {

    Page<OrderResponse> findAll(Pageable pageable);

    Page<OrderResponse> findByCustomer(Long customerId, Pageable pageable);

    OrderResponse findById(Long id);

    OrderResponse create(OrderRequest request);
}