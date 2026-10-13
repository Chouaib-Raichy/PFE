package ma.fsac.pfeecom.cart;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.fsac.pfeecom.cart.client.CustomerInfo;
import ma.fsac.pfeecom.cart.client.CustomerServiceClient;
import ma.fsac.pfeecom.cart.client.ProductInfo;
import ma.fsac.pfeecom.cart.client.ProductServiceClient;
import ma.fsac.pfeecom.cart.dto.request.AddItemRequest;
import ma.fsac.pfeecom.cart.dto.request.MergeCartRequest;
import ma.fsac.pfeecom.cart.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de sécurité : authenticité, propriété du panier (cross-customer) et rôle admin.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@SpringBootTest
@AutoConfigureMockMvc
class CartSecurityTest {

    private static final long OWNER = 42L;
    private static final long OTHER = 7L;
    private static final long PRODUCT = 10L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CartRepository cartRepository;

    @MockBean
    private ProductServiceClient productServiceClient;

    @MockBean
    private CustomerServiceClient customerServiceClient;

    @BeforeEach
    void setUp() {
        cartRepository.deleteAll();
        when(customerServiceClient.getCustomer(anyLong()))
                .thenReturn(new CustomerInfo(OWNER, "Yassine", "Alami", "yassine.alami@example.com"));
        when(productServiceClient.getProduct(anyLong()))
                .thenReturn(new ProductInfo(PRODUCT, "Ordinateur Portable", new BigDecimal("2549.99")));
    }

    @Test
    void unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/carts/" + OWNER))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void owner_shouldReachOwnCartLogic() throws Exception {
        mockMvc.perform(get("/api/carts/" + OWNER)
                        .with(jwt().jwt(j -> j.claim("customer_id", String.valueOf(OWNER)))))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossCustomer_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/carts/" + OTHER + "/items")
                        .with(jwt().jwt(j -> j.claim("customer_id", String.valueOf(OWNER))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddItemRequest(PRODUCT, 1))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void admin_shouldActOnAnyCustomerCart() throws Exception {
        mockMvc.perform(post("/api/carts/" + OWNER + "/items")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddItemRequest(PRODUCT, 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void merge_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/carts/" + OWNER + "/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MergeCartRequest(List.of(new AddItemRequest(PRODUCT, 1))))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void merge_crossCustomer_shouldReturn403() throws Exception {
        String body = objectMapper.writeValueAsString(
                new MergeCartRequest(List.of(new AddItemRequest(PRODUCT, 1))));

        mockMvc.perform(post("/api/carts/" + OTHER + "/merge")
                        .with(jwt().jwt(j -> j.claim("customer_id", String.valueOf(OWNER))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}