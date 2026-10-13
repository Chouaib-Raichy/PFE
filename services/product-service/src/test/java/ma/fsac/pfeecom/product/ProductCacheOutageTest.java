package ma.fsac.pfeecom.product;

import ma.fsac.pfeecom.product.repository.CategoryRepository;
import ma.fsac.pfeecom.product.repository.ProductRepository;
import ma.fsac.pfeecom.product.repository.SearchOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Strategie de panne Redis : le cache est pointe vers un port injoignable.
 * L'application doit rester fonctionnelle (repli transparent sur PostgreSQL) :
 * aucune requete metier ne doit echouer a cause du cache.
 */
@SpringBootTest(properties = {
        "spring.data.redis.port=1",
        "spring.data.redis.timeout=100ms",
        "spring.data.redis.connect-timeout=100ms"
})
@AutoConfigureMockMvc
class ProductCacheOutageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SearchOutboxRepository searchOutboxRepository;

    @BeforeEach
    void clean() {
        searchOutboxRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void listingSucceedsEvenWhenRedisIsDown() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "0").param("size", "5"))
                .andExpect(status().isOk());
    }

    @Test
    void writeAndDetailSucceedEvenWhenRedisIsDown() throws Exception {
        var created = mockMvc.perform(post("/api/products")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Outage\",\"price\":3.00,\"stockQuantity\":1,\"active\":true}"))
                .andExpect(status().isCreated())
                .andReturn();
        long id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/products/{id}", id)).andExpect(status().isOk());
    }
}
