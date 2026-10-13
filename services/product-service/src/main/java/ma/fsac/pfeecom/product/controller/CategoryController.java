package ma.fsac.pfeecom.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import ma.fsac.pfeecom.product.domain.entity.Category;
import ma.fsac.pfeecom.product.dto.response.CategoryResponse;
import ma.fsac.pfeecom.product.exception.ConflictException;
import ma.fsac.pfeecom.product.repository.CategoryRepository;
import ma.fsac.pfeecom.product.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints REST de gestion des catégories.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Categories produits (lecture publique, ecriture ADMIN)")
public class CategoryController {

    private static final String NOT_FOUND_EXAMPLE =
            "{\"timestamp\":\"2026-01-01T10:00:00Z\",\"status\":404,\"code\":\"NOT_FOUND\","
                    + "\"message\":\"Categorie introuvable: 42\",\"details\":null}";

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @GetMapping
    @Operation(summary = "Lister les categories", description = "Lecture publique.")
    @ApiResponse(responseCode = "200", description = "Liste des categories")
    @Cacheable(value = "categories", key = "'all'", unless = "#result == null || #result.isEmpty()")
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream().map(CategoryController::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter une categorie", description = "Lecture publique.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categorie trouvee"),
            @ApiResponse(responseCode = "404", description = "Categorie introuvable",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    public CategoryResponse findById(@PathVariable Long id) {
        return categoryRepository.findById(id)
                .map(CategoryController::toResponse)
                .orElseThrow(() -> ma.fsac.pfeecom.product.exception.ResourceNotFoundException.of("Catégorie", id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CacheEvict(value = {"categories", "products", "product"}, allEntries = true)
    @Operation(summary = "Creer une categorie", description = "Reserve au role ADMIN.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categorie creee"),
            @ApiResponse(responseCode = "400", description = "Donnees invalides"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Role insuffisant (ADMIN requis)")
    })
    public CategoryResponse create(@Valid @RequestBody CreateCategoryRequest request) {
        Category category = Category.builder()
                .name(request.name())
                .description(request.description())
                .build();
        return toResponse(categoryRepository.save(category));
    }

    @PutMapping("/{id}")
    @CacheEvict(value = {"categories", "products", "product"}, allEntries = true)
    @Operation(summary = "Modifier une categorie", description = "Reserve au role ADMIN.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categorie modifiee"),
            @ApiResponse(responseCode = "400", description = "Donnees invalides"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Role insuffisant (ADMIN requis)"),
            @ApiResponse(responseCode = "404", description = "Categorie introuvable",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CreateCategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> ma.fsac.pfeecom.product.exception.ResourceNotFoundException.of("Catégorie", id));
        category.setName(request.name());
        category.setDescription(request.description());
        return toResponse(categoryRepository.save(category));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @CacheEvict(value = {"categories", "products", "product"}, allEntries = true)
    @Operation(summary = "Supprimer une categorie", description = "Reserve au role ADMIN. Refusee (409) si des produits y sont encore associes.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Categorie supprimee"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Role insuffisant (ADMIN requis)"),
            @ApiResponse(responseCode = "404", description = "Categorie introuvable",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE))),
            @ApiResponse(responseCode = "409", description = "Conflit : produits encore associes - `CONFLICT`",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"timestamp\":\"2026-01-01T10:00:00Z\",\"status\":409,\"code\":\"CONFLICT\","
                                    + "\"message\":\"Suppression impossible : des produits sont encore associes a cette categorie.\","
                                    + "\"details\":null}")))
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!categoryRepository.existsById(id)) {
            throw ma.fsac.pfeecom.product.exception.ResourceNotFoundException.of("Catégorie", id);
        }
        if (productRepository.existsByCategoryId(id)) {
            throw new ConflictException(
                    "Suppression impossible : des produits sont encore associés à cette catégorie.");
        }
        categoryRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    record CreateCategoryRequest(
            @NotBlank(message = "Le nom de la catégorie est obligatoire")
            @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
            String name,
            @Size(max = 500, message = "La description ne doit pas dépasser 500 caractères")
            String description
    ) {}

    private static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(), category.getCreatedAt());
    }
}