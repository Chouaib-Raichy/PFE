package ma.fsac.pfeecom.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sécurité du point d'entrée unique : le gateway agit comme ressource OAuth2 (JWT).
 *
 * <p>Frontière de confiance : le gateway est le SEUL point d'exposition externe de
 * la pile (les services métier ne sont pas publiés sur l'hôte et vivent sur un
 * réseau Docker privé). Il valide donc l'émetteur, la signature et l'expiration du
 * jeton, puis applique la matrice d'autorisation avant tout routage.</p>
 *
 * <p>Matrice : catalogue produits/catégories en lecture = public ; écritures
 * produits/catégories/clients et listing clients = ADMIN ; fiche client unitaire
 * = propriétaire (claim {@code customer_id}) ou ADMIN ; tout le reste
 * (paniers, commandes) = authentifié (contrôle de propriété dans le service).</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                  CustomerProfileAuthorizationManager customerProfileOwnership,
                                                  @Value("${server.port:8080}") int serverPort,
                                                  @Value("${management.server.port:-1}") int managementPort) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(ex -> ex
                        // Port de gestion interne (non publie) : les endpoints d'actuator
                        // (dont /actuator/prometheus) y sont accessibles, et uniquement la.
                        .matchers(managementActuatorMatcher(serverPort, managementPort)).permitAll()
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // Ne JAMAIS exposer les endpoints d'actuator des services en aval a
                        // travers les routes du gateway (protection contre les routes generiques
                        // du discovery locator : /<service-id>/actuator/**).
                        .pathMatchers("/actuator/**", "/*/actuator/**").denyAll()
                        // Documentation agregee (Swagger UI + specs OpenAPI) : publique
                        // (elle ne donne acces a aucune operation protegee — les appels
                        // « Try it out » restent soumis a l'authentification ci-dessous).
                        // /actuator/** est explicitement refuse ci-dessus, donc jamais
                        // expose via Swagger.
                        .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/webjars/**",
                                "/api-docs/**", "/v3/api-docs/**").permitAll()
                        // Catalogue public (lecture seule)
                        .pathMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").permitAll()
                        // Répertoire clients : listing réservé aux administrateurs
                        .pathMatchers(HttpMethod.GET, "/api/customers").hasRole("ADMIN")
                        // Fiche client unitaire : propriétaire (claim customer_id du jeton)
                        // ou administrateur — ferme l'IDOR, aucune confiance dans l'ID d'URL.
                        .pathMatchers(HttpMethod.GET, "/api/customers/*").access(customerProfileOwnership)
                        // Provisionnement du profil (nouvel utilisateur inscrit) : tout
                        // porteur d'un jeton valide peut créer SON profil (identité lue
                        // côté serveur depuis le claim sub). Doit précéder la règle ADMIN
                        // sur POST /api/customers/**.
                        .pathMatchers(HttpMethod.POST, "/api/customers/me").authenticated()
                        // Écritures catalogue/clients réservées aux administrateurs
                        .pathMatchers(HttpMethod.POST, "/api/products/**", "/api/categories/**", "/api/customers/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/api/products/**", "/api/categories/**", "/api/customers/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PATCH, "/api/products/**", "/api/categories/**", "/api/customers/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/api/products/**", "/api/categories/**", "/api/customers/**").hasRole("ADMIN")
                        // Panier, commandes : authentifié (contrôle de propriété en aval)
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    /**
     * N'autorise l'accès aux endpoints d'actuator que lorsqu'ils arrivent sur le
     * port de gestion interne. Si aucun port de gestion distinct n'est configuré,
     * le matcher ne correspond jamais (aucun endpoint d'actuator n'est ouvert sur
     * le port public).
     */
    private ServerWebExchangeMatcher managementActuatorMatcher(int serverPort, int managementPort) {
        boolean distinct = managementPort > 0 && managementPort != serverPort;
        return exchange -> {
            if (!distinct || !exchange.getRequest().getPath().value().startsWith("/actuator")) {
                return ServerWebExchangeMatcher.MatchResult.notMatch();
            }
            var local = exchange.getRequest().getLocalAddress();
            return local != null && local.getPort() == managementPort
                    ? ServerWebExchangeMatcher.MatchResult.match()
                    : ServerWebExchangeMatcher.MatchResult.notMatch();
        };
    }

    /**
     * Décodeur JWT explicite : la clé JWKS est récupérée sur le réseau interne
     * ({@code http://keycloak:8080/...}) tandis que l'émetteur attendu reste l'URL
     * publique ({@code http://localhost:8181/...}), ce qui évite de dépendre de
     * Keycloak au démarrage et valide tout de même le claim {@code iss}.
     */
    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder(
            @Value("${app.security.jwk-set-uri}") String jwkSetUri,
            @Value("${app.security.issuer-uri}") String issuerUri) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuerUri)));
        return decoder;
    }

    private ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> Flux.fromIterable(extractRealmRoles(jwt)));
        return converter;
    }

    /** Convertit {@code realm_access.roles} (Keycloak) en autorités {@code ROLE_*}. */
    static List<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }
}
