package ma.fsac.pfeecom.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verification de la specification OpenAPI exposee par le Product Service.
 *
 * <p>Controle que la spec est valide, que les chemins attendus (CRUD, images,
 * recherche) et les schemes de securite (bearerAuth + oauth2) sont presents.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductOpenApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void apiDocs_isValidAndExposesExpectedContract() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode doc = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(doc.path("openapi").asText()).startsWith("3.");
        assertThat(doc.path("info").path("title").asText()).isEqualTo("Product Service API");
        assertThat(doc.path("info").path("version").asText()).isEqualTo("1.0.0");

        JsonNode paths = doc.path("paths");
        assertThat(paths.has("/api/products")).isTrue();
        assertThat(paths.has("/api/products/{id}")).isTrue();
        assertThat(paths.has("/api/products/{id}/image")).isTrue();
        assertThat(paths.has("/api/products/search")).isTrue();
        assertThat(paths.has("/api/products/suggest")).isTrue();
        assertThat(paths.has("/api/products/reindex")).isTrue();
        assertThat(paths.has("/api/categories")).isTrue();
        assertThat(paths.path("/api/products/{id}/image").has("post")).isTrue();
        assertThat(paths.path("/api/products/{id}/image").has("get")).isTrue();

        JsonNode schemes = doc.path("components").path("securitySchemes");
        assertThat(schemes.has("bearerAuth")).isTrue();
        assertThat(schemes.path("bearerAuth").path("scheme").asText()).isEqualTo("bearer");
        assertThat(schemes.has("oauth2")).isTrue();
        assertThat(schemes.path("oauth2").path("flows").path("authorizationCode")
                .path("authorizationUrl").asText()).contains("/realms/pfeecom/");

        JsonNode productRequest = doc.path("components").path("schemas").path("ProductRequest");
        assertThat(productRequest.path("required").toString()).contains("name", "price", "stockQuantity");
    }
}
