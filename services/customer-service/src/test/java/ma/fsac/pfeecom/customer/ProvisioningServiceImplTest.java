package ma.fsac.pfeecom.customer;

import ma.fsac.pfeecom.customer.client.KeycloakAdminClient;
import ma.fsac.pfeecom.customer.domain.entity.Customer;
import ma.fsac.pfeecom.customer.dto.response.CustomerResponse;
import ma.fsac.pfeecom.customer.exception.DuplicateResourceException;
import ma.fsac.pfeecom.customer.exception.InvalidProfileException;
import ma.fsac.pfeecom.customer.exception.UnauthorizedException;
import ma.fsac.pfeecom.customer.mapper.CustomerMapper;
import ma.fsac.pfeecom.customer.repository.CustomerRepository;
import ma.fsac.pfeecom.customer.service.impl.ProvisioningServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du provisionnement client (aucun appel reseau).
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@ExtendWith(MockitoExtension.class)
class ProvisioningServiceImplTest {

    private static final String SUB = "9f2c-abc";

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @InjectMocks
    private ProvisioningServiceImpl provisioningService;

    private Jwt jwt(String sub, String email) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(sub)
                .claim("email", email)
                .claim("given_name", "Nadia")
                .claim("family_name", "Kabbaj")
                .build();
    }

    @Test
    void newSub_createsProfileAndSetsClaimAttribute() {
        when(customerRepository.findByKeycloakSub(SUB)).thenReturn(Optional.empty());
        when(customerRepository.existsByEmailKey("nadia@demo.com")).thenReturn(false);
        Customer saved = Customer.builder().id(7L).keycloakSub(SUB)
                .firstName("Nadia").lastName("Kabbaj")
                .email("nadia@demo.com").emailKey("nadia@demo.com").build();
        when(customerRepository.save(any(Customer.class))).thenReturn(saved);
        CustomerResponse expected = new CustomerResponse(7L, "Nadia", "Kabbaj", "nadia@demo.com", null, null, null);
        when(customerMapper.toResponse(saved)).thenReturn(expected);

        CustomerResponse result = provisioningService.ensureProfile(jwt(SUB, "nadia@demo.com"));

        assertThat(result).isEqualTo(expected);
        verify(keycloakAdminClient).setCustomerIdAttribute(SUB, 7L);
    }

    @Test
    void existingSub_isIdempotent_noInsert_butAttributeEnsured() {
        Customer existing = Customer.builder().id(5L).keycloakSub(SUB)
                .email("nadia@demo.com").emailKey("nadia@demo.com").build();
        when(customerRepository.findByKeycloakSub(SUB)).thenReturn(Optional.of(existing));
        when(customerMapper.toResponse(existing))
                .thenReturn(new CustomerResponse(5L, "Nadia", "Kabbaj", "nadia@demo.com", null, null, null));

        provisioningService.ensureProfile(jwt(SUB, "nadia@demo.com"));

        verify(customerRepository, never()).save(any(Customer.class));
        verify(keycloakAdminClient).setCustomerIdAttribute(SUB, 5L);
    }

    @Test
    void existingProfileClaimedBySeedUser_isLinked_notDuplicated() {
        // Utilisateur de demonstration : pas de keycloak_sub mais un claim customer_id = 1.
        Jwt seed = Jwt.withTokenValue("token").header("alg", "none").subject(SUB)
                .claim("customer_id", "1").claim("email", "yassine.alami@demo.com").build();
        when(customerRepository.findByKeycloakSub(SUB)).thenReturn(Optional.empty());
        Customer seedCustomer = Customer.builder().id(1L).keycloakSub(null)
                .email("yassine.alami@demo.com").emailKey("yassine.alami@demo.com").build();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(seedCustomer));
        when(customerRepository.save(seedCustomer)).thenReturn(seedCustomer);
        when(customerMapper.toResponse(seedCustomer))
                .thenReturn(new CustomerResponse(1L, "Yassine", "Alami", "yassine.alami@demo.com", null, null, null));

        CustomerResponse result = provisioningService.ensureProfile(seed);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(seedCustomer.getKeycloakSub()).isEqualTo(SUB);
        verify(keycloakAdminClient).setCustomerIdAttribute(SUB, 1L);
    }

    @Test
    void duplicateEmail_isRejected() {
        when(customerRepository.findByKeycloakSub(SUB)).thenReturn(Optional.empty());
        when(customerRepository.existsByEmailKey("nadia@demo.com")).thenReturn(true);

        assertThatThrownBy(() -> provisioningService.ensureProfile(jwt(SUB, "nadia@demo.com")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(keycloakAdminClient, never()).setCustomerIdAttribute(any(), eq(0L));
    }

    @Test
    void missingSubject_isUnauthorized() {
        assertThatThrownBy(() -> provisioningService.ensureProfile(null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void missingEmail_isInvalidProfile() {
        Jwt noEmail = Jwt.withTokenValue("t").header("alg", "none").subject(SUB).build();
        when(customerRepository.findByKeycloakSub(SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provisioningService.ensureProfile(noEmail))
                .isInstanceOf(InvalidProfileException.class);
    }

    @Test
    void concurrentInsert_refetchesBySub() {
        when(customerRepository.findByKeycloakSub(SUB)).thenReturn(Optional.empty());
        when(customerRepository.existsByEmailKey("nadia@demo.com")).thenReturn(false);
        when(customerRepository.save(any(Customer.class)))
                .thenThrow(new DataIntegrityViolationException("unique"));
        Customer winner = Customer.builder().id(9L).keycloakSub(SUB)
                .email("nadia@demo.com").emailKey("nadia@demo.com").build();
        // Premier appel (createProfile) vide, puis relecture apres violation -> present.
        when(customerRepository.findByKeycloakSub(SUB))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(customerMapper.toResponse(winner))
                .thenReturn(new CustomerResponse(9L, "Nadia", "Kabbaj", "nadia@demo.com", null, null, null));

        CustomerResponse result = provisioningService.ensureProfile(jwt(SUB, "nadia@demo.com"));

        assertThat(result.id()).isEqualTo(9L);
        verify(keycloakAdminClient).setCustomerIdAttribute(SUB, 9L);
    }
}
