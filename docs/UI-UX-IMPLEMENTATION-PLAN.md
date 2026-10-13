# Plan d'implémentation UI/UX — Boutique e-commerce premium (PFE)

> Phase 0/1 — direction artistique, architecture frontend cible et plan
> d'exécution. À lire avec `docs/PLATFORM-INTEGRATION-AUDIT.md`.

---

## 1. Objectifs

1. Transformer le frontend mono-fichier actuel en une **application
   e-commerce professionnelle**, responsive, accessible et maintenable.
2. Séparer clairement l'**expérience client** et l'**espace d'administration**.
3. Connecter **toutes** les pages aux API réelles via le gateway (aucun tableau
   codé en dur).
4. Préserver strictement l'authentification Keycloak et l'autorisation côté
   serveur.
5. Poser un **design system** réutilisable, base des phases suivantes
   (recherche ES, images MinIO).

---

## 2. Audit de l'UI actuelle

- SPA mono-fichier `App.tsx` (≈730 lignes) sans routeur : navigation par état
  interne (`shop`/`cart`/`orders`).
- Thème sombre unique (`styles.css`), pas de tokens de design.
- États de chargement/erreur/vide rudimentaires (quelques paragraphes).
- Aucune page produit dédiée, aucune recherche, aucun profil, aucune 403/404.
- Mélange des responsabilités (un ADMIN gère les clients via une modale dans
  l'expérience d'achat).
- Le client API `fetch` (`api.ts`) et le wrapper Keycloak (`auth.ts`) sont
  corrects et **à réutiliser**.

---

## 3. Direction artistique (design system)

Palette « premium » noir / blanc / or :

| Token | Valeur | Usage |
|---|---|---|
| `--bg` | `#0B0B0C` | fond principal |
| `--surface` | `#141416` | cartes, panneaux |
| `--surface-2` | `#1C1C1F` | surfaces élevées |
| `--border` | `#2A2A2E` | séparateurs |
| `--text` | `#F5F5F4` | texte principal |
| `--muted` | `#A1A1AA` | texte secondaire |
| `--gold` | `#C8A24B` | accent / CTA / focus |
| `--gold-strong` | `#E0BC66` | survol |
| `--danger` | `#E5484D` | erreurs, rupture |
| `--success` | `#3FB950` | succès |

- Typographie système propre (`system-ui`, `Segoe UI`, `Inter` si dispo).
- Échelle d'espacements 4/8/12/16/24/32/48.
- Rayons cohérents (`--r-sm 8`, `--r-md 12`, `--r-lg 16`).
- Ombres discrètes, transitions 150–200 ms.
- Composants réutilisables : `Button`, `Input`, `Select`, `Badge`, `Card`,
  `Modal`/`Dialog`, `Toast`, `Skeleton`, `EmptyState`, `Pagination`,
  `Breadcrumbs`, `Table`, `Tabs`, `Dropdown`.
- Accessibilité : contraste AA, `:focus-visible` doré, navigation clavier,
  `aria-*` sur les motifs interactifs.

Interdits : dégradés criards, cartes surdimensionnées, animations superflues,
templates de dashboard génériques.

---

## 4. Architecture frontend cible

```
src/
  main.tsx                 # bootstrap + init Keycloak + Router
  app/
    router.tsx             # routes + garde ProtectedRoute / RequireAdmin
    providers.tsx          # Toast, Auth context
  lib/
    api.ts                 # client HTTP centralisé (fetch/axios) + ApiError
    auth.ts                # wrapper keycloak-js (existant, enrichi)
    format.ts              # prix, dates, helpers
    env.ts                 # configuration par variables d'environnement
  ui/                      # design system (Button, Input, Card, Modal…)
  features/
    catalog/               # home, liste, détail produit, catégories, recherche
    cart/                  # panier
    orders/                # historique + détail commande
    account/               # profil client
    admin/                 # dashboard + modules (Phase 3)
  pages/                   # Forbidden (403), NotFound (404)
  styles/
    tokens.css
    base.css
```

- **Routeur** : `react-router-dom` (routes protégées + `RequireAdmin`).
- **HTTP** : client centralisé (axios ou `fetch` enrichi) avec injection du
  Bearer, gestion 401/403/timeout, décodage `ApiError`.
- **État partagé** : Redux Toolkit **uniquement si nécessaire** ; sinon
  Context + hooks. (Éviter la complexité inutile.)
- **Formulaires** : `react-hook-form` + validation (`yup`/`zod`) pour les
  formulaires admin et client.
- **i18n** : FR par défaut ; AR + RTL préparés (Phase ultérieure selon besoin
  du catalogue — ne pas prétendre un support arabe complet sans test).

> L'ajout de dépendances (`react-router-dom`, `react-hook-form`, `axios`) est
> justifié : l'architecture demandée l'exige et rien d'équivalent n'existe.

---

## 5. Pages et composants (expérience client)

| Page | Route | Données (API réelle) |
|---|---|---|
| Accueil | `/` | produits mis en avant + catégories (`/api/products`, `/api/categories`) |
| Catalogue | `/catalog` | `GET /api/products?page&size` (+ filtre catégorie) |
| Détail produit | `/products/:id` | `GET /api/products/{id}` |
| Recherche | `/search?q=` | recherche (ES en Phase 4 ; fallback filtrage côté client en attendant) |
| Catégorie | `/catalog?categoryId=` | `GET /api/categories`, produits filtrés |
| Panier | `/cart` | `GET/POST/DELETE /api/carts/...` |
| Commandes | `/orders` | `GET /api/orders` |
| Détail commande | `/orders/:id` | `GET /api/orders/{id}` |
| Profil | `/account` | `GET /api/customers/{id}` (id du jeton) |
| Connexion | redirection Keycloak | thème Keycloak (Phase 2) |
| Accès refusé | `/403` | — |
| Introuvable | `*` | — |

Chaque page gère : chargement (squelettes), vide, erreur (retry), 401 (session
expirée), 403 (accès refusé), 5xx (message clair).

---

## 6. Espace d'administration (Phase 3 — réalisé, voir §11)

- Layout dédié : sidebar, barre supérieure, fil d'Ariane, recherche, menu
  profil, responsive.
- Modules : **Produits** (CRUD + images MinIO en Phase 5), **Catégories**
  (CRUD ; ajouter `PUT`/`DELETE` API), **Clients** (lecture + édition selon
  API), **Commandes** (liste + détail ; statut seulement si l'API le permet),
  **Vue d'ensemble** (compteurs réels dérivés des listes).
- Protection : route protégée **et** autorisation serveur (jamais un contrôle
  front-only).

---

## 7. Plan d'exécution

1. **Design system** : `tokens.css`, `base.css`, composants `ui/`.
2. **Socle app** : routeur, providers, client API, garde de routes.
3. **Pages client** : accueil, catalogue, détail produit, catégorie, panier,
   commandes, profil, 403/404.
4. **Recherche** (fallback client puis ES).
5. **Admin** (Phase 3).
6. **Vérifications** : `npm run typecheck`, `npm run lint`, `npm run build` à
   chaque étape ; tests frontend en Phase 7.

---

## 8. Critères d'acceptation (frontend)

- Build, typecheck et lint verts.
- Navigation complète sans rechargement, routes protégées fonctionnelles.
- Toutes les pages branchées sur l'API réelle (preuve d'exécution).
- Responsive desktop/tablette/mobile.
- Accessibilité de base : focus visible, clavier, contrastes.
- Aucune régression de sécurité (testé en Phase 7).

---

## 9. Phase 1 — implémentation (terminée)

### Occurrences réalisées

- **Routeur** : `react-router-dom` v7 installé ; `src/app/router.tsx` (routes
  gérées + `RequireAdmin`). Reconstruction de `main.tsx` (bootstrap Keycloak →
  rendu) et de `App.tsx` (composition `ToastProvider` → `AuthProvider` →
  `CartProvider` → `Router`).
- **Socle d'état** : `app/AuthProvider.tsx` (identité issue du jeton,
  `customerId` via claim `customer_id`), `app/CartProvider.tsx` (panier
  partagé : badge d'en-tête + page panier + ajout produit), hook `lib/useAsync.ts`
  (chargement/erreur/retry/cancel).
- **Layout** : `components/AppLayout.tsx` (header sticky, nav, recherche,
  panier, avatar, déconnexion, drawer mobile, footer).
- **Pages client** : Accueil (hero + catégories + produits en vedette),
  Catalogue (filtre catégorie + pagination client), Détail produit (quantité,
  stock, spéculations), Recherche (fallback filtrage local ; ES en Phase 4),
  Panier (lignes, quantités, récapitulatif, passer commande), Commandes
  (liste + détail avec table), Profil (compte + profil client + sécurité),
  403, 404.
- **Admin** : garde `RequireAdmin` (UX ; l'autorisation serveur reste la
  source de vérité) + `features/admin/AdminHomePage.tsx` (compteurs réels
  issus des listes API, modules en attente Phase 3).
- **Suppressions** : anciens `App.tsx` mono-fichier, `api.ts`, `auth.ts`,
  `styles.css` (remplacés par `lib/` et `styles/`).
- **Fichiers produits** : `src/lib/{api,auth,format,useAsync}.ts`,
  `src/types.ts` (`ProductInput`, `CategoryInput`), `src/ui/*` (Icon, Button,
  Field, Modal, Spinner, Skeleton, Badge, EmptyState, Pagination, Breadcrumbs,
  Toast, CardLink), `src/styles/{tokens,base,components,layout,pages,index}.css`.

### Vérifications exécutées

| Commande | Résultat |
|---|---|
| `npm run typecheck` (strict) | 0 erreur |
| `npm run lint` | 0 erreur (5 warnings `react-refresh` sur les providers/contextes — pattern réactif standard) |
| `npm run build` | OK — `dist/` regénéré (22,4 kB CSS / 384,3 kB JS) |

### Limites assumées

- La recherche catalogue est un **filtrage côté client** du catalogue chargé
  (`size=200`) en attendant l'endpoint Elasticsearch de la Phase 4 ; le champ
  recherche du header et la page `/search` sont déjà câblés (`api.searchProducts`
  à injecter).
- Pas de décrémentation d'un article panier (l'API n'expose que ajout /
  suppression de ligne ; bouton moins = retirer la ligne).
- Géo-quantité du panier : le minus ne retire que la ligne entière (cf. API
  cart-service : `POST items` avec `quantity >= 1`, `DELETE items/{id}`).
- L'aperçu visuel réel (rendu navigateur) n'a pas été capturé : navigation
  testée par typecheck/build ; le premier chargement redirige vers Keycloak
  (login requis), configuration existante inchangée.
- `Modal`/`Field` du design system sont en place mais servis par la Phase 3
  (formulaires admin).

---

## 10. Phase 2 — authentification brandée (terminée)

### Occurrences réalisées

- **Thème Keycloak `pfeecom`** : `keycloak/themes/pfeecom/`
  - `theme.properties` + `login/theme.properties` (`parent=keycloak.v2`,
    `styles=css/login.css`) : hérite des gabarits Keycloak 26 / PatternFly v5,
    remplace uniquement la feuille de style.
  - `login/resources/css/login.css` : thème premium noir / blanc / or (fond
    dégradé or, carte sombre, bouton et liens dorés, en-tête de marque avec
    monogramme « S », tagline « Espace sécurisé · PFE E-commerce »), responsive.
  - S'applique à la connexion **et** à l'inscription (`parent=keycloak.v2`
    partage `template.ftl`).
- **Montage** : `docker-compose.ecom.yml` → `./keycloak/themes/pfeecom` monté en
  lecture seule sur `/opt/keycloak/themes/pfeecom`.
- **Realm** (`keycloak/realm-pfeecom.json` + application au realm en cours via
  l'API admin) :
  - `loginTheme: "pfeecom"`, `displayNameHtml` (Shop + **PFE** doré) ;
  - `registrationAllowed: true`, `registrationEmailAsUsername: false`,
    `rememberMe: true` ;
  - i18n : `internationalizationEnabled`, `supportedLocales: ["fr","en"]`,
    `defaultLocale: "fr"` ;
  - `defaultRoles: ["CUSTOMER"]` → tout nouvel inscrit obtient le rôle CUSTOMER.
- **`directAccessGrantsEnabled`** : **conservé** à `true` (choix explicite) — le
  SPA reste en Authorization Code + PKCE ; le password grant n'est utilisé que
  par les scripts runbook. Documenté comme durcissement séparé, voir audit §3.2.

### Vérifications exécutées

| Contrôle | Résultat |
|---|---|
| Conteneur Keycloak recréé avec le montage thème | `Up (healthy)` |
| Ressource CSS servie | `GET /resources/*/login/pfeecom/css/login.css` → **200** (4,8 ko, contenu pfeecom) |
| Page de connexion | CSS `login/pfeecom`, en-tête `Shop`+`PFE`, titre FR « Se connecter à PFE E-commerce » |
| Lien d'inscription | présent (`/realms/pfeecom/login-actions/registration`) — `registrationAllowed=true` |
| Page d'inscription (chargement partiel) | thème `pfeecom` (`.../login/pfeecom/js/...`), en-tête, sélecteur FR/EN |
| Realm JSON (fresh import) | import réussi sur un realm de test : `loginTheme=pfeecom`, `registrationAllowed=true`, `defaultLocale=fr`, `CUSTOMER` dans le rôle par défaut |

### Limites assumées

- ~~L'inscription crée l'utilisateur Keycloak **avec le rôle CUSTOMER**, mais
  **ne provisionne pas** automatiquement un profil dans `customer-service`~~ →
  **résolu** : à la première connexion, la SPA appelle `POST /api/customers/me` ;
  `customer-service` crée le profil côté serveur (clé sur le `sub` Keycloak),
  idempotent, et matérialise le claim `customer_id`. L'approche retenue est un
  **appel paresseux côté serveur**, pas un *event listener* Keycloak. Détails :
  `docs/RUNBOOK_ECOM.md` §3ter.
- Réinitialisation de mot de passe : lien présent mais **SMTP non configuré**
  (démo) — non testée de bout en bout.
- Le rendu visuel final n'a pas été capturé dans un navigateur ; la
  vérification porte sur le HTML/CSS servis et les endpoints (pas de capture
  d'écran).

---

## 11. Phase 3 — espace d'administration (terminée)

### Occurrences réalisées

**Backend — `product-service`** (les autres services étaient déjà complets) :

- `CategoryController` : ajout de `PUT /api/categories/{id}` et
  `DELETE /api/categories/{id}` ; `@Valid` désormais appliqué à la création
  (la contrainte `@NotBlank` du nom était jusque-là non appliquée).
- `ProductRepository.existsByCategoryId(Long)` pour détecter les produits liés.
- `ConflictException` + gestionnaire **409 CONFLICT** dans
  `GlobalExceptionHandler`.
- Garde-fou métier : la suppression d'une catégorie **encore référencée** par des
  produits est refusée (409) — cohérent avec la clé étrangère
  `fk_products_category ON DELETE RESTRICT` (sinon erreur SQL brute).
- **Gateway : aucune modification.** La matrice d'autorisation
  (`SecurityConfig`) autorisait déjà `PUT`/`DELETE` sur `/api/categories/**`
  pour le rôle `ADMIN` ; routes inchangées (`/api/categories/**` →
  `product-service`).

**Frontend — `frontend-ecom/src/features/admin/`** :

- `AdminLayout.tsx` : coquille dédiée (navigation latérale, barre supérieure,
  profil, déconnexion, retour boutique), routes imbriquées sous `/admin`.
- `ProductsAdminPage.tsx` : liste + recherche, création/édition (modale),
  activer/désactiver, suppression (confirmation).
- `CategoriesAdminPage.tsx` : création/édition/suppression ; le conflit 409 est
  remonté en notification.
- `CustomersAdminPage.tsx` : création/édition/suppression des fiches clients.
- `OrdersAdminPage.tsx` : liste complète (l'administrateur voit **toutes** les
  commandes via le jeton), filtre par statut, recherche, lien vers le détail.
- `AdminHomePage.tsx` : compteurs réels (produits, catégories, clients,
  commandes) + cartes modules fonctionnelles (plus d'état « Phase 3 »).
- `features/orders/OrderDetailPage.tsx` : prop `admin` pour réutiliser le même
  écran de détail (fil d'Ariane et retour adaptés) au lieu d'un doublon.
- `ui/ConfirmDialog.tsx` : boîte de confirmation réutilisable (suppressions).
- `lib/api.ts` : `createCategory` / `updateCategory` / `deleteCategory`.
- `styles/pages.css` : styles de la console (grille latérale, navigation,
  barre d'outils, formulaire en grille, responsive < 900 px).

### Vérifications exécutées

| Contrôle | Résultat |
|---|---|
| Tests `product-service` (`mvn -o test`) | **17 / 17** (5 nouveaux : update catégorie, update 404, delete 204, delete référencé 409, nom vide 400) |
| Frontend `npm run typecheck` | **0 erreur** |
| Frontend `npm run lint` | **0 erreur** (5 avertissements `react-refresh` préexistants) |
| Frontend `npm run build` | OK — 24,4 ko CSS / 410 ko JS |
| E2E gateway (jeton ADMIN) `POST`/`PUT`/`DELETE` catégorie | 201 / 200 / 204 |
| E2E suppression catégorie référencée | **409 CONFLICT** (puis 204 après suppression du produit) |
| E2E `GET /api/customers` (ADMIN) | 200, `totalElements=2` |
| E2E `POST /api/products` (CUSTOMER) | **403** — autorisation serveur intacte |
| Reconstruction conteneurs | `product-service`, `api-gateway`, `frontend-ecom` recréés et `healthy` |

### Limites assumées

- **Statut de commande non modifiable** : l'API `order-service` n'expose aucune
  mise à jour de statut — la console est volontairement en lecture seule et le
  signale dans l'interface. Aucun endpoint n'a été inventé.
- Listes chargées en une passe (`size=100`) + filtre client-side ; basculer sur
  la pagination serveur au-delà (commentaire `ponytail:` dans le code).
- Les écrans d'administration ne sont pas encore couverts par des tests
  frontend automatisés (prévus en Phase 7) ; la validation Phase 3 porte sur les
  tests backend, le build frontend et les vérifications E2E via le gateway.
- Aucune capture d'écran navigateur.

---

## 12. Phase 4 — recherche Elasticsearch (terminée)

> Détail complet (mapping, synchronisation, exploitation) :
> `docs/ELASTICSEARCH-SETUP.md`.

### Occurrences réalisées

**Infrastructure** :

- `docker-compose.ecom.yml` : service `elasticsearch`
  (`docker.elastic.co/elasticsearch/elasticsearch:8.13.4`, nœud unique,
  `xpack.security.enabled=false`, heap 512 Mo, `bootstrap.memory_lock`,
  healthcheck, volume `elasticsearch-data`, port hôte **`127.0.0.1:9200`** lié à
  la boucle locale). **Aucun `depends_on`** depuis `product-service` (démarrage
  indépendant, dégradation gracieuse).
- `config-repo/product-service.yml` : URI ES par variable
  (`${ELASTICSEARCH_URIS:http://elasticsearch:9200}`), délais, `app.search.*`,
  `management.health.elasticsearch.enabled=false`. Surcharge `local` :
  `http://localhost:9200`. Config de test : `enabled=false`, index
  `pfeecom-products-test`.

**Backend — `product-service`** :

- Dépendance `spring-boot-starter-data-elasticsearch` (Spring Data ES 5.3.10,
  client 8.13.4, aligné sur l'image).
- `resources/search/product-index.json` : mapping (analyzer `french`,
  sous-champ `name.ar` analyzer `arabic`, `price` scaled_float, dates
  `epoch_millis`), shards=1 replicas=0.
- **Outbox durable** : migration Flyway **`V3__product_search_outbox.sql`**
  (table `product_search_outbox`, sans clé étrangère) ;
  `SearchOutboxRepository.enqueue` (upsert natif `ON CONFLICT DO NOTHING`) ;
  `ProductServiceImpl.create/update/delete` désormais `@Transactional` (écriture
  produit + enqueue dans la même transaction, enqueue conditionné par
  `app.search.enabled`).
- `ProductSearchOutboxProcessor` (vidage planifié toutes les 5 s ; échec
  conservé pour nouvel essai) + `SearchIndexInitializer` (ensureIndex + réindex
  si index vide) + `@EnableScheduling` sur l'application.
- `ProductSearchService(Impl)` : `multi_match` (`name^3`, `name.ar^2`,
  `categoryName^2`, `description`, `fuzziness=AUTO`), filtre `active` +
  catégorie, tri `_score`/`id`, surlignage `<em>…</em>` ; limites `q ≤ 100`,
  `size 1..50`, suggestions ≤ 10.
- Repli PostgreSQL (`ProductRepository.searchFallback`/`suggestFallback`) avec
  drapeau `degraded=true` ; `SearchUnavailableException` → **503**.
- Endpoints : `GET /api/products/search`, `GET /api/products/suggest`
  (**publics**), `POST /api/products/reindex` (**ADMIN**) — via la route gateway
  existante `/api/products/**`, **gateway inchangé**.

**Frontend** :

- `components/SearchBox.tsx` (debounce 250 ms, `AbortController`, navigation
  clavier, ARIA combobox) intégré à l'en-tête et au tiroir mobile ;
  `ui/HighlightedText.tsx` (surlignage sans `dangerouslySetInnerHTML`) ;
  `ProductCard` (prop `nameHighlight`) ; `features/catalog/SearchPage.tsx`
  réécrite en recherche serveur + bannière dégradé ; types `ProductSearchHit`/
  `SearchPage`/`SuggestItem`/`SuggestResponse` et `api.searchProducts`/
  `suggestProducts` ; styles dans `styles/pages.css`.

### Vérifications exécutées

| Contrôle | Résultat |
|---|---|
| `mvn test` product-service | **28/28** (22 DB/repli + 6 ES) |
| `mvn test` api-gateway | **10/10** (+4 routes search/suggest/reindex) |
| Frontend `npm run typecheck` | **0 erreur** |
| Frontend `npm run lint` | **0 erreur** (5 avertissements `react-refresh` préexistants) |
| Frontend `npm run build` | OK |
| E2E `POST /api/products/reindex` (ADMIN) | `{"indexed":3}` |
| E2E `GET /search?q=bluetooth` | `degraded=false`, `highlights.name="Casque <em>Bluetooth</em>"` |
| E2E `GET /suggest?q=char` | `Charger`, `degraded=false` |
| E2E `POST /reindex` (CUSTOMER) | **403** |
| E2E création produit → recherche | indexé par l'outbox (≤ 5 s), trouvé |
| E2E suppression produit → recherche | `totalElements=0` (désindexé) |
| ES arrêté : `search`/`suggest` | `degraded=true`, repli PostgreSQL (pas d'erreur) |
| ES arrêté : `reindex` | **503** |
| ES redémarré : `search` | `degraded=false` (récupération automatique) |
| Conteneurs | `product-service`, `frontend-ecom`, `elasticsearch` reconstruits/`healthy` |

### Limites assumées

- Analyse arabe/français limitée aux **analyseurs intégrés** ES (pas de
  racinisation/NLP maison) ; aucune analyse linguistique avancée revendiquée.
- Propagation du catalogue vers l'index **≤ 5 s** (intervalle de vidage outbox),
  pas de synchronisation temps réel.
- Tests ES **ignorés** (et non « verts ») lorsque l'ES n'est pas joignable
  (`Assumptions.assumeTrue`) ; les 22 tests non-ES couvrent le repli.
- Surlignage : balises `<em>` interprétées côté frontend par découpage sûr ; un
  premier jeu de marqueurs de contrôle `\u0001`/`\u0002` a été écarté (la balise
  fermante n'était pas restituée par ES), choix documenté.
- Pas de capture d'écran navigateur.

---

## 13. Phase 5 — images produit via MinIO/S3 (terminée)

> Détail complet (image retenue, moindre privilège, exploitation) :
> `docs/MINIO-S3-SETUP.md`.

### Occurrences réalisées

**Infrastructure** :

- `docker-compose.ecom.yml` : service `minio`
  (`bitnamilegacy/minio:2025.7.23-debian-12-r5` épinglé par **digest**, UID 1001,
  données `/bitnami/minio/data`, volume `minio-data`, port hôte **`127.0.0.1:9000`**
  boucle locale, healthcheck) + service one-shot `minio-init` (crée bucket +
  utilisateur applicatif, **sort en 0**) + volume `minio-data`. `product-service`
  **ne dépend pas** de `minio` (dégradation gracieuse). Décision d'image
  documentée (Docker Hub `minio/minio` retiré, `quay.io` 401 → image `bitnamilegacy`).
- `docker/minio/init-bucket.sh` + `docker/minio/product-images-policy.json` :
  bucket `pfeecom-product-images` + policy **moindre privilège** (GetBucketLocation/
  ListBucket ; GetObject/PutObject/DeleteObject sur `/*`), attachée à
  `pfeecom-app`.
- `config-repo/product-service.yml` : `spring.servlet.multipart.*` (5/6 Mo) +
  `app.storage.*` (`enabled`, `endpoint`, `access-key`, `secret-key`, `bucket`,
  `max-image-bytes`) par variables d'environnement. `product-service-local.yml`
  (`localhost:9000`), config de test (`enabled=false`). `config-repo/api-gateway.yml` :
  `spring.codec.max-in-memory-size: 6MB`.

**Backend — `product-service`** :

- Dépendance `io.minio:minio:8.5.17`.
- Flyway **`V4__product_image_key.sql`** : `image_key VARCHAR(300)` (additive).
  `Product.imageKey` (clé S3 interne, jamais exposée) ; `imageUrl` = URL publique
  `/api/products/{id}/image`.
- Paquet `storage` : `StorageService`, `MinioStorageService` (client construit
  seulement si `app.storage.enabled`), `StoredImage`. **Clés générées côté
  serveur** `products/{id}/{yyyyMMddHHmmss}-{8 hex}.{ext}` (aucune entrée client
  → pas de path traversal). Type MIME **détecté par signature binaire**
  (JPEG/PNG/WEBP/GIF) et cohérent avec le type déclaré.
- `ProductServiceImpl.uploadImage/removeImage/loadImage` : stockage → écriture
  produit + outbox (transaction) → **suppression de l'ancien objet seulement
  après commit** ; échec → nettoyage de l'objet orphelin. `deleteImage`
  best-effort.
- Exceptions `InvalidImageException` (400 `INVALID_IMAGE`),
  `StorageUnavailableException` (503 `STORAGE_UNAVAILABLE`) + `FILE_TOO_LARGE`
  (413) dans `GlobalExceptionHandler`.
- `ProductController` : `POST /{id}/image` (ADMIN), `DELETE /{id}/image` (ADMIN,
  204), `GET /{id}/image` (public, octets + `Content-Type` + `Cache-Control`) —
  via la route gateway existante `/api/products/**`, **gateway inchangé**.

**Frontend** :

- `ui/ProductImage.tsx` (image avec repli placeholder sur erreur), utilisé par
  `ProductCard`, `ProductDetailPage`, `SearchBox` ; `api.uploadProductImage`
  (XHR + progression) / `api.deleteProductImage` ; section image dans
  `features/admin/ProductsAdminPage.tsx` (aperçu, sélection, suppression,
  upload) ; styles dans `styles/pages.css`.

### Vérifications exécutées

| Contrôle | Résultat |
|---|---|
| `mvn test` product-service | **44/44** (22 DB/repli + 8 images + 6 ES + 8 stockage) |
| `mvn test` api-gateway | **16/16** (+6 autorisation images) |
| Frontend `npm run typecheck` | **0 erreur** |
| Frontend `npm run lint` | **0 erreur** (5 avertissements `react-refresh` préexistants) |
| Frontend `npm run build` | OK |
| Conteneurs | `minio` **healthy**, `minio-init` **Exited (0)**, `product-service` healthy |
| E2E upload ADMIN (via gateway 8090) | **200**, `imageUrl=/api/products/1/image` |
| E2E métadonnées | `image_key=products/1/<ts>-<hex>.png` en base |
| E2E `GET /{id}/image` | **200**, `image/png`, octets identiques (SHA-256) |
| E2E upload sans jeton / CUSTOMER | **401** / **403** |
| E2E fichier invalide / >5 Mo | **400** `INVALID_IMAGE` / **413** `FILE_TOO_LARGE` |
| E2E remplacement | nouvelle clé ; **1 seul objet** dans le bucket (ancien supprimé) |
| E2E remplacement échoué | **400** ; image précédente conservée (clé + octets) |
| E2E utilisateur applicatif | `pfeecom-app` + policy `pfeecom-product-images` attachée |
| E2E `docker restart pfe-minio-1` (volume conservé) | objet persiste, `GET` **200** |

### Limites assumées

- **Image `bitnamilegacy` gelée** (namespace legacy, non mise à jour en amont) —
  reproductibilité par digest épinglé ; alternative documentée.
- **Pas de console web MinIO** (retirée des versions récentes) ; administration
  uniquement via `mc`/API S3 (pas d'UI de stockage).
- Un seul bucket, **pas de versioning d'objet** : le remplacement supprime
  l'ancien objet après commit (pas d'historique d'images).
- Validation d'image par **signature binaire** (JPEG/PNG/WEBP/GIF) — **pas** de
  ré-encodage/redimensionnement ni d'analyse antivirus.
- Pas de capture d'écran navigateur.

---

## 14. Phase 6 — documentation OpenAPI / Swagger (terminée)

> Détail complet (URLs, schémas, PKCE, exploitation) :
> `docs/SWAGGER-OPENAPI.md`.

### Occurrences réalisées

**Services métier** :

- 4 × `config/OpenApiConfig.java` (product/customer/cart/order) : métadonnées
  (`info`, licence, `servers: ["/"]` relatif) + schemes de sécurité
  `bearerAuth` (HTTP bearer JWT) **et** `oauth2` (Authorization Code + PKCE),
  requirement global `bearerAuth`.
- Annotations enrichies sur les contrôleurs : `@Tag`, `@Operation`,
  `@ApiResponses` et **exemples** (produits/catégories/recherche réécrits ;
  clients/paniers/commandes complétés) — y compris erreurs `401/403/404/409/413/503`
  et l'upload multipart.
- `config-repo/{product,customer,cart,order}-service.yml` : `springdoc.*` +
  `app.openapi.oauth.{authorization-url,token-url}` (URLs publiques Keycloak).

**Gateway (agrégation)** :

- `springdoc-openapi-starter-webflux-ui` (`2.6.0`) + `config-repo/api-gateway.yml` :
  routes `*-docs` (`/api-docs/<service>` → `SetPath=/v3/api-docs`),
  `springdoc.swagger-ui.urls` (les 4 services), `disable-swagger-default-url`.
- `SecurityConfig` : `permitAll` sur `/swagger-ui.html`, `/swagger-ui/**`,
  `/webjars/**`, `/api-docs/**`, `/v3/api-docs/**` ; `/actuator/**` et
  `/*/actuator/**` restent **refusés**.
- **Correction** : ne **pas** désactiver `springdoc.api-docs.enabled` sur le
  gateway — cela supprime aussi les ressources statiques de Swagger UI
  (`/webjars/swagger-ui/**` → 404). `api-docs` reste actif (le gateway expose en
  plus sa spec minimale sur `/v3/api-docs`, consommée par `/v3/api-docs/swagger-config`).

**Keycloak** :

- Client **public** `pfeecom-swagger` dans `keycloak/realm-pfeecom.json` :
  PKCE S256, `standardFlowEnabled`, `directAccessGrantsEnabled=false`,
  `implicitFlowEnabled=false`, **aucun secret**, redirect `http://localhost:8081-8090/*`.

**Tests** : `Product/Customer/Cart/OrderOpenApiTest` (spécification valide :
chemins, schémas, schemes) + `GatewayDocsSecurityTest` (docs publiques,
actuator refusé, config publique).

### Vérifications exécutées

| Contrôle | Résultat |
|---|---|
| `mvn test` product-service | **45/45** (44 + 1 OpenAPI) |
| `mvn test` customer-service | **10/10** (9 + 1 OpenAPI) |
| `mvn test` cart-service | **14/14** (13 + 1 OpenAPI) |
| `mvn test` order-service | **13/13** (11 + 1 OpenAPI) |
| `mvn test` api-gateway | **21/21** (16 + 5 docs) |
| **Backend total** | **103 / 103** — BUILD SUCCESS |
| Conteneurs (`--build`, 5 services) | tous **healthy**, aucun ERROR dans les logs |
| `GET /swagger-ui.html` (8090) | **302** → `/webjars/swagger-ui/index.html` |
| `GET /webjars/swagger-ui/index.html` | **200** `text/html` |
| `GET /v3/api-docs/swagger-config` | **200**, liste des 4 services |
| `GET /api-docs/{product,customer,cart,order}-service` | **200** `application/json`, **OpenAPI 3.0.1**, chemins + `bearerAuth`/`oauth2` |
| Public `GET /api/products` / `search` / `categories` | **200** |
| Protégé sans jeton (`/api/carts/1`, `/api/orders`, `/api/customers`) | **401** |
| Propriété (`/api/carts/2` en yassine ; `/api/carts/1` en sarah) | **403** |
| `GET /api/customers` (yassine / admin) | **403** / **200** |
| `POST /api/products {}` (admin / anon / yassine) | **400** / **401** / **403** |
| Actuator via 8090 (`/actuator/prometheus`, `/*/actuator/**`) | **401** (non exposé) |
| Client `pfeecom-swagger` (API admin) | public, code flow, **pas** de direct grant, **pas** de secret, PKCE **S256** |
| Endpoint d'autorisation PKCE | **200** formulaire de connexion (pas d'erreur client) |

### Limites assumées

- **Login interactif NON TESTÉ** : l'échange PKCE complet requiert une saisie
  navigateur ; seules la configuration du client et la réponse de l'endpoint
  d'autorisation sont vérifiées.
- **Realm existant** : l'export JSON n'importe le client `pfeecom-swagger` que
  sur un realm **neuf** ; sur le realm en cours, le client a été créé en direct
  via l'API admin (documenté).
- Pas de capture d'écran navigateur.
