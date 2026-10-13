package ma.fsac.pfeecom.product.repository;

import ma.fsac.pfeecom.product.domain.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository JPA pour l'entité Category.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
}
