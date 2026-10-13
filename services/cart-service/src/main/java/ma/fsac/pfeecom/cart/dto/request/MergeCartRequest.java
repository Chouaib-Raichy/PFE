package ma.fsac.pfeecom.cart.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * DTO de requête pour la fusion d'un panier invité dans le panier serveur.
 *
 * <p>La fusion est idempotente : rejouer la même requête ne duplique aucune
 * ligne. Lorsqu'un produit est déjà présent, la quantité retenue est le
 * maximum des deux (et non une somme), de sorte qu'un renvoi après échec
 * réseau ne gonfle pas le panier.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
public record MergeCartRequest(

        @NotEmpty(message = "La liste d'articles ne doit pas être vide")
        List<@Valid AddItemRequest> items
) {}
