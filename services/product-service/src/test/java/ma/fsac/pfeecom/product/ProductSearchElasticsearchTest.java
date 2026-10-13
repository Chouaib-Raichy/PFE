package ma.fsac.pfeecom.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.fsac.pfeecom.product.repository.CategoryRepository;
import ma.fsac.pfeecom.product.repository.ProductRepository;
import ma.fsac.pfeecom.product.repository.SearchOutboxRepository;
import ma.fsac.pfeecom.product.search.ProductSearchOutboxProcessor;
import ma.fsac.pfeecom.product.search.SearchIndexService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.HttpURLConnection;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration Elasticsearch (index de test dedie
 * {@code pfeecom-products-test}). Si Elasticsearch n'est pas joignable sur
 * http://localhost:9200, les tests sont marques IGNORES (jamais verts a tort) :
 * voir docs/ELASTICSEARCH-SETUP.md pour demarrer le conteneur.
 *
 * <p>Le chemin de synchronisation reel est exerce : chaque ecriture produit
 * alimente la file d'indexation (outbox), puis {@code drainOnce()} la traite.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@SpringBootTest(properties = {"app.search.enabled=true"})
@AutoConfigureMockMvc
class ProductSearchElasticsearchTest {

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

    @Autowired
    private SearchIndexService searchIndexService;

    @Autowired
    private ProductSearchOutboxProcessor outboxProcessor;

    @BeforeAll
    static void requireElasticsearch() {
        Assumptions.assumeTrue(elasticsearchReachable(),
                "Elasticsearch absent sur http://localhost:9200 : tests ES ignores "
                        + "(docker compose up -d elasticsearch).");
    }

    @BeforeEach
    void resetIndexAndDatabase() {
        searchOutboxRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        searchIndexService.reindexAll();
    }

    @Test
    void searchRanksByRelevanceAndHighlights() throws Exception {
        createAndIndex("Ordinateur portable", "Un PC portable puissant", null);
        createAndIndex("Souris portable", "Petite souris de voyage", null);

        mockMvc.perform(get("/api/products/search").param("q", "ordinateur"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Ordinateur portable"))
                .andExpect(jsonPath("$.content[0].highlights.name").exists());
    }

    @Test
    void suggestReturnsPrefixMatches() throws Exception {
        createAndIndex("Souris Gamer", "Haute precision", null);
        createAndIndex("Souris Sans Fil", "Bluetooth", null);
        createAndIndex("Clavier", "Mecanique", null);

        mockMvc.perform(get("/api/products/suggest").param("q", "sour"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.suggestions.length()").value(2))
                .andExpect(jsonPath("$.suggestions[0].name").value(org.hamcrest.Matchers.startsWith("Souris")));
    }

    @Test
    void searchPaginatesAndReportsTotals() throws Exception {
        createAndIndex("Produit Alpha", "un", null);
        createAndIndex("Produit Beta", "deux", null);
        createAndIndex("Produit Gamma", "trois", null);

        mockMvc.perform(get("/api/products/search").param("q", "produit").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void categoryFilterRestrictsResults() throws Exception {
        long categoryId = createCategory("Laptops");
        createAndIndex("Lenovo ThinkPad", "pro", categoryId);
        createAndIndex("Casque Audio", "son", null);

        mockMvc.perform(get("/api/products/search")
                        .param("q", "lenovo")
                        .param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Lenovo ThinkPad"));
    }

    @Test
    void deletingProductRemovesItFromIndex() throws Exception {
        long id = createAndIndex("Temporaire", "ephemere", null);

        mockMvc.perform(get("/api/products/search").param("q", "temporaire"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(delete("/api/products/{id}", id)).andExpect(status().isNoContent());
        outboxProcessor.drainOnce();

        mockMvc.perform(get("/api/products/search").param("q", "temporaire"))
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void reindexRebuildsWithoutDuplicates() throws Exception {
        createAndIndex("Ref A", "x", null);
        createAndIndex("Ref B", "y", null);

        searchIndexService.reindexAll();
        searchIndexService.reindexAll();

        assertThat(searchIndexService.documentCount()).isEqualTo(2L);
    }

    private long createCategory(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), JsonNode.class).get("id").asLong();
    }

    private long createAndIndex(String name, String description, Long categoryId) throws Exception {
        String category = categoryId == null ? "" : ",\"categoryId\":" + categoryId;
        String body = "{\"name\":\"" + name + "\",\"description\":\"" + description
                + "\",\"price\":10.00,\"stockQuantity\":1,\"active\":true" + category + "}";
        MvcResult result = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readValue(result.getResponse().getContentAsString(), JsonNode.class).get("id").asLong();
        outboxProcessor.drainOnce();
        return id;
    }

    private static boolean elasticsearchReachable() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create("http://localhost:9200").toURL().openConnection();
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(1500);
            connection.setRequestMethod("GET");
            return connection.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
