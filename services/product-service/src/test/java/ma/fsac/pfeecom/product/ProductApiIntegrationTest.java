package ma.fsac.pfeecom.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import ma.fsac.pfeecom.product.domain.entity.Product;
import ma.fsac.pfeecom.product.repository.CategoryRepository;
import ma.fsac.pfeecom.product.repository.ProductRepository;
import ma.fsac.pfeecom.product.repository.SearchOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration de l'API Product/Category (MockMvc + PostgreSQL dédié au test).
 *
 * <p>Les tests s'exécutent contre la base <code>pfe_products_test</code> du
 * conteneur <code>postgres-test</code> (docker-compose.ecom.yml), toujours vidée
 * avant chaque test : les données de démonstration <code>pfe_*</code> ne sont
 * jamais touchées.</p>
 *
 * <p>Couvre : CRUD produit, association catégorie, catégorie invalide,
 * JSON malformé, validation des DTOs.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SearchOutboxRepository searchOutboxRepository;

    @BeforeEach
    void cleanDatabase() {
        searchOutboxRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    private long postAndParseId(String url, String body) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), JsonNode.class).get("id").asLong();
    }

    private long createCategory(String name) throws Exception {
        return postAndParseId("/api/categories", "{\"name\":\"" + name + "\",\"description\":\"desc\"}");
    }

    @Test
    void createThenGetProduct() throws Exception {
        long id = postAndParseId("/api/products",
                "{\"name\":\"Laptop\",\"description\":\"Ultrabook\",\"price\":1299.00,\"stockQuantity\":5,\"active\":true}");

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Laptop"))
                .andExpect(jsonPath("$.price").value(1299.00))
                .andExpect(jsonPath("$.stockQuantity").value(5));
    }

    @Test
    void getUnknownProductReturns404() throws Exception {
        mockMvc.perform(get("/api/products/{id}", 99999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void updateProduct() throws Exception {
        long id = postAndParseId("/api/products", "{\"name\":\"Old\",\"price\":10.00,\"stockQuantity\":1,\"active\":true}");

        mockMvc.perform(put("/api/products/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\",\"price\":20.50,\"stockQuantity\":9,\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"))
                .andExpect(jsonPath("$.price").value(20.50))
                .andExpect(jsonPath("$.stockQuantity").value(9));
    }

    @Test
    void deleteProductThenGetReturns404() throws Exception {
        long id = postAndParseId("/api/products", "{\"name\":\"Temp\",\"price\":5.00,\"stockQuantity\":1,\"active\":true}");

        mockMvc.perform(delete("/api/products/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void listProductsPaged() throws Exception {
        postAndParseId("/api/products", "{\"name\":\"Item\",\"price\":1.00,\"stockQuantity\":1,\"active\":true}");

        mockMvc.perform(get("/api/products").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void createProductWithCategoryLinksCategory() throws Exception {
        long categoryId = createCategory("Laptops");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"MacBook\",\"price\":1199.00,\"stockQuantity\":3,\"active\":true,\"categoryId\":" + categoryId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.categoryName").value("Laptops"));
    }

    @Test
    void createProductWithUnknownCategoryReturns404() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ghost\",\"price\":9.99,\"stockQuantity\":1,\"categoryId\":99999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void validationErrorsReturn400() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"price\":-5,\"stockQuantity\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Broken\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    @Test
    void createAndListCategory() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(java.util.Map.of("name", "Phones", "description", "Mobiles"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Phones"));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Phones"));
    }

    @Test
    void updateCategory() throws Exception {
        long id = createCategory("Old");

        mockMvc.perform(put("/api/categories/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"description\":\"new desc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.description").value("new desc"));
    }

    @Test
    void updateUnknownCategoryReturns404() throws Exception {
        mockMvc.perform(put("/api/categories/{id}", 99999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nope\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void deleteUnusedCategoryReturns204AndListIsEmpty() throws Exception {
        long id = createCategory("Disposable");

        mockMvc.perform(delete("/api/categories/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deleteCategoryWithProductsReturns409Conflict() throws Exception {
        long categoryId = createCategory("Used");
        postAndParseId("/api/products",
                "{\"name\":\"Linked\",\"price\":10.00,\"stockQuantity\":1,\"active\":true,\"categoryId\":" + categoryId + "}");

        mockMvc.perform(delete("/api/categories/{id}", categoryId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void createCategoryWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"description\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void productWithUnknownCategory_isRejectedByForeignKey() {
        assertThatThrownBy(() -> productRepository.saveAndFlush(Product.builder()
                .name("Fantôme").price(new BigDecimal("9.99")).stockQuantity(1)
                .categoryId(99999L).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void negativePrice_isRejectedByCheckConstraint() {
        assertThatThrownBy(() -> productRepository.saveAndFlush(Product.builder()
                .name("Prix negatif").price(new BigDecimal("-1.00")).stockQuantity(1).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- Recherche : repli PostgreSQL (Elasticsearch desactive en test) ---

    @Test
    void searchWithoutElasticsearchFallsBackToDatabaseAndExcludesInactive() throws Exception {
        postAndParseId("/api/products",
                "{\"name\":\"Ordinateur portable\",\"price\":1000.00,\"stockQuantity\":3,\"active\":true}");
        postAndParseId("/api/products",
                "{\"name\":\"Portable casse\",\"price\":10.00,\"stockQuantity\":1,\"active\":false}");

        mockMvc.perform(get("/api/products/search").param("q", "portable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Ordinateur portable"));
    }

    @Test
    void searchFallbackFiltersByCategory() throws Exception {
        long laptops = createCategory("Laptops");
        postAndParseId("/api/products",
                "{\"name\":\"Lenovo\",\"price\":800.00,\"stockQuantity\":1,\"active\":true,\"categoryId\":" + laptops + "}");
        postAndParseId("/api/products",
                "{\"name\":\"Casque\",\"price\":50.00,\"stockQuantity\":1,\"active\":true}");

        mockMvc.perform(get("/api/products/search").param("categoryId", String.valueOf(laptops)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Lenovo"));
    }

    @Test
    void searchSizeIsClampedToMaximum() throws Exception {
        mockMvc.perform(get("/api/products/search").param("size", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void suggestWithoutElasticsearchFallsBackToDatabase() throws Exception {
        postAndParseId("/api/products",
                "{\"name\":\"Souris gamer\",\"price\":40.00,\"stockQuantity\":1,\"active\":true}");

        mockMvc.perform(get("/api/products/suggest").param("q", "sour"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.suggestions[0].name").value("Souris gamer"));
    }

    @Test
    void searchOutboxEnqueueIsIdempotent() {
        searchOutboxRepository.enqueue(42L);
        searchOutboxRepository.enqueue(42L);

        assertThat(searchOutboxRepository.findAll())
                .extracting(ma.fsac.pfeecom.product.domain.entity.SearchOutboxEntry::getProductId)
                .containsExactly(42L);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}