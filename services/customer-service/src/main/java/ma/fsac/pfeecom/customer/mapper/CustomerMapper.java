package ma.fsac.pfeecom.customer.mapper;

import ma.fsac.pfeecom.customer.domain.entity.Customer;
import ma.fsac.pfeecom.customer.dto.request.CustomerRequest;
import ma.fsac.pfeecom.customer.dto.response.CustomerResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapper MapStruct entre l'entité Customer et ses DTOs.
 *
 * <p>La clé technique {@code emailKey} n'est jamais exposée ni fournie par
 * l'API : elle est calculée par le service.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Mapper(componentModel = "spring")
public interface CustomerMapper {

    CustomerResponse toResponse(Customer customer);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "emailKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Customer toEntity(CustomerRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "emailKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromRequest(CustomerRequest request, @MappingTarget Customer customer);
}