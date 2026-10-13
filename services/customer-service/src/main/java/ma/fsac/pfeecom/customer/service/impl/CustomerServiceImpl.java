package ma.fsac.pfeecom.customer.service.impl;

import lombok.RequiredArgsConstructor;
import ma.fsac.pfeecom.customer.domain.entity.Customer;
import ma.fsac.pfeecom.customer.dto.request.CustomerRequest;
import ma.fsac.pfeecom.customer.dto.response.CustomerResponse;
import ma.fsac.pfeecom.customer.exception.DuplicateResourceException;
import ma.fsac.pfeecom.customer.exception.ResourceNotFoundException;
import ma.fsac.pfeecom.customer.mapper.CustomerMapper;
import ma.fsac.pfeecom.customer.repository.CustomerRepository;
import ma.fsac.pfeecom.customer.service.CustomerService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Implémentation du service métier des clients.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private static final String RESOURCE = "Client";

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    @Override
    public Page<CustomerResponse> findAll(Pageable pageable) {
        return customerRepository.findAll(pageable).map(customerMapper::toResponse);
    }

    @Override
    public CustomerResponse findById(Long id) {
        return customerMapper.toResponse(getCustomer(id));
    }

    @Override
    public CustomerResponse create(CustomerRequest request) {
        String emailKey = normalizeEmail(request.email());
        if (customerRepository.existsByEmailKey(emailKey)) {
            throw duplicate(request.email());
        }
        Customer customer = customerMapper.toEntity(request);
        customer.setEmailKey(emailKey);
        try {
            return customerMapper.toResponse(customerRepository.save(customer));
        } catch (DataIntegrityViolationException ex) {
            throw duplicate(request.email());
        }
    }

    @Override
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = getCustomer(id);
        customerMapper.updateFromRequest(request, customer);
        if (request.email() != null) {
            customer.setEmailKey(normalizeEmail(request.email()));
        }
        try {
            return customerMapper.toResponse(customerRepository.save(customer));
        } catch (DataIntegrityViolationException ex) {
            throw duplicate(request.email());
        }
    }

    @Override
    public void delete(Long id) {
        Customer customer = getCustomer(id);
        customerRepository.delete(customer);
    }

    private Customer getCustomer(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private DuplicateResourceException duplicate(String email) {
        return new DuplicateResourceException("Un client existe déjà avec l'email : " + email);
    }
}