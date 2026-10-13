package ma.fsac.pfeecom.order.repository;

import ma.fsac.pfeecom.order.domain.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Accès aux données des commandes (MongoDB).
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Repository
public interface OrderRepository extends MongoRepository<Order, Long> {

    Page<Order> findByCustomerId(Long customerId, Pageable pageable);
}