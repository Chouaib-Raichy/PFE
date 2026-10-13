package ma.fsac.pfeecom.cart;

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
 * Verification de la specification OpenAPI du Cart Service.
 *
 * <p>Confirme aussi que {@code /v3/api-docs} reste accessible SANS jeton
 * (documentation publique) alors que les endpoints metier sont proteges.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@SpringBootTest
@AutoConfigureMockMvc
class CartOpenApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void apiDocs_isValidAndPublic() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode doc = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(doc.path("info").path("title").asText()).isEqualTo("Cart Service API");
        assertThat(doc.path("paths").has("/api/carts/{customerId}")).isTrue();
        assertThat(doc.path("paths").has("/api/carts/{customerId}/items")).isTrue();

        JsonNode schemes = doc.path("components").path("securitySchemes");
        assertThat(schemes.has("bearerAuth")).isTrue();
        assertThat(schemes.has("oauth2")).isTrue();
    }
}
