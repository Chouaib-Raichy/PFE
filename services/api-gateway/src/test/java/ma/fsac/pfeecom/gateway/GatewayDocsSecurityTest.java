package ma.fsac.pfeecom.gateway;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verification de l'exposition de la documentation (Swagger UI + specs) via le
 * gateway, et de la non-regression de la frontiere de securite.
 *
 * <p>La documentation est publique (elle ne donne acces a aucune operation
 * protegee) tandis que les endpoints d'actuator restent refuses.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.gateway.discovery.locator.enabled=false"
        })
@AutoConfigureWebTestClient
class GatewayDocsSecurityTest {

    @Autowired
    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    private int rawStatus(WebTestClient.ResponseSpec exchange) {
        return exchange.expectBody().returnResult().getRawStatusCode();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void swaggerUi_isPublic_noToken() {
        int status = rawStatus(webTestClient.get().uri(url("/swagger-ui/index.html")).exchange());
        Assertions.assertNotEquals(401, status);
        Assertions.assertNotEquals(403, status);
    }

    @Test
    void swaggerRedirect_isPublic_noToken() {
        int status = rawStatus(webTestClient.get().uri(url("/swagger-ui.html")).exchange());
        Assertions.assertNotEquals(401, status);
        Assertions.assertNotEquals(403, status);
    }

    @Test
    void aggregatedApiDocs_isPublic_noToken() {
        // La route de documentation est autorisee par la securite (le routage
        // cible un service absent en test : l'echec eventuel n'est pas 401/403).
        int status = rawStatus(webTestClient.get().uri(url("/api-docs/product-service")).exchange());
        Assertions.assertNotEquals(401, status);
        Assertions.assertNotEquals(403, status);
    }

    @Test
    void swaggerConfig_isPublic_noToken() {
        webTestClient.get().uri(url("/v3/api-docs/swagger-config"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.configUrl").exists();
    }

    @Test
    void actuator_isStillDenied_noToken() {
        int status = rawStatus(webTestClient.get().uri(url("/actuator/env")).exchange());
        Assertions.assertTrue(status == 401 || status == 403,
                "actuator doit rester refuse (observe: " + status + ")");
    }
}
