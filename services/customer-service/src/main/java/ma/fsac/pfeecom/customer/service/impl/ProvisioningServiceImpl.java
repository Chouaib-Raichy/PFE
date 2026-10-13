package ma.fsac.pfeecom.customer.service.impl;

import lombok.RequiredArgsConstructor;
import ma.fsac.pfeecom.customer.client.KeycloakAdminClient;
import ma.fsac.pfeecom.customer.domain.entity.Customer;
import ma.fsac.pfeecom.customer.dto.response.CustomerResponse;
import ma.fsac.pfeecom.customer.exception.DuplicateResourceException;
import ma.fsac.pfeecom.customer.exception.InvalidProfileException;
import ma.fsac.pfeecom.customer.exception.UnauthorizedException;
import ma.fsac.pfeecom.customer.mapper.CustomerMapper;
import ma.fsac.pfeecom.customer.repository.CustomerRepository;
import ma.fsac.pfeecom.customer.service.ProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * Implémentation du provisionnement client.
 *
 * <p>L'identité provient exclusivement du {@code sub} du jeton validé (clé
 * immuable côté Keycloak) : l'identifiant client n'est jamais accepté depuis le
 * client. La création est idempotente (contrainte UNIQUE sur {@code keycloak_sub})
 * et le claim {@code customer_id} est ensuite matérialisé dans Keycloak via le
 * compte de service, de sorte que les jetons suivants le portent.</p>
 *
 * <p>En cas d'échec de l'appel Keycloak, le profil déjà créé est conservé : le
 * prochain appel, idempotent, re-tente simplement la pose de l'attribut.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Service
@RequiredArgsConstructor
public class ProvisioningServiceImpl implements ProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningServiceImpl.class);
    private static final int MAX_NAME_LENGTH = 150;

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final KeycloakAdminClient keycloakAdminClient;

    @Override
    public CustomerResponse ensureProfile(Jwt jwt) {
        String sub = jwt != null ? jwt.getSubject() : null;
        if (sub == null || sub.isBlank()) {
            throw new UnauthorizedException("Jeton sans identifiant utilisateur (sub) exploitable.");
        }

        Customer customer = customerRepository.findByKeycloakSub(sub)
                .orElseGet(() -> linkByClaimedId(jwt, sub)
                        .orElseGet(() -> createProfile(jwt, sub)));

        // Matérialise le claim customer_id (idempotent ; répare un éventuel
        // provisionnement partiel). Non journalisé (aucune PII).
        keycloakAdminClient.setCustomerIdAttribute(sub, customer.getId());
        return customerMapper.toResponse(customer);
    }

    /**
     * Rattache un profil existant non encore lié (utilisateur de démonstration ou
     * profil pré-créé par un administrateur) lorsque le jeton porte déjà un claim
     * {@code customer_id}. Le claim est signé par Keycloak : il fait foi.
     */
    private Optional<Customer> linkByClaimedId(Jwt jwt, String sub) {
        Long claimedId = parseId(jwt.getClaimAsString("customer_id"));
        if (claimedId == null) {
            return Optional.empty();
        }
        return customerRepository.findById(claimedId)
                .filter(customer -> customer.getKeycloakSub() == null)
                .map(customer -> {
                    customer.setKeycloakSub(sub);
                    return customerRepository.save(customer);
                });
    }

    private Customer createProfile(Jwt jwt, String sub) {
        String email = trimToNull(jwt.getClaimAsString("email"));
        if (email == null) {
            throw new InvalidProfileException("L'adresse email est requise pour creer un profil client.");
        }
        String emailKey = email.toLowerCase(Locale.ROOT);
        if (customerRepository.existsByEmailKey(emailKey)) {
            throw new DuplicateResourceException("Un client existe deja avec l'email : " + email);
        }

        Customer customer = Customer.builder()
                .firstName(name(jwt, "given_name"))
                .lastName(name(jwt, "family_name"))
                .email(email)
                .emailKey(emailKey)
                .keycloakSub(sub)
                .build();
        try {
            return customerRepository.save(customer);
        } catch (DataIntegrityViolationException ex) {
            // Course entre deux premiers appels : le profil a deja ete cree.
            Optional<Customer> existing = customerRepository.findByKeycloakSub(sub);
            if (existing.isPresent()) {
                return existing.get();
            }
            throw new DuplicateResourceException("Un client existe deja avec l'email : " + email);
        }
    }

    /** Nom lisible : claim dedie, sinon identifiant de connexion, sinon valeur de repli. */
    private String name(Jwt jwt, String claim) {
        String value = trimToNull(jwt.getClaimAsString(claim));
        if (value == null) {
            value = trimToNull(jwt.getClaimAsString("preferred_username"));
        }
        if (value == null) {
            value = "Client";
        }
        return value.length() > MAX_NAME_LENGTH ? value.substring(0, MAX_NAME_LENGTH) : value;
    }

    private Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
