package ma.fsac.pfeecom.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.fsac.pfeecom.order.client.CustomerInfo;
import ma.fsac.pfeecom.order.client.CustomerServiceClient;
import ma.fsac.pfeecom.order.client.ProductInfo;
import ma.fsac.pfeecom.order.client.ProductServiceClient;
import ma.fsac.pfeecom.order.domain.entity.Order;
import ma.fsac.pfeecom.order.domain.entity.PaymentMethod;
import ma.fsac.pfeecom.order.domain.entity.PaymentStatus;
import ma.fsac.pfeecom.order.dto.request.OrderRequest;
import ma.fsac.pfeecom.order.exception.ResourceNotFoundException;
import ma.fsac.pfeecom.order.repository.OrderRepository;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration de l'API /api/orders (MongoDB dédié, clients Customer/Product mockés).
 *
 * <p>Les tests de logique métier s'exécutent en tant qu'administrateur
 * ({@code @WithMockUser(roles = "ADMIN")}) afin d'ignorer le contrôle de propriété ;
 * la sécurité elle-même est couverte par {@code OrderSecurityTest}.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class OrderApiIntegrationTest {

    private static final long CUSTOMER = 7L;
    private static final long LAPTOP = 100L;
    private static final long MOUSE = 101L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
    private CustomerServiceClient customerServiceClient;

    @MockBean
    private ProductServiceClient productServiceClient;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        when(customerServiceClient.getCustomer(anyLong()))
                .thenReturn(new CustomerInfo(CUSTOMER, "Yassine", "Alami", "yassine.alami@example.com"));
        when(productServiceClient.getProduct(LAPTOP))
                .thenReturn(new ProductInfo(LAPTOP, "Ordinateur Portable", new BigDecimal("2549.99")));
        when(productServiceClient.getProduct(MOUSE))
                .thenReturn(new ProductInfo(MOUSE, "Souris Sans Fil", new BigDecimal("149.50")));
    }

    private String orderBody() throws Exception {
        return objectMapper.writeValueAsString(new OrderRequest(CUSTOMER, PaymentMethod.CASH_ON_DELIVERY, List.of(
                new OrderRequest.OrderItemRequest(LAPTOP, 1),
                new OrderRequest.OrderItemRequest(MOUSE, 2))));
    }

    @Test
    void createOrder_shouldReturn201WithSnapshotAndTotal() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(CUSTOMER))
                .andExpect(jsonPath("$.customerName").value("Yassine Alami"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentMethod").value("CASH_ON_DELIVERY"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productName").value("Ordinateur Portable"))
                .andExpect(jsonPath("$.items[1].subtotal").value(299.0))
                .andExpect(jsonPath("$.total").value(2848.99));
    }

    @Test
    void createOrderWithUnknownCustomer_shouldReturn404() throws Exception {
        when(customerServiceClient.getCustomer(anyLong()))
                .thenThrow(ResourceNotFoundException.of("Client", 999L));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderRequest(999L,
                                PaymentMethod.CASH_ON_DELIVERY, List.of(
                                new OrderRequest.OrderItemRequest(LAPTOP, 1))))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void createOrderWithUnknownProduct_shouldReturn404() throws Exception {
        when(productServiceClient.getProduct(999L))
                .thenThrow(ResourceNotFoundException.of("Produit", 999L));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderRequest(CUSTOMER,
                                PaymentMethod.CASH_ON_DELIVERY, List.of(
                                new OrderRequest.OrderItemRequest(999L, 1))))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getOrderById_shouldReturn200() throws Exception {
        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.total").value(2848.99));
    }

    @Test
    void getUnknownOrder_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/orders/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void findAll_shouldReturnPagedOrders() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/orders").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void createOrderWithEmptyItems_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderRequest(CUSTOMER,
                                PaymentMethod.CASH_ON_DELIVERY, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createOrderPersistsCashOnDeliveryAsPending() throws Exception {
        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentMethod").value("CASH_ON_DELIVERY"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        Order persisted = orderRepository.findById(id).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(persisted.getPaymentMethod())
                .isEqualTo(PaymentMethod.CASH_ON_DELIVERY);
        org.assertj.core.api.Assertions.assertThat(persisted.getPaymentStatus())
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void createOrder_shouldPersistServerSideCreatedAt() throws Exception {
        LocalDateTime before = LocalDateTime.now().minusMinutes(1);

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();
        LocalDateTime apiCreatedAt = LocalDateTime.parse(objectMapper.readTree(created).get("createdAt").asText());

        Order reloaded = orderRepository.findById(id).orElseThrow();
        LocalDateTime after = LocalDateTime.now().plusMinutes(1);

        org.assertj.core.api.Assertions.assertThat(reloaded.getCreatedAt())
                .isNotNull()
                .isBetween(before, after);
        org.assertj.core.api.Assertions.assertThat(apiCreatedAt).isBetween(before, after);
    }

    @Test
    void createOrderWithUnsupportedPaymentMethod_shouldReturn400() throws Exception {
        String body = "{\"customerId\":" + CUSTOMER + ",\"paymentMethod\":\"CREDIT_CARD\","
                + "\"items\":[{\"productId\":" + LAPTOP + ",\"quantity\":1}]}";

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrderWithMissingPaymentMethod_shouldReturn400() throws Exception {
        String body = "{\"customerId\":" + CUSTOMER + ","
                + "\"items\":[{\"productId\":" + LAPTOP + ",\"quantity\":1}]}";

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}