package ma.fsac.pfeecom.customer.repository;

import ma.fsac.pfeecom.customer.domain.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Accès aux données des clients (PostgreSQL via Spring Data JPA).
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmailKey(String emailKey);

    boolean existsByEmailKey(String emailKey);

    Optional<Customer> findByKeycloakSub(String keycloakSub);
}
