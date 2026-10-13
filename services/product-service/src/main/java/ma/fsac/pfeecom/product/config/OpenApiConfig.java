package ma.fsac.pfeecom.product.config;

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
 * Documentation OpenAPI du Product Service.
 *
 * <p>Expose deux mecanismes d'authentification : {@code bearerAuth} (jeton JWT
 * Keycloak colle manuellement) et {@code oauth2} (Authorization Code + PKCE
 * contre le realm {@code pfeecom}). Le security scheme est declare au niveau du
 * document ; l'application reelle des droits reste faite par l'API Gateway.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI productOpenAPI(
            @Value("${app.openapi.oauth.authorization-url:http://localhost:8181/realms/pfeecom/protocol/openid-connect/auth}") String authorizationUrl,
            @Value("${app.openapi.oauth.token-url:http://localhost:8181/realms/pfeecom/protocol/openid-connect/token}") String tokenUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title("Product Service API")
                        .description("""
                                Catalogue produits et categories de la plateforme e-commerce PFE.
                                Source de verite : PostgreSQL. Recherche : Elasticsearch (repli PostgreSQL
                                marque `degraded=true`). Images : stockage objet MinIO (S3).

                                Acces via l'API Gateway : http://localhost:8090 (le service n'est pas publie
                                sur l'hote en Docker). Lecture catalogue publique ; ecritures et operations
                                d'administration reservees au role ADMIN.
                                """)
                        .version("1.0.0")
                        .license(new License().name("Projet academique PFE FSAC")))
                // Serveur relatif : Swagger UI appelle l'origine qui sert la page
                // (gateway 8090 quand la doc est agregee, port du service en local).
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
