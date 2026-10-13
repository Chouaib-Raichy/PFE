package ma.fsac.pfeecom.order.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.fsac.pfeecom.order.dto.request.OrderRequest;
import ma.fsac.pfeecom.order.dto.response.OrderResponse;
import ma.fsac.pfeecom.order.security.CurrentUser;
import ma.fsac.pfeecom.order.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST «/api/orders» de gestion des commandes.
 *
 * <p>Un client ne voit que ses propres commandes et ne peut commander que pour
 * lui-même ; un administrateur voit et gère toutes les commandes. L'identité
 * provient du jeton, jamais du corps de requête.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Gestion des commandes du e-commerce")
public class OrderController {

    private static final String OWNERSHIP_EXAMPLE =
            "{\"timestamp\":\"2026-01-01T10:00:00Z\",\"status\":403,\"code\":\"FORBIDDEN\","
                    + "\"message\":\"Acces refuse a la ressource d'un autre client.\",\"details\":null}";
    private static final String ORDER_EXAMPLE =
            "{\"customerId\":1,\"paymentMethod\":\"CASH_ON_DELIVERY\","
                    + "\"items\":[{\"productId\":1,\"quantity\":2},{\"productId\":2,\"quantity\":1}]}";

    private final OrderService orderService;
    private final CurrentUser currentUser;

    @GetMapping
    @Operation(summary = "Lister les commandes (pagine, cloisonne par client)",
            description = "Authentifie. Un client ne voit que ses commandes ; un administrateur voit toutes les commandes. Pagination Spring Data : `page`, `size`, `sort`.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de commandes"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Compte sans client associe - `FORBIDDEN`",
                    content = @Content(examples = @ExampleObject(value = OWNERSHIP_EXAMPLE)))
    })
    public Page<OrderResponse> findAll(Pageable pageable, Authentication authentication) {
        if (currentUser.isAdmin(authentication)) {
            return orderService.findAll(pageable);
        }
        Long customerId = currentUser.customerId(authentication);
        if (customerId == null) {
            throw new AccessDeniedException("Aucun client associe au compte authentifie.");
        }
        return orderService.findByCustomer(customerId, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter une commande par identifiant",
            description = "Authentifie + propriete : un client n'accede qu'a ses propres commandes.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Commande trouvee"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Commande d'un autre client - `FORBIDDEN`",
                    content = @Content(examples = @ExampleObject(value = OWNERSHIP_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "Commande introuvable")
    })
    public OrderResponse findById(@PathVariable Long id, Authentication authentication) {
        OrderResponse order = orderService.findById(id);
        currentUser.requireOwnership(authentication, order.customerId());
        return order;
    }

    @PostMapping
    @Operation(summary = "Creer une commande pour un client",
            description = "Authentifie + propriete : un client ne peut commander que pour lui-meme. Le client et les produits sont valides par appels inter-services. "
                    + "Paiement a la livraison uniquement : `paymentMethod` = `CASH_ON_DELIVERY` (obligatoire). "
                    + "La commande est creee avec le statut de paiement `PENDING` (jamais marquee payee a la creation).")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Commande creee (statut CONFIRMED, paiement PENDING, total calcule)"),
            @ApiResponse(responseCode = "400", description = "Donnees invalides ou mode de paiement non supporte - `VALIDATION_FAILED` / `MALFORMED_JSON`"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Commande pour un autre client - `FORBIDDEN`",
                    content = @Content(examples = @ExampleObject(value = OWNERSHIP_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "Client ou produit introuvable - `NOT_FOUND`")
    })
    public ResponseEntity<OrderResponse> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(name = "Exemple", value = ORDER_EXAMPLE)))
            @Valid @RequestBody OrderRequest request,
            Authentication authentication) {
        currentUser.requireOwnership(authentication, request.customerId());
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(request));
    }
}
