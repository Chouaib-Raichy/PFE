package ma.fsac.pfeecom.customer.service;

import ma.fsac.pfeecom.customer.dto.response.CustomerResponse;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Provisionnement du profil client à partir d'une identité Keycloak vérifiée.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public interface ProvisioningService {

    /**
     * Renvoie le profil client associé à l'utilisateur {@code sub} du jeton, en
     * le créant s'il n'existe pas encore. Idempotent : les appels répétés
     * (reconnexion, double soumission) renvoient toujours le même profil.
     *
     * @param jwt jeton d'accès validé de l'utilisateur (jamais fourni par le client)
     * @return le profil client (existant ou nouvellement créé)
     */
    CustomerResponse ensureProfile(Jwt jwt);
}
