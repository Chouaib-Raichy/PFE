package ma.fsac.pfeecom.customer.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Documentation OpenAPI du Customer Service.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI customerOpenAPI(
            @Value("${app.openapi.oauth.authorization-url:http://localhost:8181/realms/pfeecom/protocol/openid-connect/auth}") String authorizationUrl,
            @Value("${app.openapi.oauth.token-url:http://localhost:8181/realms/pfeecom/protocol/openid-connect/token}") String tokenUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title("Customer Service API")
                        .description("""
                                Gestion des clients de la plateforme e-commerce PFE (PostgreSQL, email unique).

                                Acces via l'API Gateway : http://localhost:8090. Le listing et les ecritures
                                sont reserves au role ADMIN ; la fiche d'un client authentifie est accessible
                                a ce client (et a un administrateur).
                                """)
                        .version("1.0.0")
                        .license(new License().name("Projet academique PFE FSAC")))
                .servers(List.of(new Server().url("/")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Jeton d'acces Keycloak (realm pfeecom). Coller la valeur de `access_token`."))
                        .addSecuritySchemes("oauth2", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .description("Authorization Code + PKCE (client public `pfeecom-swagger`).")
                                .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                                        .authorizationUrl(authorizationUrl)
                                        .tokenUrl(tokenUrl)
                                        .scopes(new Scopes().addString("openid", "Authentification OpenID Connect"))))));
    }
}
