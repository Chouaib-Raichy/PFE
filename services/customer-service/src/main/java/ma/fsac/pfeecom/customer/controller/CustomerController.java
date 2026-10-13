package ma.fsac.pfeecom.customer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.fsac.pfeecom.customer.dto.request.CustomerRequest;
import ma.fsac.pfeecom.customer.dto.response.CustomerResponse;
import ma.fsac.pfeecom.customer.service.CustomerService;
import ma.fsac.pfeecom.customer.service.ProvisioningService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * API REST «/api/customers» de gestion des clients.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Gestion des clients du e-commerce")
public class CustomerController {

    private static final String CUSTOMER_EXAMPLE =
            "{\"firstName\":\"Nadia\",\"lastName\":\"Kabbaj\",\"email\":\"nadia.kabbaj@demo.com\",\"phone\":\"0600000000\"}";

    private final CustomerService customerService;
    private final ProvisioningService provisioningService;

    @PostMapping("/me")
    @Operation(summary = "Provisionner le profil du client connecte",
            description = "Cree (ou renvoie) le profil client associe a l'utilisateur Keycloak "
                    + "authentifie, a partir de l'identifiant immuable du jeton. Idempotent : "
                    + "les appels repetes renvoient le meme profil. Aucun identifiant client "
                    + "n'est accepte depuis le navigateur.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil client existant ou cree"),
            @ApiResponse(responseCode = "400", description = "Inscription incomplete - `INVALID_PROFILE`"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "409", description = "Email deja utilise - `DUPLICATE_RESOURCE`"),
            @ApiResponse(responseCode = "503", description = "Provisionnement momentanement indisponible")
    })
    public CustomerResponse ensureProfile(@AuthenticationPrincipal Jwt jwt) {
        return provisioningService.ensureProfile(jwt);
    }

    @GetMapping
    @Operation(summary = "Lister les clients (pagine)", description = "Reserve au role ADMIN (applique par le gateway). Pagination Spring Data : `page`, `size`, `sort`.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de clients"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Role insuffisant (ADMIN requis)")
    })
    public Page<CustomerResponse> findAll(Pageable pageable) {
        return customerService.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter un client par identifiant", description = "Authentifie : un client accede a sa propre fiche, un administrateur a toutes.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Client trouve"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Acces refuse (client d'un autre)"),
            @ApiResponse(responseCode = "404", description = "Client introuvable")
    })
    public CustomerResponse findById(@PathVariable Long id) {
        return customerService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Creer un client", description = "Reserve au role ADMIN.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Client cree"),
            @ApiResponse(responseCode = "400", description = "Donnees invalides - `VALIDATION_FAILED`"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Role insuffisant (ADMIN requis)"),
            @ApiResponse(responseCode = "409", description = "Email deja utilise - `DUPLICATE_RESOURCE`",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"timestamp\":\"2026-01-01T10:00:00Z\",\"status\":409,"
                                    + "\"code\":\"DUPLICATE_RESOURCE\",\"message\":\"Un client avec cet email existe deja.\","
                                    + "\"details\":null}")))
    })
    public ResponseEntity<CustomerResponse> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(name = "Exemple", value = CUSTOMER_EXAMPLE)))
            @Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un client", description = "Reserve au role ADMIN.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Client modifie"),
            @ApiResponse(responseCode = "400", description = "Donnees invalides - `VALIDATION_FAILED`"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Role insuffisant (ADMIN requis)"),
            @ApiResponse(responseCode = "404", description = "Client introuvable"),
            @ApiResponse(responseCode = "409", description = "Email deja utilise - `DUPLICATE_RESOURCE`")
    })
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un client", description = "Reserve au role ADMIN.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Client supprime"),
            @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide"),
            @ApiResponse(responseCode = "403", description = "Role insuffisant (ADMIN requis)"),
            @ApiResponse(responseCode = "404", description = "Client introuvable")
    })
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ResponseEntity.ok(Map.of("deleted", true, "timestamp", LocalDateTime.now()));
    }
}