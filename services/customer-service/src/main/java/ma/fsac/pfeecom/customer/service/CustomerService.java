package ma.fsac.pfeecom.customer.service;

import ma.fsac.pfeecom.customer.dto.request.CustomerRequest;
import ma.fsac.pfeecom.customer.dto.response.CustomerResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Contrat du service métier des clients.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public interface CustomerService {

    Page<CustomerResponse> findAll(Pageable pageable);

    CustomerResponse findById(Long id);

    CustomerResponse create(CustomerRequest request);

    CustomerResponse update(Long id, CustomerRequest request);

    void delete(Long id);
}