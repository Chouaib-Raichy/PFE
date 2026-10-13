package ma.fsac.pfeecom.customer.client;

import ma.fsac.pfeecom.customer.exception.ProvisioningUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client serveur vers l'API d'administration Keycloak.
 *
 * <p>Utilisé au provisionnement pour renseigner l'attribut utilisateur
 * {@code customer_id}, qui alimente le claim JWT du même nom. S'authentifie via
 * un compte de service (client confidentiel dédié, grant {@code client_credentials})
 * dont le secret reste côté serveur (aucune donnée privilégiée dans le navigateur).</p>
 *
 * <p>Aucun secret ni jeton n'est journalisé.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Component
public class KeycloakAdminClient {

    private static final String CUSTOMER_ID_ATTRIBUTE = "customer_id";

    private final RestClient restClient = RestClient.create();
    private final String baseUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakAdminClient(
            @Value("${app.provisioning.keycloak-base-url}") String baseUrl,
            @Value("${app.provisioning.realm}") String realm,
            @Value("${app.provisioning.client-id}") String clientId,
            @Value("${app.provisioning.client-secret}") String clientSecret) {
        this.baseUrl = baseUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * Renseigne (ou met à jour) l'attribut {@code customer_id} de l'utilisateur
     * {@code sub}. Idempotent : réexécutable sans effet de bord.
     */
    public void setCustomerIdAttribute(String sub, long customerId) {
        try {
            String token = fetchAdminToken();
            Map<String, Object> user = fetchUser(sub, token);
            Map<String, Object> attributes = mutableAttributes(user);
            attributes.put(CUSTOMER_ID_ATTRIBUTE, List.of(String.valueOf(customerId)));
            user.put("attributes", attributes);
            updateUser(sub, user, token);
        } catch (RestClientException ex) {
            // Ni le jeton ni le secret ne figurent dans l'exception remontee.
            throw new ProvisioningUnavailableException(
                    "Echec de l'appel d'administration Keycloak lors du provisionnement.", ex);
        }
    }

    private String fetchAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri(baseUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        if (response == null || response.get("access_token") == null) {
            throw new ProvisioningUnavailableException(
                    "Reponse de jeton Keycloak invalide (compte de service).", null);
        }
        return String.valueOf(response.get("access_token"));
    }

    private Map<String, Object> fetchUser(String sub, String token) {
        @SuppressWarnings("unchecked")
        Map<String, Object> user = restClient.get()
                .uri(baseUrl + "/admin/realms/" + realm + "/users/" + sub)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(Map.class);
        if (user == null) {
            throw new ProvisioningUnavailableException(
                    "Utilisateur Keycloak introuvable pour le provisionnement.", null);
        }
        return user;
    }

    private void updateUser(String sub, Map<String, Object> user, String token) {
        restClient.put()
                .uri(baseUrl + "/admin/realms/" + realm + "/users/" + sub)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(user)
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mutableAttributes(Map<String, Object> user) {
        Object existing = user.get("attributes");
        if (existing instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        return new HashMap<>();
    }
}
