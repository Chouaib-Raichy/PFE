package ma.fsac.pfeecom.customer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Sécurité du Customer Service.
 *
 * <p>Le service agit comme ressource OAuth2 (JWT) uniquement pour lire l'identité
 * de l'appelant sur {@code POST /api/customers/me} (provisionnement). Toutes les
 * autres routes restent {@code permitAll} : la frontière de confiance demeure le
 * gateway (matrice d'autorisation + contrôle de propriété), et les appels
 * inter-services (cart/order vers customer) circulent sans jeton.</p>
 *
 * <p>Un jeton présent est toujours validé (signature, émetteur, expiration) : un
 * jeton invalide est rejeté (401) même si la route est publique. En l'absence de
 * jeton, la requête poursuit et le contrôleur décide (401 sur /me).</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)))
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
}
