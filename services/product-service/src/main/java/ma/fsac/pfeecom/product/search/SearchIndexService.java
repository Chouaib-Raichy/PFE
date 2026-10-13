package ma.fsac.pfeecom.product.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import lombok.extern.slf4j.Slf4j;
import ma.fsac.pfeecom.product.domain.entity.Product;
import ma.fsac.pfeecom.product.exception.SearchUnavailableException;
import ma.fsac.pfeecom.product.repository.ProductRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Gestion de l'index Elasticsearch : creation du mapping (versionne dans
 * {@code resources/search/product-index.json}), indexation unitaire idempotente,
 * desindexation et reindexation complete depuis PostgreSQL (source de verite).
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Slf4j
@Service
public class SearchIndexService {

    private static final String MAPPING_RESOURCE = "search/product-index.json";

    private final ObjectProvider<ElasticsearchClient> clientProvider;
    private final ProductRepository productRepository;
    private final boolean enabled;
    private final String indexName;

    public SearchIndexService(ObjectProvider<ElasticsearchClient> clientProvider,
                              ProductRepository productRepository,
                              @Value("${app.search.enabled:false}") boolean enabled,
                              @Value("${app.search.index-name:pfeecom-products}") String indexName) {
        this.clientProvider = clientProvider;
        this.productRepository = productRepository;
        this.enabled = enabled;
        this.indexName = indexName;
    }

    public String indexName() {
        return indexName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Verifie que le client est present et que le cluster repond (HEAD /). */
    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }
        ElasticsearchClient client = clientProvider.getIfAvailable();
        if (client == null) {
            return false;
        }
        try {
            return client.ping().value();
        } catch (IOException | RuntimeException e) {
            log.debug("Elasticsearch injoignable : {}", e.getMessage());
            return false;
        }
    }

    /** Cree l'index avec son mapping s'il n'existe pas deja (idempotent). */
    public void ensureIndex() {
        ElasticsearchClient client = requireClient();
        try {
            boolean exists = client.indices().exists(e -> e.index(indexName)).value();
            if (exists) {
                return;
            }
            try (InputStream in = new ClassPathResource(MAPPING_RESOURCE).getInputStream()) {
                client.indices().create(c -> c.index(indexName).withJson(in));
                log.info("Index Elasticsearch '{}' cree.", indexName);
            }
        } catch (IOException e) {
            throw new SearchUnavailableException("Creation de l'index Elasticsearch impossible", e);
        }
    }

    /** Indexe (ou remplace) le document d'un produit. Idempotent. */
    public void indexProduct(Product product) {
        ElasticsearchClient client = requireClient();
        try {
            client.index(i -> i.index(indexName)
                    .id(String.valueOf(product.getId()))
                    .document(toDocument(product)));
        } catch (IOException | RuntimeException e) {
            throw new SearchUnavailableException(
                    "Indexation du produit " + product.getId() + " impossible", e);
        }
    }

    /** Desindexe un produit. Un document absent est traite comme un succes. */
    public void deleteProduct(Long productId) {
        ElasticsearchClient client = requireClient();
        try {
            client.delete(d -> d.index(indexName).id(String.valueOf(productId)));
        } catch (ElasticsearchException e) {
            if (e.response() != null && e.response().status() == 404) {
                return;
            }
            throw new SearchUnavailableException(
                    "Desindexation du produit " + productId + " impossible", e);
        } catch (IOException e) {
            throw new SearchUnavailableException(
                    "Desindexation du produit " + productId + " impossible", e);
        }
    }

    /**
     * Reconciliation complete : reconstruit l'index a partir de PostgreSQL.
     * L'index est supprime puis recree avec son mapping, ce qui elimine aussi
     * les documents orphelins (produits supprimes). A n'appeler que sur action
     * admin ou au premier demarrage (jamais a chaque ecriture).
     */
    public long reindexAll() {
        ElasticsearchClient client = requireClient();
        try {
            try {
                client.indices().delete(d -> d.index(indexName));
            } catch (ElasticsearchException ignored) {
                // Index absent : rien a supprimer.
            }
            ensureIndex();
            long count = 0;
            for (Product product : productRepository.findAll()) {
                client.index(i -> i.index(indexName)
                        .id(String.valueOf(product.getId()))
                        .document(toDocument(product)));
                count++;
            }
            refresh();
            log.info("Reindexation complete : {} produit(s) indexe(s) dans '{}'.", count, indexName);
            return count;
        } catch (IOException | RuntimeException e) {
            throw new SearchUnavailableException("Reindexation Elasticsearch impossible", e);
        }
    }

    /** Nombre de documents actuellement indexes (0 si l'index est absent). */
    public long documentCount() {
        ElasticsearchClient client = requireClient();
        try {
            return client.count(c -> c.index(indexName)).count();
        } catch (IOException | RuntimeException e) {
            log.debug("Comptage des documents de '{}' impossible : {}", indexName, e.getMessage());
            return 0L;
        }
    }

    /** Rend les ecritures visibles immediatement (utilise apres un lot). */
    public void refresh() {
        ElasticsearchClient client = clientProvider.getIfAvailable();
        if (client == null) {
            return;
        }
        try {
            client.indices().refresh(r -> r.index(indexName));
        } catch (IOException | RuntimeException e) {
            log.debug("Rafraichissement de l'index '{}' ignore : {}", indexName, e.getMessage());
        }
    }

    private ElasticsearchClient requireClient() {
        ElasticsearchClient client = clientProvider.getIfAvailable();
        if (client == null) {
            throw new SearchUnavailableException("Client Elasticsearch indisponible");
        }
        return client;
    }

    private ProductSearchDocument toDocument(Product product) {
        return new ProductSearchDocument(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getImageUrl(),
                product.getActive(),
                product.getCategoryId(),
                product.getCategoryName(),
                toEpochMillis(product.getCreatedAt()),
                toEpochMillis(product.getUpdatedAt()));
    }

    private static Long toEpochMillis(LocalDateTime dateTime) {
        return dateTime == null ? null
                : dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
