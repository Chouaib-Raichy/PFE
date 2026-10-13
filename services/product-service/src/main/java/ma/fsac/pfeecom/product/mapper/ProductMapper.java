package ma.fsac.pfeecom.product.mapper;

import ma.fsac.pfeecom.product.domain.entity.Product;
import ma.fsac.pfeecom.product.dto.request.ProductRequest;
import ma.fsac.pfeecom.product.dto.response.ProductResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapping entre les DTOs et l'entité Product (via MapStruct).
 *
 * <p>La catégorie est stockée en copie (categoryId + categoryName) directement
 * dans le document Product, plus aucune association gérée par l'ORM.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "categoryId", ignore = true)
    @Mapping(target = "categoryName", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Product toEntity(ProductRequest request);

    ProductResponse toResponse(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "categoryId", ignore = true)
    @Mapping(target = "categoryName", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromRequest(ProductRequest request, @MappingTarget Product product);
}