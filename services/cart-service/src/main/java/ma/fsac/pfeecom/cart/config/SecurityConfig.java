package ma.fsac.pfeecom.cart.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sécurité du Cart Service : ressource OAuth2 (JWT) + endpoints d'actuator publics.
 *
 * <p>Le panier est une ressource personnelle : l'authentification est vérifiée ici
 * et le contrôle de propriété (le client ne peut agir que sur SON panier) est
 * appliqué dans la couche contrôleur via {@code CurrentUser}.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                            JwtAuthenticationConverter realmRolesConverter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // Le service n'est pas publie sur l'hote : /actuator/prometheus
                        // reste donc accessible uniquement dans le reseau Docker (Prometheus).
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(realmRolesConverter)))
                .build();
    }

    /**
     * Décodeur JWT explicite : clé JWKS récupérée sur le réseau interne
     * ({@code http://keycloak:8080/...}), émetteur attendu = URL publique
     * ({@code http://localhost:8181/...}) — pas de dépendance à Keycloak au démarrage.
     */
    @Bean
    JwtDecoder jwtDecoder(@Value("${app.security.jwk-set-uri}") String jwkSetUri,
                          @Value("${app.security.issuer-uri}") String issuerUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuerUri)));
        return decoder;
    }

    /** Convertit {@code realm_access.roles} (Keycloak) en autorités {@code ROLE_*}. */
    @Bean
    JwtAuthenticationConverter realmRolesConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(realmRolesConverterFunction());
        return converter;
    }

    private Converter<Jwt, Collection<GrantedAuthority>> realmRolesConverterFunction() {
        return jwt -> {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
                return List.of();
            }
            return roles.stream()
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());
        };
    }
}
