package ma.fsac.pfeecom.cart;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.fsac.pfeecom.cart.client.CustomerInfo;
import ma.fsac.pfeecom.cart.client.CustomerServiceClient;
import ma.fsac.pfeecom.cart.client.ProductInfo;
import ma.fsac.pfeecom.cart.client.ProductServiceClient;
import ma.fsac.pfeecom.cart.dto.request.AddItemRequest;
import ma.fsac.pfeecom.cart.dto.request.MergeCartRequest;
import ma.fsac.pfeecom.cart.exception.ResourceNotFoundException;
import ma.fsac.pfeecom.cart.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration de l'API /api/carts (MongoDB dédié, Product-Service mocké).
 *
 * <p>Les tests de logique métier s'exécutent en tant qu'administrateur
 * ({@code @WithMockUser(roles = "ADMIN")}) afin d'ignorer le contrôle de propriété ;
 * la sécurité elle-même est couverte par {@code CartSecurityTest}.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class CartApiIntegrationTest {

    private static final long CUSTOMER = 42L;
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
    void cleanDatabase() {
        cartRepository.deleteAll();
        when(customerServiceClient.getCustomer(anyLong()))
                .thenReturn(new CustomerInfo(CUSTOMER, "Yassine", "Alami", "yassine.alami@example.com"));
        when(productServiceClient.getProduct(anyLong()))
                .thenReturn(new ProductInfo(PRODUCT, "Ordinateur Portable", new BigDecimal("2549.99")));
    }

    private String addItemBody(int quantity) throws Exception {
        return objectMapper.writeValueAsString(new AddItemRequest(PRODUCT, quantity));
    }

    @Test
    void addProduct_shouldReturnCartWithSnapshotAndTotal() throws Exception {
        mockMvc.perform(post("/api/carts/" + CUSTOMER + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItemBody(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(CUSTOMER))
                .andExpect(jsonPath("$.items[0].productName").value("Ordinateur Portable"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(2549.99))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].subtotal").value(5099.98))
                .andExpect(jsonPath("$.total").value(5099.98));
    }

    @Test
    void addSameProductTwice_shouldIncrementQuantity() throws Exception {
        mockMvc.perform(post("/api/carts/" + CUSTOMER + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItemBody(1)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/carts/" + CUSTOMER + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItemBody(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(4))
                .andExpect(jsonPath("$.total").value(10199.96));
    }

    @Test
    void addItemForUnknownCustomer_shouldReturn404() throws Exception {
        when(customerServiceClient.getCustomer(anyLong()))
                .thenThrow(ResourceNotFoundException.of("Client", 777L));

        mockMvc.perform(post("/api/carts/777/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItemBody(1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getCartWithoutItems_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/carts/" + CUSTOMER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unknownProduct_shouldReturn404() throws Exception {
        when(productServiceClient.getProduct(anyLong()))
                .thenThrow(ResourceNotFoundException.of("Produit", 999L));

        mockMvc.perform(post("/api/carts/" + CUSTOMER + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddItemRequest(999L, 1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void removeItem_shouldReturnCartWithoutLine() throws Exception {
        String created = mockMvc.perform(post("/api/carts/" + CUSTOMER + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItemBody(1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long itemId = objectMapper.readTree(created).get("items").get(0).get("itemId").asLong();

        mockMvc.perform(delete("/api/carts/" + CUSTOMER + "/items/" + itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void removeUnknownItem_shouldReturn404() throws Exception {
        mockMvc.perform(delete("/api/carts/" + CUSTOMER + "/items/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void clearCart_shouldReturn204Then404() throws Exception {
        mockMvc.perform(post("/api/carts/" + CUSTOMER + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItemBody(1)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/carts/" + CUSTOMER))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/carts/" + CUSTOMER))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidQuantity_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/carts/" + CUSTOMER + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddItemRequest(PRODUCT, 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String mergeBody(int quantity) throws Exception {
        return objectMapper.writeValueAsString(
                new MergeCartRequest(List.of(new AddItemRequest(PRODUCT, quantity))));
    }

    @Test
    void mergeCart_shouldAddGuestItems() throws Exception {
        when(productServiceClient.getProduct(20L))
                .thenReturn(new ProductInfo(20L, "Souris sans fil", new BigDecimal("99.00")));
        String body = objectMapper.writeValueAsString(new MergeCartRequest(
                List.of(new AddItemRequest(10L, 2), new AddItemRequest(20L, 3))));

        mockMvc.perform(post("/api/carts/" + CUSTOMER + "/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.total").value(5396.98));
    }

    @Test
    void mergeCart_isIdempotent_whenReplayed() throws Exception {
        mockMvc.perform(post("/api/carts/" + CUSTOMER + "/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergeBody(3)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/carts/" + CUSTOMER + "/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergeBody(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3));
    }

    @Test
    void mergeCart_keepsGreaterQuantity_forExistingLine() throws Exception {
        mockMvc.perform(post("/api/carts/" + CUSTOMER + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItemBody(2)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/carts/" + CUSTOMER + "/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergeBody(5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(5));
        mockMvc.perform(post("/api/carts/" + CUSTOMER + "/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mergeBody(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(5));
    }
}