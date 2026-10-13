package ma.fsac.pfeecom.product.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.fsac.pfeecom.product.domain.entity.SearchOutboxEntry;
import ma.fsac.pfeecom.product.domain.entity.Product;
import ma.fsac.pfeecom.product.repository.ProductRepository;
import ma.fsac.pfeecom.product.repository.SearchOutboxRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Traitement differe de la file d'indexation. Relit le produit courant puis
 * indexe s'il existe, desindexe sinon — operation idempotente, sans doublon.
 *
 * <p>Toute erreur laisse la ligne en file : elle sera rejouee au cycle suivant
 * (resilience a une coupure/indisponibilite d'Elasticsearch). Une reindexation
 * complete ({@code POST /api/products/reindex}) sert de reconciliation.</p>
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSearchOutboxProcessor {

    private final SearchOutboxRepository outboxRepository;
    private final ProductRepository productRepository;
    private final SearchIndexService indexService;

    @Scheduled(fixedDelayString = "${app.search.drain-interval-ms:5000}")
    public void scheduledDrain() {
        if (!indexService.isEnabled()) {
            return;
        }
        try {
            drainOnce();
        } catch (RuntimeException e) {
            log.warn("Traitement de la file d'indexation en echec : {}", e.getMessage());
        }
    }

    /**
     * Traite une fois la file. Retourne le nombre de produits synchronises.
     * Les lignes en echec sont conservees pour un nouvel essai.
     */
    public int drainOnce() {
        List<Long> ids = outboxRepository.findAll().stream()
                .map(SearchOutboxEntry::getProductId)
                .toList();
        if (ids.isEmpty()) {
            return 0;
        }

        List<Long> done = new ArrayList<>();
        for (Long id : ids) {
            try {
                Optional<Product> product = productRepository.findById(id);
                if (product.isPresent()) {
                    indexService.indexProduct(product.get());
                } else {
                    indexService.deleteProduct(id);
                }
                done.add(id);
            } catch (RuntimeException e) {
                log.warn("Synchronisation differee du produit {} en echec, nouvel essai au "
                        + "prochain cycle : {}", id, e.getMessage());
            }
        }

        if (!done.isEmpty()) {
            outboxRepository.deleteAllByIdInBatch(done);
            indexService.refresh();
        }
        return done.size();
    }
}
