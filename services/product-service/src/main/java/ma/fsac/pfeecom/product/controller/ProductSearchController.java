package ma.fsac.pfeecom.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import ma.fsac.pfeecom.product.dto.response.ReindexResponse;
import ma.fsac.pfeecom.product.dto.response.SearchPageResponse;
import ma.fsac.pfeecom.product.dto.response.SuggestResponse;
import ma.fsac.pfeecom.product.service.ProductSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de recherche produits.
 *
 * <ul>
 *   <li>{@code GET /api/products/search} : recherche paginee (public) ;</li>
 *   <li>{@code GET /api/products/suggest} : suggestions (public) ;</li>
 *   <li>{@code POST /api/products/reindex} : reindexation complete (ADMIN).</li>
 * </ul>
 *
 * <p>Le filtrage des roles (POST reserve a ADMIN) est applique par l'API
 * Gateway, conformement a la politique existante sur {@code /api/products/**}.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Product Search", description = "Recherche plein-texte (Elasticsearch, repli PostgreSQL)")
public class ProductSearchController {

    private final ProductSearchService productSearchService;

    @GetMapping("/search")
    @Operation(summary = "Rechercher des produits",
            description = """
                    Recherche plein-texte dans Elasticsearch (champs nom, nom arabe, categorie, description)
                    avec tolerance aux fautes. Lecture publique. Si Elasticsearch est indisponible, la
                    recherche bascule automatiquement sur PostgreSQL et la reponse porte `degraded=true`.
                    Pagination : `page` (0-based) et `size` (1..50).
                    """)
    @ApiResponse(responseCode = "200", description = "Page de resultats (champ `degraded` = repli PostgreSQL)")
    public SearchPageResponse search(
            @Parameter(description = "Termes de recherche (max 100 caracteres)") @RequestParam(required = false) String q,
            @Parameter(description = "Filtre par identifiant de categorie") @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return productSearchService.search(q, categoryId, page, size);
    }

    @GetMapping("/suggest")
    @Operation(summary = "Suggestions d'autocompletion", description = "Lecture publique. Renvoie au plus `limit` suggestions (jamais d'erreur : liste vide si Elasticsearch est indisponible, `degraded=true`).")
    @ApiResponse(responseCode = "200", description = "Suggestions (champ `degraded`)")
    public SuggestResponse suggest(
            @Parameter(description = "Prefixe/termes saisis") @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "6") int limit) {
        return productSearchService.suggest(q, limit);
    }

    @PostMapping("/reindex")
    @Operation(summary = "Reindexer le catalogue (ADMIN)", description = """
            Reconstruit l'index Elasticsearch depuis PostgreSQL. Reserve au role ADMIN.
            Renvoie `503` si Elasticsearch est momentanement indisponible.
            """)
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reindexation effectuee (`indexed` = nombre de produits)"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Role insuffisant (ADMIN requis)"),
            @ApiResponse(responseCode = "503", description = "Elasticsearch indisponible - `SEARCH_UNAVAILABLE`",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"timestamp\":\"2026-01-01T10:00:00Z\",\"status\":503,"
                                    + "\"code\":\"SEARCH_UNAVAILABLE\",\"message\":\"La recherche est momentanement indisponible.\","
                                    + "\"details\":null}")))
    })
    public ReindexResponse reindex() {
        return productSearchService.reindex();
    }
}
