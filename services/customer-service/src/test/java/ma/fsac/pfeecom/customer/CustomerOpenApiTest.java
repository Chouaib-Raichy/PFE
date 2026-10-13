package ma.fsac.pfeecom.customer;

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
 * Verification de la specification OpenAPI du Customer Service.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@SpringBootTest
@AutoConfigureMockMvc
class CustomerOpenApiTest {

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
        assertThat(doc.path("info").path("title").asText()).isEqualTo("Customer Service API");
        assertThat(doc.path("paths").has("/api/customers")).isTrue();
        assertThat(doc.path("paths").has("/api/customers/{id}")).isTrue();

        JsonNode schemes = doc.path("components").path("securitySchemes");
        assertThat(schemes.has("bearerAuth")).isTrue();
        assertThat(schemes.has("oauth2")).isTrue();

        JsonNode customerRequest = doc.path("components").path("schemas").path("CustomerRequest");
        assertThat(customerRequest.path("required").toString()).contains("firstName", "lastName", "email");
    }
}
