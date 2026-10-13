package ma.fsac.pfeecom.product.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Initialisation de l'index de recherche au demarrage : cree le mapping puis,
 * si l'index est vide, reindexe l'integralite du catalogue (indexation initiale).
 * Si Elasticsearch est indisponible, le service demarre normalement et la
 * recherche se replie sur PostgreSQL.
 *
 * @author PFE E-commerce - FSAC Casablanca
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexInitializer implements ApplicationRunner {

    private final SearchIndexService indexService;

    @Override
    public void run(ApplicationArguments args) {
        if (!indexService.isEnabled()) {
            log.info("Recherche Elasticsearch desactivee (app.search.enabled=false).");
            return;
        }
        if (!indexService.isAvailable()) {
            log.warn("Elasticsearch indisponible au demarrage : index non initialise, "
                    + "la recherche se replie sur PostgreSQL.");
            return;
        }
        try {
            indexService.ensureIndex();
            if (indexService.documentCount() == 0) {
                indexService.reindexAll();
            }
        } catch (RuntimeException e) {
            log.warn("Initialisation de l'index de recherche en echec : {}", e.getMessage());
        }
    }
}
