package ma.fsac.pfeecom.order.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Document MongoDB représentant une commande.
 *
 * <p>La commande embarque un instantané du client (nom, email) et ses lignes :
 * les données affichées restent correctes même si le client ou le produit
 * évolue par la suite.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Document(collection = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    private Long id;

    @Indexed
    private Long customerId;

    private String customerName;

    private String customerEmail;

    private OrderStatus status;

    /** Mode de paiement choisi (paiement à la livraison uniquement). */
    private PaymentMethod paymentMethod;

    /** Statut de paiement : {@code PENDING} à la création (jamais payé d'emblée). */
    private PaymentStatus paymentStatus;

    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Indexed
    private LocalDateTime createdAt;

    public void addItem(OrderItem item) {
        this.items.add(item);
    }
}