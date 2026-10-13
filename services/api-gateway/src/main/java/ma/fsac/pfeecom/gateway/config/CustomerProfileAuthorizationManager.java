package ma.fsac.pfeecom.gateway.config;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Autorisation de propriete pour {@code GET /api/customers/{id}}.
 *
 * <p>Ferme un IDOR : la fiche client n'est accessible qu'a son proprietaire ou a
 * un administrateur. L'identite provient EXCLUSIVEMENT du claim JWT
 * {@code customer_id} (jamais de l'URL ni du corps de requete). Toute identite
 * absente, non numerique ou non renseignee est refusee (fail closed).</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Component
public class CustomerProfileAuthorizationManager
        implements ReactiveAuthorizationManager<AuthorizationContext> {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String CUSTOMER_ID_CLAIM = "customer_id";
    private static final String BASE_PATH = "/api/customers/";

    @Override
    public Mono<AuthorizationDecision> check(Mono<Authentication> authentication,
                                             AuthorizationContext context) {
        Long requestedId = requestedCustomerId(context.getExchange().getRequest().getPath().value());
        return authentication
                .map(auth -> new AuthorizationDecision(isAllowed(auth, requestedId)))
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private boolean isAllowed(Authentication authentication, Long requestedId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (isAdmin(authentication)) {
            return true;
        }
        Long ownId = trustedCustomerId(authentication);
        return ownId != null && ownId.equals(requestedId);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
    }

    /** Identifiant client issu du seul claim JWT valide ({@code customer_id}). */
    private Long trustedCustomerId(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return null;
        }
        return parseId(jwtAuthentication.getToken().getClaimAsString(CUSTOMER_ID_CLAIM));
    }

    /** Identifiant cible extrait de {@code /api/customers/{id}}. */
    private Long requestedCustomerId(String path) {
        if (path == null || !path.startsWith(BASE_PATH)) {
            return null;
        }
        String tail = path.substring(BASE_PATH.length());
        int slash = tail.indexOf('/');
        if (slash >= 0) {
            tail = tail.substring(0, slash);
        }
        return parseId(tail);
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
}
