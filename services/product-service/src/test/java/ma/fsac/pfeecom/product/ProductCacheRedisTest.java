package ma.fsac.pfeecom.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.fsac.pfeecom.product.repository.CategoryRepository;
import ma.fsac.pfeecom.product.repository.ProductRepository;
import ma.fsac.pfeecom.product.repository.SearchOutboxRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration du cache Redis (instance dediee, locale:6380).
 *
 * <p>Si Redis n'est pas joignable, les tests sont IGNORES (jamais verts a tort).
 * Demarrer l'instance : {@code docker compose --profile test up -d redis-test}.</p>
 *
 * <p>Exerce le vrai cache-aside : miss puis remplissage, reutilisation (hit),
 * TTL configure, et invalidation apres creation / mise a jour.</p>
 */
@SpringBootTest(properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6380",
        "spring.data.redis.timeout=500ms",
        "spring.data.redis.connect-timeout=500ms"
})
@AutoConfigureMockMvc
class ProductCacheRedisTest {

    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6380;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SearchOutboxRepository searchOutboxRepository;

    @BeforeAll
    static void requireRedis() {
        Assumptions.assumeTrue(reachable(),
                "Redis absent sur localhost:6380 : tests de cache ignores "
                        + "(docker compose --profile test up -d redis-test).");
    }

    @BeforeEach
    void clean() {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        searchOutboxRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void listingIsCachedOnMissThenServedFromCacheOnHit() throws Exception {
        seedProduct("Laptop");
        assertThat(redis.hasKey("products::0-5")).isFalse();

        mockMvc.perform(get("/api/products").param("page", "0").param("size", "5"))
                .andExpect(status().isOk());
        assertThat(redis.hasKey("products::0-5")).isTrue();

        mockMvc.perform(get("/api/products").param("page", "0").param("size", "5"))
                .andExpect(status().isOk());
        assertThat(redis.hasKey("products::0-5")).isTrue();
    }

    @Test
    void listingCacheHasPositiveTtlWithinConfiguredBound() throws Exception {
        seedProduct("Laptop");
        mockMvc.perform(get("/api/products").param("page", "0").param("size", "5"))
                .andExpect(status().isOk());
        Long ttl = redis.getExpire("products::0-5");
        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(0L).isLessThanOrEqualTo(300L);
    }

    @Test
    void creatingProductInvalidatesListingCache() throws Exception {
        seedProduct("Laptop");
        mockMvc.perform(get("/api/products").param("page", "0").param("size", "5"))
                .andExpect(status().isOk());
        assertThat(redis.hasKey("products::0-5")).isTrue();

        seedProduct("Cache Buster");

        assertThat(redis.hasKey("products::0-5")).isFalse();
    }

    @Test
    void detailIsCachedAndInvalidatedOnUpdate() throws Exception {
        long id = seedProduct("Detail");

        mockMvc.perform(get("/api/products/{id}", id)).andExpect(status().isOk());
        assertThat(redis.hasKey("product::" + id)).isTrue();

        mockMvc.perform(put("/api/products/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Detail2\",\"price\":12.00,\"stockQuantity\":2,\"active\":true}"))
                .andExpect(status().isOk());

        assertThat(redis.hasKey("product::" + id)).isFalse();
    }

    @Test
    void categoryListingIsCachedAndInvalidatedOnCreate() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Informatique\",\"description\":\"desc\"}"))
                .andExpect(status().isCreated());
        // la creation evince le cache : on repart donc d'un etat non cache
        assertThat(redis.hasKey("categories::all")).isFalse();

        mockMvc.perform(get("/api/categories")).andExpect(status().isOk());
        assertThat(redis.hasKey("categories::all")).isTrue();

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Audio\",\"description\":\"desc\"}"))
                .andExpect(status().isCreated());
        assertThat(redis.hasKey("categories::all")).isFalse();
    }

    private long seedProduct(String name) throws Exception {
        var created = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"price\":11.00,\"stockQuantity\":2,\"active\":true}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(created.getResponse().getContentAsString(), JsonNode.class).get("id").asLong();
    }

    private static boolean reachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(REDIS_HOST, REDIS_PORT), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
