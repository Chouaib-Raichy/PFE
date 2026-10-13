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
 * Tests de la matrice d'autorisation du gateway (frontière de confiance).
 *
 * <p>Aucune route n'est déclarée (config-client et discovery désactivés) : le
 * filtre de sécurité s'exécute avant le routage, donc « accès refusé » = 401/403
 * et « autorisé » = poursuite du routage vers une route inexistante (404/5xx),
 * pas une erreur de sécurité.</p>
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
class GatewaySecurityTest {

    @Autowired
    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    private int rawStatus(WebTestClient.ResponseSpec exchange) {
        return exchange.expectBody().returnResult().getRawStatusCode();
    }

    @Test
    void unauthenticated_protectedRoute_401() {
        int status = rawStatus(webTestClient.get().uri("http://localhost:" + port + "/api/carts/1").exchange());
        Assertions.assertEquals(401, status);
    }

    @Test
    void unauthenticated_orders_401() {
        int status = rawStatus(webTestClient.get().uri("http://localhost:" + port + "/api/orders").exchange());
        Assertions.assertEquals(401, status);
    }

    @Test
    void customerRole_customerListing_403() {
        WebTestClient client = webTestClient.mutateWith(
                mockJwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        int status = rawStatus(client.get().uri("http://localhost:" + port + "/api/customers").exchange());
        Assertions.assertEquals(403, status);
    }

    @Test
    void unauthenticated_provisionCustomer_401() {
        int status = rawStatus(webTestClient.post()
                .uri("http://localhost:" + port + "/api/customers/me").exchange());
        Assertions.assertEquals(401, status);
    }

    @Test
    void customerRole_provisionCustomer_isNotBlockedBySecurity() {
        WebTestClient client = webTestClient.mutateWith(
                mockJwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        int status = rawStatus(client.post()
                .uri("http://localhost:" + port + "/api/customers/me").exchange());
        Assertions.assertNotEquals(403, status);
        Assertions.assertNotEquals(401, status);
    }

    @Test
    void customerRole_createCustomer_403() {
        WebTestClient client = webTestClient.mutateWith(
                mockJwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        int status = rawStatus(client.post()
                .uri("http://localhost:" + port + "/api/customers").exchange());
        Assertions.assertEquals(403, status);
    }

    @Test
    void customerRole_adminWrite_403() {
        WebTestClient client = webTestClient.mutateWith(
                mockJwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        int status = rawStatus(client.post().uri("http://localhost:" + port + "/api/products").exchange());
        Assertions.assertEquals(403, status);
    }

    @Test
    void noToken_publicCatalog_isNotBlockedBySecurity() {
        int status = rawStatus(webTestClient.get().uri("http://localhost:" + port + "/api/products").exchange());
        Assertions.assertNotEquals(401, status);
    }

    @Test
    void adminRole_customerListing_isNotBlockedBySecurity() {
        WebTestClient client = webTestClient.mutateWith(
                mockJwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
        int status = rawStatus(client.get().uri("http://localhost:" + port + "/api/customers").exchange());
        Assertions.assertNotEquals(403, status);
        Assertions.assertNotEquals(401, status);
    }

    @Test
    void noToken_publicSearch_isNotBlockedBySecurity() {
        int status = rawStatus(webTestClient.get()
                .uri("http://localhost:" + port + "/api/products/search?q=portable").exchange());
        Assertions.assertNotEquals(401, status);
    }

    @Test
    void noToken_publicSuggest_isNotBlockedBySecurity() {
        int status = rawStatus(webTestClient.get()
                .uri("http://localhost:" + port + "/api/products/suggest?q=port").exchange());
        Assertions.assertNotEquals(401, status);
    }

    @Test
    void customerRole_reindex_403() {
        WebTestClient client = webTestClient.mutateWith(
                mockJwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        int status = rawStatus(client.post().uri("http://localhost:" + port + "/api/products/reindex").exchange());
        Assertions.assertEquals(403, status);
    }

    @Test
    void adminRole_reindex_isNotBlockedBySecurity() {
        WebTestClient client = webTestClient.mutateWith(
                mockJwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
        int status = rawStatus(client.post().uri("http://localhost:" + port + "/api/products/reindex").exchange());
        Assertions.assertNotEquals(403, status);
        Assertions.assertNotEquals(401, status);
    }

    @Test
    void noToken_productImage_isNotBlockedBySecurity() {
        int status = rawStatus(webTestClient.get()
                .uri("http://localhost:" + port + "/api/products/1/image").exchange());
        Assertions.assertNotEquals(401, status);
    }

    @Test
    void unauthenticated_uploadImage_401() {
        int status = rawStatus(webTestClient.post()
                .uri("http://localhost:" + port + "/api/products/1/image").exchange());
        Assertions.assertEquals(401, status);
    }

    @Test
    void customerRole_uploadImage_403() {
        WebTestClient client = webTestClient.mutateWith(
                mockJwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        int status = rawStatus(client.post()
                .uri("http://localhost:" + port + "/api/products/1/image").exchange());
        Assertions.assertEquals(403, status);
    }

    @Test
    void adminRole_uploadImage_isNotBlockedBySecurity() {
        WebTestClient client = webTestClient.mutateWith(
                mockJwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
        int status = rawStatus(client.post()
                .uri("http://localhost:" + port + "/api/products/1/image").exchange());
        Assertions.assertNotEquals(403, status);
        Assertions.assertNotEquals(401, status);
    }

    @Test
    void customerRole_deleteImage_403() {
        WebTestClient client = webTestClient.mutateWith(
                mockJwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        int status = rawStatus(client.delete()
                .uri("http://localhost:" + port + "/api/products/1/image").exchange());
        Assertions.assertEquals(403, status);
    }

    @Test
    void adminRole_deleteImage_isNotBlockedBySecurity() {
        WebTestClient client = webTestClient.mutateWith(
                mockJwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
        int status = rawStatus(client.delete()
                .uri("http://localhost:" + port + "/api/products/1/image").exchange());
        Assertions.assertNotEquals(403, status);
        Assertions.assertNotEquals(401, status);
    }
}