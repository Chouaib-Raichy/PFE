package ma.fsac.pfeecom.cart.repository;

import ma.fsac.pfeecom.cart.domain.entity.Cart;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Accès aux données des paniers (MongoDB).
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Repository
public interface CartRepository extends MongoRepository<Cart, Long> {

    Optional<Cart> findByCustomerId(Long customerId);
}