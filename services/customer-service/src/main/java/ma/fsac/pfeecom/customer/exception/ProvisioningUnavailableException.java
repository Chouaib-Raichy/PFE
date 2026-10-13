package ma.fsac.pfeecom.customer.exception;

/**
 * Provisionnement momentanément indisponible (annuaire d'identité injoignable).
 * Le profil peut déjà exister : l'appel est réessayable et idempotent.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public class ProvisioningUnavailableException extends RuntimeException {

    public ProvisioningUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
