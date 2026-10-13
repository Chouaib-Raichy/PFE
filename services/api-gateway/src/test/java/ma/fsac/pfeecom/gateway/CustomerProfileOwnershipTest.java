package ma.fsac.pfeecom.gateway;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/**
 * Tests d'autorisation de la fiche client unitaire
 * ({@code GET /api/customers/{id}}), controle de propriete par claim JWT.
 *
 * <p>Aucune route n'est declaree (config-client et discovery desactives) : le
 * filtre de securite s'execute avant le routage, donc « refus » = 401/403 et
 * « autorise » = poursuite du routage vers une route inexistante, pas une erreur
 * de securite.</p>
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
class CustomerProfileOwnershipTest {

    @Autowired
    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    private int status(WebTestClient client, String uri) {
        return client.get().uri("http://localhost:" + port + uri).exchange()
                .expectBody().returnResult().getRawStatusCode();
    }

    private WebTestClient customer(String customerIdClaim) {
        var mutator = mockJwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        if (customerIdClaim != null) {
            mutator = mutator.jwt(jwt -> jwt.claim("customer_id", customerIdClaim));
        }
        return webTestClient.mutateWith(mutator);
    }

    @Test
    void unauthenticated_customerProfile_401() {
        Assertions.assertEquals(401, status(webTestClient, "/api/customers/2"));
    }

    @Test
    void customer_ownProfile_allowed() {
        int s = status(customer("1"), "/api/customers/1");
        Assertions.assertNotEquals(401, s);
        Assertions.assertNotEquals(403, s);
    }

    @Test
    void customer_otherProfile_403() {
        Assertions.assertEquals(403, status(customer("1"), "/api/customers/2"));
    }

    @Test
    void admin_otherProfile_allowed() {
        WebTestClient client = webTestClient.mutateWith(
                mockJwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
        int s = status(client, "/api/customers/2");
        Assertions.assertNotEquals(401, s);
        Assertions.assertNotEquals(403, s);
    }

    @Test
    void customer_missingClaim_denied() {
        Assertions.assertEquals(403, status(customer(null), "/api/customers/1"));
    }

    @Test
    void customer_invalidClaim_denied() {
        Assertions.assertEquals(403, status(customer("not-a-number"), "/api/customers/1"));
    }
}
