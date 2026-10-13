# Recherche Elasticsearch — configuration et exploitation (Phase 4)

> Recherche plein-texte du catalogue : index `pfeecom-products`, synchronisation
> PostgreSQL → Elasticsearch, endpoints `/api/products/search` et
> `/api/products/suggest`, repli PostgreSQL (dégradé). État vérifié le
> 12/10/2026. Chaque affirmation adossée à un fichier ou une commande exécutée.

---

## 1. Composants et versions

| Élément | Valeur |
|---|---|
| Dépendance | `spring-boot-starter-data-elasticsearch` (gérée par le BOM Spring Boot 3.3.10) |
| Resolve Spring Data Elasticsearch | **5.3.10** |
| Client `elasticsearch-java` | **8.13.4** |
| Image serveur | `docker.elastic.co/elasticsearch/elasticsearch:8.13.4` (épinglée, identique au client) |
| Conteneur | `pfe-elasticsearch-1` |
| Volume | `elasticsearch-data` |
| Index (prod/dev) | `pfeecom-products` |
| Index (tests) | `pfeecom-products-test` |

Le numéro de version de l'image est **exactement** 8.13.4 pour aligner serveur et
client (le client 8.x refuse un serveur de majeure incompatible ; aligner évite
les avertissements de compatibilité).

---

## 2. Service Docker Compose

Défini dans `docker-compose.ecom.yml` (service `elasticsearch`) :

- **Nœud unique** (`discovery.type=single-node`), `cluster.name=pfeecom-es`.
- `xpack.security.enabled=false` — le service n'est **joignable que sur le
  réseau Docker interne** `pfe-net` ; le port hôte est lié à la boucle locale
  (`127.0.0.1:9200:9200`) pour les tests lancés sur l'hôte. **Jamais** exposé au
  navigateur ni au LAN.
- Mémoire : `ES_JAVA_OPTS=-Xms512m -Xmx512m`, `bootstrap.memory_lock=true` +
  `ulimits.memlock` (`-1`) — évite le swap sur un conteneur de démo.
- Healthcheck : `curl -fs http://127.0.0.1:9200/_cluster/health`.
- `depends_on` **absent** côté `product-service` : la boutique démarre et
  fonctionne même si Elasticsearch est indisponible (voir §7).
- Indicateur de santé Spring désactivé
  (`management.health.elasticsearch.enabled=false`, `config-repo/product-service.yml`) :
  la santé de `product-service` n'est pas couplée à ES.

Démarrage :

```powershell
docker compose -f docker-compose.ecom.yml up -d elasticsearch
docker ps --filter name=elasticsearch          # Up (healthy)
Invoke-RestMethod http://127.0.0.1:9200         # tagline "You Know, for Search"
```

---

## 3. Mapping d'index

Source : `services/product-service/src/main/resources/search/product-index.json`,
appliqué par `SearchIndexService.ensureIndex()` (au démarrage via
`SearchIndexInitializer`, et avant `reindex`).

- `shards=1`, `replicas=0` (nœud unique).
- Champs indexés :
  - `name` (`text`) + sous-champ `name.ar` (analyzer intégré `arabic`),
  - `categoryName` (`text`),
  - `description` (`text`),
  - `fr_text` : analyzer intégré `french` (pas de NLP maison),
  - `price` : `scaled_float` (facteur 100),
  - `categoryId`, `active` (`keyword`/`boolean`) pour filtrage,
  - `imageUrl` (`keyword`, `index=false`),
  - `createdAt`/`updatedAt` : `epoch_millis`.

Aucun analyseur personnalisé n'est revendiqué : on s'appuie uniquement sur les
analyseurs intégrés d'Elasticsearch (`french`, `arabic`). Une analyse
linguistique arabe avancée (racinisation, désambiguïsation) n'est **pas**
réalisée et ne doit pas être présentée comme telle.

---

## 4. Synchronisation PostgreSQL → Elasticsearch (outbox durable)

Pas de transaction atomique entre PostgreSQL et Elasticsearch. La cohérence est
obtenue par une **outbox** idempotente dans la base relationnelle :

- Table `product_search_outbox(product_id PK, created_at)`, migration Flyway
  **`V3__product_search_outbox.sql`**. **Aucune clé étrangère** : la
  désindexation d'un produit supprimé doit rester possible.
- `SearchOutboxRepository.enqueue(...)` : requête native
  `INSERT ... ON CONFLICT (product_id) DO NOTHING` (`@Modifying @Transactional`).
- `ProductServiceImpl.create/update/delete` sont désormais `@Transactional` :
  l'écriture produit **et** l'enqueue outbox se font dans la même transaction
  (un rollback annule les deux). L'enqueue est conditionnée par
  `app.search.enabled`.
- `ProductSearchOutboxProcessor.drainOnce()` (planifié toutes les 5 s,
  `@Scheduled(fixedDelayString="${app.search.drain-interval-ms:5000}")`,
  `@EnableScheduling` sur `ProductServiceApplication`) :
  - relit chaque produit de l'outbox et l'indexe (ou le supprime de l'index
    s'il n'existe plus),
  - supprime la ligne d'outbox en cas de succès, la **conserve** en cas d'échec
    (nouvel essai au cycle suivant).
- `SearchIndexInitializer` : au démarrage, `ensureIndex()` puis réindexation
  complète **si l'index est vide** (amorçage initial sans action manuelle).

Conséquence : si Elasticsearch est momentanément indisponible, les écritures
produit ne sont pas perdues ; elles sont appliquées à l'index dès le retour d'ES.

---

## 5. Requêtes de recherche

`ProductSearchServiceImpl` :

- **Recherche** : `multi_match` sur `name^3, name.ar^2, categoryName^2,
  description`, `fuzziness=AUTO` ; filtre `active=true` (+ `categoryId` si
  fourni) ; tri `_score` desc puis `id` asc (départage déterministe).
- **Surlignage** : tags `pre_tag=<em>` / `post_tag=</em>` (balises ASCII, cf.
  §9), sans HTML injecté côté serveur.
- **Limites** : `q` ≤ 100 caractères, `size` borné à 1..50, `page ≥ 0`,
  suggestions ≤ 10.

---

## 6. Endpoints et autorisation

| Méthode | Chemin | Accès | Source |
|---|---|---|---|
| GET | `/api/products/search?q&categoryId&page&size` | **public** | `ProductSearchController#search` |
| GET | `/api/products/suggest?q` | **public** | `ProductSearchController#suggest` |
| POST | `/api/products/reindex` | **ADMIN** | `ProductSearchController#reindex` |

Toutes ces routes passent par la route gateway existante `/api/products/**`
(le littéral `search`/`suggest`/`reindex` prime sur `{id}`) : **le gateway n'a
pas eu besoin d'être modifié**. `reindex` est protégé ADMIN côté gateway **et**
côté service (défense en profondeur). ES n'est **jamais** joignable depuis le
navigateur : seul le service y accède, via le réseau interne.

En cas d'indisponibilité d'ES, `SearchUnavailableException` → **503** via
`GlobalExceptionHandler`.

---

## 7. Repli et dégradation (resilience)

Lorsque Elasticsearch est injoignable :

- `GET /search` et `GET /suggest` **ne renvoient jamais d'erreur** : ils
  répondent avec le repli PostgreSQL
  (`ProductRepository.searchFallback`/`suggestFallback`, `LIKE`, `active=true`,
  filtre catégorie) et posent `degraded=true`.
- `suggest` renvoie une liste vide (jamais 5xx).
- `POST /reindex` renvoie **503**.
- Le frontend affiche une bannière d'information (`.notice-warn`) quand
  `degraded=true`.
- `product-service` reste `healthy` (indicateur ES désactivé).

---

## 8. Frontend

- `src/components/SearchBox.tsx` : autocomplétion (debounce 250 ms,
  `AbortController` + protection anti-réponse obsolète, navigation clavier,
  motif ARIA combobox, états vide/chargement). Utilisée dans l'en-tête et le
  tiroir mobile (`AppLayout.tsx`).
- `src/features/catalog/SearchPage.tsx` : recherche serveur (page size 24),
  bannière dégradé, pagination.
- `src/ui/HighlightedText.tsx` : rend le surlignage **sans**
  `dangerouslySetInnerHTML` (découpage sur `<em>`/`</em>`, texte rendu en nœuds
  React échappés).
- `src/types.ts`, `src/lib/api.ts` : types `ProductSearchHit`/`SearchPage`/
  `SuggestItem`/`SuggestResponse`, méthodes `searchProducts`/`suggestProducts`.

---

## 9. Note sur les marqueurs de surlignage

Le surlignage utilise les balises ASCII `<em>` / `</em>`. Un premier essai avec
des marqueurs de contrôle (`\u0001` / `\u0002`) a montré que la balise
**fermante** `\u0002` était perdue par la sérialisation côté Elasticsearch ;
des balises ASCII ont été retenues (testées : les deux balises sont bien
présentes). Le frontend tolère par ailleurs une balise non fermée (le reste est
rendu comme texte). Aucune injection HTML n'est possible : `HighlightedText`
n'interprète que ces deux balises et échappe tout le reste.

---

## 10. Tests automatisés

- `ProductSearchElasticsearchTest` — **6/6** (nécessite un ES joignable :
  `@BeforeAll` `Assumptions.assumeTrue(elasticsearchReachable())`, sinon la
  classe est **ignorée**, jamais présentée comme réussie) :
  pertinence + surlignage, suggestions par préfixe, pagination/totaux, filtre
  catégorie, désindexation après suppression, `reindex` sans doublon.
- `ProductApiIntegrationTest` — **22/22** (couvre le repli PostgreSQL :
  recherche hors produits inactifs, filtre catégorie, `size` borné à 50,
  suggestions de repli, enqueue outbox idempotent).
- `GatewaySecurityTest` — **10/10** (+4 : `search` public, `suggest` public,
  `reindex` CUSTOMER → 403, `reindex` ADMIN non bloqué).

```powershell
# ES joignable : docker compose -f docker-compose.ecom.yml up -d elasticsearch
cd services/product-service ; mvn test   # 28/28 (22 + 6, dont 6 ES)
cd services/api-gateway     ; mvn test   # 10/10
```

---

## 11. Preuves d'exécution (E2E via gateway 8090)

Séquence exécutée le 12/10/2026 :

| Contrôle | Résultat |
|---|---|
| `POST /api/products/reindex` (ADMIN) | `{"indexed":3}` |
| `GET /api/products/search?q=bluetooth` | `degraded=false`, 1 résultat `Casque Bluetooth`, `highlights.name = "Casque <em>Bluetooth</em>"` |
| `GET /api/products/suggest?q=char` | `Charger`, `degraded=false` |
| `POST /api/products/reindex` (CUSTOMER) | **403** |
| Création produit → visible en recherche | produit créé, indexé par l'outbox (≤ 5 s), trouvé avec `degraded=false` |
| Suppression produit → désindexé | recherche du terme → `totalElements=0` |
| **ES arrêté** : `GET /search` | `degraded=true`, résultat servi depuis PostgreSQL |
| **ES arrêté** : `GET /suggest` | `degraded=true`, suggestions de repli |
| **ES arrêté** : `POST /reindex` | **503** |
| **ES arrêté** : santé de `product-service` | reste `healthy` |
| **ES redémarré** : `GET /search` | `degraded=false` (récupération automatique, index persistant par volume) |

Conteneurs `product-service` et `frontend-ecom` reconstruits ; `pfe-product-service-1`
et `pfe-elasticsearch-1` `healthy`.

---

## 12. Exploitation

```powershell
# Réindexer à la demande (reconstruit l'index depuis PostgreSQL)
# Jeton ADMIN requis (voir RUNBOOK_ECOM.md §3bis)
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8090/api/products/reindex' -Headers $auth

# État du cluster / de l'index
Invoke-RestMethod 'http://127.0.0.1:9200/_cluster/health'
Invoke-RestMethod 'http://127.0.0.1:9200/pfeecom-products/_count'
Invoke-RestMethod 'http://127.0.0.1:9200/pfeecom-products/_search?q=bluetooth'
```

### Dépendances de configuration

`config-repo/product-service.yml` :

```yaml
spring:
  elasticsearch:
    uris: ${ELASTICSEARCH_URIS:http://elasticsearch:9200}
    connection-timeout: 3s
    socket-timeout: 5s
app:
  search:
    enabled: ${SEARCH_ENABLED:true}
    index-name: pfeecom-products
    drain-interval-ms: 5000
management:
  health:
    elasticsearch:
      enabled: false
```

`config-repo/product-service-local.yml` : `uris: http://localhost:9200` (ES
lancé en Docker, port lié à la boucle locale).
`src/test/resources/application.yml` : `app.search.enabled=false`,
`index-name: pfeecom-products-test`.

---

## 13. Limites assumées

- Analyse **arabe/français** limitée aux analyseurs intégrés d'Elasticsearch ;
  aucune analyse linguistique avancée (racinisation arabe) n'est réalisée.
- Pas de synchronisation temps réel : latence de propagation **≤ 5 s**
  (intervalle de vidage de l'outbox).
- Surlignage rendu via balises `<em>` interprétées par un découpage côté
  frontend (pas de HTML brut).
- ES est un composant **reconstructible** (`reindex` depuis PostgreSQL) : aucune
  donnée métier n'y est source de vérité.
- `xpack.security` désactivé : acceptable uniquement parce qu'ES n'est pas
  exposé hors du réseau interne (port hôte lié à `127.0.0.1`).
