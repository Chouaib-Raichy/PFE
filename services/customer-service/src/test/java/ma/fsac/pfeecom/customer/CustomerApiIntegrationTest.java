package ma.fsac.pfeecom.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.fsac.pfeecom.customer.domain.entity.Customer;
import ma.fsac.pfeecom.customer.dto.request.CustomerRequest;
import ma.fsac.pfeecom.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration de l'API /api/customers (PostgreSQL dédié, Eureka désactivé).
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@SpringBootTest
@AutoConfigureMockMvc
class CustomerApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @BeforeEach
    void cleanDatabase() {
        customerRepository.deleteAll();
    }

    private CustomerRequest validRequest() {
        return new CustomerRequest("Yassine", "Alami", "yassine.alami@example.com", "0661234567");
    }

    @Test
    void createAndGetCustomer_shouldReturn201And200() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("yassine.alami@example.com"))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/customers/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Yassine"))
                .andExpect(jsonPath("$.lastName").value("Alami"));
    }

    @Test
    void getUnknownCustomer_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/customers/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void createDuplicateEmail_shouldReturn409() throws Exception {
        String body = objectMapper.writeValueAsString(validRequest());
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void updateCustomer_shouldReturn200() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated()).andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        CustomerRequest updated = new CustomerRequest("Yassine", "Bennani", "yassine.bennani@example.com", null);
        mockMvc.perform(put("/api/customers/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Bennani"))
                .andExpect(jsonPath("$.phone").value("0661234567"));
    }

    @Test
    void deleteCustomer_shouldReturn200Then404() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated()).andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/customers/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        mockMvc.perform(get("/api/customers/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void findAll_shouldReturnPagedList() throws Exception {
        for (int i = 1; i <= 3; i++) {
            CustomerRequest req = new CustomerRequest("Client", "Numero" + i,
                    "client" + i + "@example.com", null);
            mockMvc.perform(post("/api/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/customers").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void createInvalidCustomer_shouldReturn400() throws Exception {
        CustomerRequest invalid = new CustomerRequest("", "Alami", "not-an-email", null);
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.firstName").exists())
                .andExpect(jsonPath("$.details.email").exists());
    }

    @Test
    void malformedJson_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    @Test
    void duplicateEmailKey_isRejectedByDatabaseConstraint() {
        customerRepository.saveAndFlush(Customer.builder()
                .firstName("Premier").lastName("Client")
                .email("doublon@example.com").emailKey("doublon@example.com")
                .build());

        assertThatThrownBy(() -> customerRepository.saveAndFlush(Customer.builder()
                .firstName("Second").lastName("Client")
                .email("Doublon@Example.com").emailKey("doublon@example.com")
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}