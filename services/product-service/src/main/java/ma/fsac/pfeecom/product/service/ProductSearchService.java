package ma.fsac.pfeecom.product.service;

import ma.fsac.pfeecom.product.dto.response.ReindexResponse;
import ma.fsac.pfeecom.product.dto.response.SearchPageResponse;
import ma.fsac.pfeecom.product.dto.response.SuggestResponse;

/**
 * Recherche produits (Elasticsearch avec repli PostgreSQL).
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public interface ProductSearchService {

    SearchPageResponse search(String q, Long categoryId, int page, int size);

    SuggestResponse suggest(String q, int limit);

    ReindexResponse reindex();
}
