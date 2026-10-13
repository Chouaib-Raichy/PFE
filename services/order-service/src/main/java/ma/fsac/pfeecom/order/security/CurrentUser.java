package ma.fsac.pfeecom.order.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Identité de l'appelant issue du jeton JWT validé.
 *
 * <p>Le {@code customerId} provient EXCLUSIVEMENT du claim {@code customer_id} du
 * jeton (jamais d'un identifiant envoyé par le navigateur). Un administrateur
 * ({@code ROLE_ADMIN}) peut agir sur n'importe quel client et voir toutes les
 * commandes.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Component
public class CurrentUser {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String CUSTOMER_ID_CLAIM = "customer_id";

    public boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
    }

    public Long customerId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            String value = jwtAuthentication.getToken().getClaimAsString(CUSTOMER_ID_CLAIM);
            return (value == null || value.isBlank()) ? null : Long.valueOf(value);
        }
        return null;
    }

    /**
     * Refuse l'accès (403) si l'appelant n'est ni administrateur ni le client ciblé.
     */
    public void requireOwnership(Authentication authentication, Long customerId) {
        if (isAdmin(authentication)) {
            return;
        }
        Long own = customerId(authentication);
        if (own == null || !own.equals(customerId)) {
            throw new AccessDeniedException("Acces interdit aux commandes d'un autre client.");
        }
    }
}
