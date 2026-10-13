package ma.fsac.pfeecom.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.fsac.pfeecom.product.domain.entity.Product;
import ma.fsac.pfeecom.product.exception.InvalidImageException;
import ma.fsac.pfeecom.product.exception.StorageUnavailableException;
import ma.fsac.pfeecom.product.repository.CategoryRepository;
import ma.fsac.pfeecom.product.repository.ProductRepository;
import ma.fsac.pfeecom.product.repository.SearchOutboxRepository;
import ma.fsac.pfeecom.product.storage.StorageService;
import ma.fsac.pfeecom.product.storage.StoredImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration de la gestion des images produit (Phase 5).
 *
 * <p>Le {@link StorageService} est remplace par une doublure Mockito : ces tests
 * verifient la logique applicative (persistance de la reference, URL exposee,
 * remplacement supprimant l'ancien objet apres commit, repli 404/503/400) sans
 * dependre d'un MinIO reel. L'integration MinIO de bout en bout est verifiee
 * manuellement via la passerelle (voir docs/MINIO-S3-SETUP.md).</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductImageIntegrationTest {

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

    @MockBean
    private StorageService storageService;

    @BeforeEach
    void cleanDatabase() {
        searchOutboxRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    private long createProduct() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Telephone\",\"price\":199.00,\"stockQuantity\":4,\"active\":true}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), JsonNode.class).get("id").asLong();
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "photo.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0});
    }

    @Test
    void uploadPersistsReferenceAndExposesImageUrl() throws Exception {
        long id = createProduct();
        when(storageService.storeImage(eq(id), any())).thenReturn("products/" + id + "/a.png");

        mockMvc.perform(multipart("/api/products/{id}/image", id).file(image()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("/api/products/" + id + "/image"));

        Product product = productRepository.findById(id).orElseThrow();
        assertThat(product.getImageKey()).isEqualTo("products/" + id + "/a.png");
        assertThat(product.getImageUrl()).isEqualTo("/api/products/" + id + "/image");
    }

    @Test
    void replacingImageDeletesPreviousObjectAfterCommit() throws Exception {
        long id = createProduct();
        when(storageService.storeImage(eq(id), any()))
                .thenReturn("products/" + id + "/old.png")
                .thenReturn("products/" + id + "/new.png");

        mockMvc.perform(multipart("/api/products/{id}/image", id).file(image()))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/products/{id}/image", id).file(image()))
                .andExpect(status().isOk());

        assertThat(productRepository.findById(id).orElseThrow().getImageKey())
                .isEqualTo("products/" + id + "/new.png");
        org.mockito.Mockito.verify(storageService).deleteImage("products/" + id + "/old.png");
    }

    @Test
    void removeImageClearsReferenceAndDeletesObject() throws Exception {
        long id = createProduct();
        when(storageService.storeImage(eq(id), any())).thenReturn("products/" + id + "/a.png");
        mockMvc.perform(multipart("/api/products/{id}/image", id).file(image()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/products/{id}/image", id))
                .andExpect(status().isNoContent());

        Product product = productRepository.findById(id).orElseThrow();
        assertThat(product.getImageKey()).isNull();
        assertThat(product.getImageUrl()).isNull();
        org.mockito.Mockito.verify(storageService).deleteImage("products/" + id + "/a.png");
    }

    @Test
    void serveImageReturnsBytesWithContentType() throws Exception {
        long id = createProduct();
        byte[] data = new byte[]{1, 2, 3, 4};
        productRepository.save(Product.builder()
                .id(id).name("Telephone").price(new java.math.BigDecimal("199.00")).stockQuantity(4)
                .imageKey("products/" + id + "/a.png").imageUrl("/api/products/" + id + "/image").build());
        when(storageService.loadImage("products/" + id + "/a.png"))
                .thenReturn(new StoredImage(data, "image/png"));

        mockMvc.perform(get("/api/products/{id}/image", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", "public, max-age=86400"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(data));
    }

    @Test
    void serveImageWithoutImageReturns404() throws Exception {
        long id = createProduct();
        mockMvc.perform(get("/api/products/{id}/image", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void uploadToUnknownProductReturns404() throws Exception {
        mockMvc.perform(multipart("/api/products/{id}/image", 99999).file(image()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void uploadWhenStorageUnavailableReturns503() throws Exception {
        long id = createProduct();
        when(storageService.storeImage(eq(id), any()))
                .thenThrow(new StorageUnavailableException("indisponible"));

        mockMvc.perform(multipart("/api/products/{id}/image", id).file(image()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STORAGE_UNAVAILABLE"));
    }

    @Test
    void uploadInvalidImageReturns400() throws Exception {
        long id = createProduct();
        when(storageService.storeImage(eq(id), any()))
                .thenThrow(new InvalidImageException("format non supporte"));

        mockMvc.perform(multipart("/api/products/{id}/image", id).file(image()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
    }
}
