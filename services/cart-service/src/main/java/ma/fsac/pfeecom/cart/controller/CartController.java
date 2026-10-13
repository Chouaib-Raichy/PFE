package ma.fsac.pfeecom.cart.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.fsac.pfeecom.cart.dto.request.AddItemRequest;
import ma.fsac.pfeecom.cart.dto.request.MergeCartRequest;
import ma.fsac.pfeecom.cart.dto.response.CartResponse;
import ma.fsac.pfeecom.cart.security.CurrentUser;
import ma.fsac.pfeecom.cart.service.CartService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST «/api/carts» de gestion des paniers.
 *
 * <p>Chaque opération exige un jeton valide et vérifie que l'appelant est bien le
 * propriétaire du panier ciblé (ou un administrateur) — un client ne peut pas
 * consulter ni modifier le panier d'un autre client.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@RestController
@RequestMapping("/api/carts")
@RequiredArgsConstructor
@Tag(name = "Carts", description = "Gestion des paniers du e-commerce")
public class CartController {

    private static final String OWNERSHIP_EXAMPLE =
            "{\"timestamp\":\"2026-01-01T10:00:00Z\",\"status\":403,\"code\":\"FORBIDDEN\","
                    + "\"message\":\"Acces refuse a la ressource d'un autre client.\",\"details\":null}";
    private static final String NOT_FOUND_EXAMPLE =
            "{\"timestamp\":\"2026-01-01T10:00:00Z\",\"status\":404,\"code\":\"NOT_FOUND\","
                    + "\"message\":\"Produit introuvable: 42\",\"details\":null}";
    private static final String ADD_ITEM_EXAMPLE = "{\"productId\":1,\"quantity\":2}";

    private final CartService cartService;
    private final CurrentUser currentUser;

    @GetMapping("/{customerId}")
    @Operation(summary = "Consulter le panier d'un client",
            description = "Authentifie. Controle de propriete : seul le client proprietaire du panier (ou un administrateur) peut y acceder.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Panier (items + total)"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Panier d'un autre client - `FORBIDDEN`",
                    content = @Content(examples = @ExampleObject(value = OWNERSHIP_EXAMPLE)))
    })
    public CartResponse getCart(@PathVariable Long customerId, Authentication authentication) {
        currentUser.requireOwnership(authentication, customerId);
        return cartService.getCart(customerId);
    }

    @PostMapping("/{customerId}/items")
    @Operation(summary = "Ajouter un produit au panier d'un client",
            description = "Authentifie + propriete du panier. Le produit et le client sont valides par appels inter-services.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Panier mis a jour"),
            @ApiResponse(responseCode = "400", description = "Donnees invalides - `VALIDATION_FAILED`"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Panier d'un autre client - `FORBIDDEN`",
                    content = @Content(examples = @ExampleObject(value = OWNERSHIP_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "Client ou produit introuvable - `NOT_FOUND`",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    public ResponseEntity<CartResponse> addItem(
            @PathVariable Long customerId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(name = "Exemple", value = ADD_ITEM_EXAMPLE)))
            @Valid @RequestBody AddItemRequest request,
            Authentication authentication) {
        currentUser.requireOwnership(authentication, customerId);
        return ResponseEntity.ok(cartService.addItem(customerId, request));
    }

    @PostMapping("/{customerId}/merge")
    @Operation(summary = "Fusionner un panier invite dans le panier serveur",
            description = "Authentifie + controle de propriete. Idempotent : rejouer la requete ne duplique aucune ligne (quantite retenue = max des deux).")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Panier fusionne"),
            @ApiResponse(responseCode = "400", description = "Donnees invalides - `VALIDATION_FAILED`"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Panier d'un autre client - `FORBIDDEN`",
                    content = @Content(examples = @ExampleObject(value = OWNERSHIP_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "Client ou produit introuvable - `NOT_FOUND`",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    public ResponseEntity<CartResponse> mergeCart(
            @PathVariable Long customerId,
            @Valid @RequestBody MergeCartRequest request,
            Authentication authentication) {
        currentUser.requireOwnership(authentication, customerId);
        return ResponseEntity.ok(cartService.mergeCart(customerId, request));
    }

    @DeleteMapping("/{customerId}/items/{itemId}")
    @Operation(summary = "Retirer une ligne du panier", description = "Authentifie + propriete du panier.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Panier mis a jour"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Panier d'un autre client - `FORBIDDEN`",
                    content = @Content(examples = @ExampleObject(value = OWNERSHIP_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "Ligne introuvable - `NOT_FOUND`",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    public CartResponse removeItem(@PathVariable Long customerId, @PathVariable Long itemId,
                                   Authentication authentication) {
        currentUser.requireOwnership(authentication, customerId);
        return cartService.removeItem(customerId, itemId);
    }

    @DeleteMapping("/{customerId}")
    @Operation(summary = "Vider le panier d'un client", description = "Authentifie + propriete du panier.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Panier vide"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Panier d'un autre client - `FORBIDDEN`",
                    content = @Content(examples = @ExampleObject(value = OWNERSHIP_EXAMPLE)))
    })
    public ResponseEntity<Void> clearCart(@PathVariable Long customerId, Authentication authentication) {
        currentUser.requireOwnership(authentication, customerId);
        cartService.clearCart(customerId);
        return ResponseEntity.noContent().build();
    }
}