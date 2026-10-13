package ma.fsac.pfeecom.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.fsac.pfeecom.product.dto.request.ProductRequest;
import ma.fsac.pfeecom.product.dto.response.ProductResponse;
import ma.fsac.pfeecom.product.service.ProductService;
import ma.fsac.pfeecom.product.storage.StoredImage;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoints REST de gestion des produits.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Catalogue produits (lecture publique, ecriture ADMIN)")
public class ProductController {

    private static final String NOT_FOUND_EXAMPLE =
            "{\"timestamp\":\"2026-01-01T10:00:00Z\",\"status\":404,\"code\":\"NOT_FOUND\","
                    + "\"message\":\"Produit introuvable: 42\",\"details\":null}";
    private static final String VALIDATION_EXAMPLE =
            "{\"timestamp\":\"2026-01-01T10:00:00Z\",\"status\":400,\"code\":\"VALIDATION_FAILED\","
                    + "\"message\":\"Erreur de validation des donnees\","
                    + "\"details\":{\"price\":\"Le prix est obligatoire\"}}";
    private static final String PRODUCT_EXAMPLE =
            "{\"name\":\"Casque Bluetooth\",\"description\":\"Casque sans fil\",\"price\":129.99,"
                    + "\"stockQuantity\":25,\"active\":true,\"categoryId\":1}";

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Lister les produits (pagine)", description = "Lecture publique. Pagination : parametres `page` (0-based) et `size`.")
    @ApiResponse(responseCode = "200", description = "Page de produits")
    public Page<ProductResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return productService.findAll(page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter un produit par identifiant", description = "Lecture publique.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Produit trouve"),
            @ApiResponse(responseCode = "404", description = "Produit introuvable",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    public ProductResponse findById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creer un produit", description = "Reserve au role ADMIN (applique par le gateway).")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Produit cree"),
            @ApiResponse(responseCode = "400", description = "Donnees invalides",
                    content = @Content(examples = @ExampleObject(value = VALIDATION_EXAMPLE))),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Role insuffisant (ADMIN requis)")
    })
    public ProductResponse create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(name = "Exemple", value = PRODUCT_EXAMPLE)))
            @Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un produit", description = "Reserve au role ADMIN (applique par le gateway).")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Produit modifie"),
            @ApiResponse(responseCode = "400", description = "Donnees invalides",
                    content = @Content(examples = @ExampleObject(value = VALIDATION_EXAMPLE))),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Role insuffisant (ADMIN requis)"),
            @ApiResponse(responseCode = "404", description = "Produit introuvable",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un produit", description = "Reserve au role ADMIN (applique par le gateway).")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Produit supprime"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Role insuffisant (ADMIN requis)"),
            @ApiResponse(responseCode = "404", description = "Produit introuvable",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Televerse (ou remplace) l'image d'un produit. Reserve aux administrateurs cote passerelle. */
    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Televerser/remplacer l'image d'un produit",
            description = """
                    Reserve au role ADMIN. Corps multipart/form-data, champ `file` (JPEG, PNG, WEBP ou GIF).
                    Taille maximale 5 Mo ; le type est verifie par signature binaire. L'image precedente
                    n'est supprimee qu'apres la sauvegarde reussie (aucune perte en cas d'echec).
                    """)
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image enregistree ; produit retourne avec `imageUrl`"),
            @ApiResponse(responseCode = "400", description = "Image invalide (format/type/vide) - `INVALID_IMAGE`"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Role insuffisant (ADMIN requis)"),
            @ApiResponse(responseCode = "404", description = "Produit introuvable"),
            @ApiResponse(responseCode = "413", description = "Fichier trop volumineux - `FILE_TOO_LARGE`"),
            @ApiResponse(responseCode = "503", description = "Stockage indisponible - `STORAGE_UNAVAILABLE`")
    })
    public ProductResponse uploadImage(@PathVariable Long id,
                                       @RequestPart("file") @Schema(type = "string", format = "binary") MultipartFile file) {
        return productService.uploadImage(id, file);
    }

    /** Supprime l'image d'un produit. Reserve aux administrateurs cote passerelle. */
    @DeleteMapping("/{id}/image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer l'image d'un produit", description = "Reserve au role ADMIN.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Image supprimee (ou produit sans image)"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Role insuffisant (ADMIN requis)"),
            @ApiResponse(responseCode = "404", description = "Produit introuvable")
    })
    public void deleteImage(@PathVariable Long id) {
        productService.removeImage(id);
    }

    /** Sert l'image courante d'un produit (lecture publique, comme le catalogue). */
    @GetMapping("/{id}/image")
    @Operation(summary = "Recuperer l'image d'un produit", description = "Lecture publique : renvoie les octets de l'image courante avec son `Content-Type`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image binaire",
                    content = @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "404", description = "Produit introuvable ou sans image"),
            @ApiResponse(responseCode = "503", description = "Stockage indisponible")
    })
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        StoredImage image = productService.loadImage(id);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(image.contentType());
        } catch (InvalidMediaTypeException ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(image.data());
    }
}
