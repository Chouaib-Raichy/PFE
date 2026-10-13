package ma.fsac.pfeecom.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.fsac.pfeecom.order.client.CustomerInfo;
import ma.fsac.pfeecom.order.client.CustomerServiceClient;
import ma.fsac.pfeecom.order.client.ProductInfo;
import ma.fsac.pfeecom.order.client.ProductServiceClient;
import ma.fsac.pfeecom.order.domain.entity.PaymentMethod;
import ma.fsac.pfeecom.order.dto.request.OrderRequest;
import ma.fsac.pfeecom.order.repository.OrderRepository;
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
 * Tests de sécurité des commandes : authenticité, propriété (cross-customer) et
 * cloisonnement de la liste par client.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderSecurityTest {

    private static final long YASSINE = 1L;
    private static final long SARAH = 2L;
    private static final long LAPTOP = 100L;

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
                .thenAnswer(inv -> {
                    long id = inv.getArgument(0);
                    return new CustomerInfo(id, "Prenom" + id, "Nom", "client" + id + "@example.com");
                });
        when(productServiceClient.getProduct(LAPTOP))
                .thenReturn(new ProductInfo(LAPTOP, "Ordinateur Portable", new BigDecimal("2549.99")));
    }

    @Test
    void unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createForAnotherCustomer_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .with(jwt().jwt(j -> j.claim("customer_id", String.valueOf(SARAH))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyFor(YASSINE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void customerSeesOnlyOwnOrders() throws Exception {
        createAsAdmin(YASSINE);
        createAsAdmin(SARAH);

        mockMvc.perform(get("/api/orders").param("size", "10")
                        .with(jwt().jwt(j -> j.claim("customer_id", String.valueOf(YASSINE)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].customerId").value(YASSINE));
    }

    @Test
    void getOrderOfAnotherCustomer_shouldReturn403() throws Exception {
        long id = createAsAdmin(YASSINE);

        mockMvc.perform(get("/api/orders/" + id)
                        .with(jwt().jwt(j -> j.claim("customer_id", String.valueOf(SARAH)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void admin_seesAllOrders_andAnyOrder() throws Exception {
        createAsAdmin(YASSINE);
        createAsAdmin(SARAH);

        mockMvc.perform(get("/api/orders").param("size", "10")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    private long createAsAdmin(long customerId) throws Exception {
        String created = mockMvc.perform(post("/api/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyFor(customerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    private String bodyFor(long customerId) throws Exception {
        return objectMapper.writeValueAsString(new OrderRequest(customerId,
                PaymentMethod.CASH_ON_DELIVERY, List.of(
                new OrderRequest.OrderItemRequest(LAPTOP, 1))));
    }
}