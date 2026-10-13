package ma.fsac.pfeecom.product.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.extern.slf4j.Slf4j;
import ma.fsac.pfeecom.product.domain.entity.Product;
import ma.fsac.pfeecom.product.dto.response.ProductSearchHit;
import ma.fsac.pfeecom.product.dto.response.ReindexResponse;
import ma.fsac.pfeecom.product.dto.response.SearchPageResponse;
import ma.fsac.pfeecom.product.dto.response.SuggestItem;
import ma.fsac.pfeecom.product.dto.response.SuggestResponse;
import ma.fsac.pfeecom.product.repository.ProductRepository;
import ma.fsac.pfeecom.product.search.ProductSearchDocument;
import ma.fsac.pfeecom.product.search.SearchIndexService;
import ma.fsac.pfeecom.product.service.ProductSearchService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recherche produits : requetes Elasticsearch (pertinence, filtres, surlignage)
 * avec repli automatique sur PostgreSQL si Elasticsearch est indisponible. Le
 * repli ne casse jamais le storefront : au pire la recherche renvoie une page
 * vide signalee {@code degraded}.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Slf4j
@Service
public class ProductSearchServiceImpl implements ProductSearchService {

    private static final String MARK_OPEN = "<em>";
    private static final String MARK_CLOSE = "</em>";
    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_SUGGESTIONS = 10;

    private final ObjectProvider<ElasticsearchClient> clientProvider;
    private final SearchIndexService indexService;
    private final ProductRepository productRepository;

    public ProductSearchServiceImpl(ObjectProvider<ElasticsearchClient> clientProvider,
                                    SearchIndexService indexService,
                                    ProductRepository productRepository) {
        this.clientProvider = clientProvider;
        this.indexService = indexService;
        this.productRepository = productRepository;
    }

    @Override
    public SearchPageResponse search(String q, Long categoryId, int page, int size) {
        String query = normalize(q);
        int safePage = Math.max(0, page);
        int safeSize = clampSize(size);

        if (indexService.isEnabled() && indexService.isAvailable()) {
            try {
                return elasticSearch(query, categoryId, safePage, safeSize);
            } catch (IOException | RuntimeException e) {
                log.warn("Recherche Elasticsearch en echec, repli PostgreSQL : {}", e.getMessage());
            }
        }
        return fallbackSearch(query, categoryId, safePage, safeSize);
    }

    @Override
    public SuggestResponse suggest(String q, int limit) {
        String query = normalize(q);
        int safeLimit = Math.min(Math.max(limit, 1), MAX_SUGGESTIONS);
        if (query.isEmpty()) {
            return new SuggestResponse(List.of(), false);
        }
        if (indexService.isEnabled() && indexService.isAvailable()) {
            try {
                return elasticSuggest(query, safeLimit);
            } catch (IOException | RuntimeException e) {
                log.warn("Suggestions Elasticsearch en echec, repli PostgreSQL : {}", e.getMessage());
            }
        }
        List<SuggestItem> items = productRepository
                .suggestFallback(query, PageRequest.of(0, safeLimit)).stream()
                .map(ProductSearchServiceImpl::toSuggestItem)
                .toList();
        return new SuggestResponse(items, true);
    }

    @Override
    public ReindexResponse reindex() {
        return new ReindexResponse(indexService.reindexAll());
    }

    private SearchPageResponse elasticSearch(String query, Long categoryId, int page, int size)
            throws IOException {
        ElasticsearchClient client = requireClient();
        SearchResponse<ProductSearchDocument> response = client.search(s -> {
            s.index(indexService.indexName())
                    .from(page * size)
                    .size(size)
                    .query(qb -> qb.bool(b -> {
                        if (query.isEmpty()) {
                            b.must(m -> m.matchAll(ma -> ma));
                        } else {
                            b.must(m -> m.multiMatch(mm -> mm
                                    .query(query)
                                    .fields("name^3", "name.ar^2", "categoryName^2", "description")
                                    .type(TextQueryType.BestFields)
                                    .fuzziness("AUTO")));
                        }
                        b.filter(f -> f.term(t -> t.field("active").value(true)));
                        if (categoryId != null) {
                            b.filter(f -> f.term(t -> t.field("categoryId").value(categoryId)));
                        }
                        return b;
                    }));
            if (!query.isEmpty()) {
                s.highlight(h -> h
                        .preTags(MARK_OPEN)
                        .postTags(MARK_CLOSE)
                        .fields("name", f -> f)
                        .fields("description", f -> f));
                s.sort(so -> so.score(sc -> sc.order(SortOrder.Desc)));
            }
            s.sort(so -> so.field(f -> f.field("id").order(SortOrder.Asc)));
            return s;
        }, ProductSearchDocument.class);

        List<ProductSearchHit> content = response.hits().hits().stream()
                .map(ProductSearchServiceImpl::toHit)
                .toList();
        long total = response.hits().total() != null
                ? response.hits().total().value()
                : content.size();
        return new SearchPageResponse(content, total, pageCount(total, size), page, size, false);
    }

    private SuggestResponse elasticSuggest(String query, int limit) throws IOException {
        ElasticsearchClient client = requireClient();
        SearchResponse<ProductSearchDocument> response = client.search(s -> s
                .index(indexService.indexName())
                .size(limit)
                .query(qb -> qb.bool(b -> b
                        .must(m -> m.multiMatch(mm -> mm
                                .query(query)
                                .fields("name^3", "categoryName")
                                .type(TextQueryType.BoolPrefix)))
                        .filter(f -> f.term(t -> t.field("active").value(true)))))
                .sort(so -> so.score(sc -> sc.order(SortOrder.Desc))),
                ProductSearchDocument.class);

        List<SuggestItem> items = response.hits().hits().stream()
                .map(Hit::source)
                .filter(java.util.Objects::nonNull)
                .map(doc -> new SuggestItem(doc.id(), doc.name(), doc.categoryName(), doc.imageUrl()))
                .toList();
        return new SuggestResponse(items, false);
    }

    private SearchPageResponse fallbackSearch(String query, Long categoryId, int page, int size) {
        Page<Product> result = productRepository.searchFallback(
                query, categoryId, PageRequest.of(page, size, Sort.by("name").ascending()));
        List<ProductSearchHit> content = new ArrayList<>();
        for (Product product : result.getContent()) {
            content.add(toHit(product));
        }
        return new SearchPageResponse(content, result.getTotalElements(), result.getTotalPages(),
                page, size, true);
    }

    private ElasticsearchClient requireClient() {
        ElasticsearchClient client = clientProvider.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("Client Elasticsearch indisponible");
        }
        return client;
    }

    private static ProductSearchHit toHit(Product product) {
        return new ProductSearchHit(product.getId(), product.getName(), product.getDescription(),
                product.getPrice(), product.getStockQuantity(), product.getImageUrl(),
                product.getActive(), product.getCategoryId(), product.getCategoryName(),
                product.getCreatedAt(), product.getUpdatedAt(), null);
    }

    private static ProductSearchHit toHit(Hit<ProductSearchDocument> hit) {
        ProductSearchDocument doc = hit.source();
        if (doc == null) {
            return null;
        }
        return new ProductSearchHit(doc.id(), doc.name(), doc.description(), doc.price(),
                doc.stockQuantity(), doc.imageUrl(), doc.active(), doc.categoryId(),
                doc.categoryName(), toLocalDateTime(doc.createdAt()), toLocalDateTime(doc.updatedAt()),
                toHighlights(hit.highlight()));
    }

    private static Map<String, String> toHighlights(Map<String, List<String>> highlight) {
        if (highlight == null || highlight.isEmpty()) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        highlight.forEach((field, fragments) -> {
            if (fragments != null && !fragments.isEmpty()) {
                result.put(field, fragments.get(0));
            }
        });
        return result.isEmpty() ? null : result;
    }

    private static SuggestItem toSuggestItem(Product product) {
        return new SuggestItem(product.getId(), product.getName(), product.getCategoryName(),
                product.getImageUrl());
    }

    private static LocalDateTime toLocalDateTime(Long epochMillis) {
        return epochMillis == null ? null
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private static int pageCount(long total, int size) {
        return size <= 0 ? 0 : (int) Math.ceil((double) total / size);
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private static String normalize(String q) {
        if (q == null) {
            return "";
        }
        String trimmed = q.trim();
        return trimmed.length() > MAX_QUERY_LENGTH ? trimmed.substring(0, MAX_QUERY_LENGTH) : trimmed;
    }
}
