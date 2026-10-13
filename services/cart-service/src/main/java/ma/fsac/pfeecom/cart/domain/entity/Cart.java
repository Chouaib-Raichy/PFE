package ma.fsac.pfeecom.cart.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Document MongoDB représentant le panier d'un client.
 *
 * <p>Les lignes de panier sont embarquées dans le document (modèle documentaire) :
 * un seul accès suffit pour lire/écrire le panier complet.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Document(collection = "carts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {

    @Id
    private Long id;

    @Indexed(unique = true)
    private Long customerId;

    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    @CreatedDate
    private LocalDateTime createdAt;

    public void addItem(CartItem item) {
        this.items.add(item);
    }

    public void removeItem(Long itemId) {
        this.items.removeIf(item -> item.getId().equals(itemId));
    }

    /** Prochain identifiant de ligne, unique au sein du panier. */
    public Long nextItemId() {
        return items.stream().mapToLong(CartItem::getId).max().orElse(0L) + 1;
    }
}