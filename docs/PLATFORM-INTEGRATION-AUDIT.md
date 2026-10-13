# Audit d'intégration plateforme — E-commerce microservices (PFE)

> Phase 0 — état des lieux vérifié avant toute modification.
> Date d'audit : 2026-10-11. Répertoire : `D:\PROJECTS\PFE`.

Ce document distingue explicitement :

- **EXISTANT** : fonctionnalité présente dans le dépôt et vérifiée.
- **MANQUANT** : fonctionnalité absente du dépôt.
- **BLOCAGE** : limitation d'environnement empêchant une vérification.
- **PROPOSÉ** : changement envisagé pour les phases suivantes.

Toute affirmation est adossée à un fichier du dépôt. Aucune intégration n'est
déclarée « faite » sans preuve d'exécution.

---

## 1. Méthode et baseline

### 1.1 Environnement vérifié

| Élément | Valeur constatée |
|---|---|
| OS / shell | Windows, PowerShell 5.1 |
| Java | 21.0.9 (LTS) |
| Maven | 3.9.16 |
| Node / npm | v24.13.0 / 11.8.0 |
| Pandoc | 3.10 (utilisé pour le rapport) |
| Conteneurs en cours | 15 (`docker ps`) + `bevaren-keycloak` (hors projet, port 8080) |

### 1.2 Baseline de tests (rejouée pendant l'audit)

| Suite | Commande | Résultat |
|---|---|---|
| product-service | `mvn -f services\product-service\pom.xml test` | **45 / 45** — BUILD SUCCESS (Phase 6 ; dont 6 ES, ignorés si ES absent) |
| customer-service | `mvn -f services\customer-service\pom.xml test` | **10 / 10** — BUILD SUCCESS |
| cart-service | `mvn -f services\cart-service\pom.xml test` | **14 / 14** — BUILD SUCCESS |
| order-service | `mvn -f services\order-service\pom.xml test` | **13 / 13** — BUILD SUCCESS |
| api-gateway | `mvn -f services\api-gateway\pom.xml test` | **21 / 21** — BUILD SUCCESS (Phase 6 ; +5 docs OpenAPI) |
| **Backend total** | | **103 / 103** |
| Frontend typecheck | `npm run typecheck` | OK |
| Frontend lint | `npm run lint` | OK |
| Frontend build | `npm run build` | OK (`dist/` généré) |

Tests backend exécutés contre l'infrastructure de test dédiée
(`postgres-test` port hôte **5433**, `mongo-test` port hôte **27018**),
elle-même démarrée (`docker ps`). Aucun test ignoré.

---

## 2. Inventaire des composants (EXISTANT)

### 2.1 Services Spring Boot

| Service | Port interne | Persistance | Config | Tests |
|---|---|---|---|---|
| config-server | 8888 | — (mode natif, `config-repo/` monté) | — | — |
| discovery-service | 8761 | — (Eureka) | — | — |
| api-gateway | 8080 (hôte 8090) | — | `config-repo/api-gateway.yml` | 21 |
| product-service | 8081 | PostgreSQL 16 (`pfe_products`) + Flyway + **Elasticsearch 8.13.4** (index `pfeecom-products`) | `product-service.yml` | 45 |
| customer-service | 8082 | PostgreSQL 16 (`pfe_customers`) + Flyway | `customer-service.yml` | 10 |
| cart-service | 8083 | MongoDB 7 (`pfe_carts`) | `cart-service.yml` | 14 |
| order-service | 8084 | MongoDB 7 (`pfe_orders`) | `order-service.yml` | 13 |

### 2.2 Contrat d'API existant (vérifié dans le code)

Routes exposées via le gateway (`/api/**`), sécurité appliquée au gateway **et**
dans les services (défense en profondeur).

| Méthode | Chemin | AuthZ | Source |
|---|---|---|---|
| GET | `/api/products?page&size` | public | `ProductController#findAll` |
| GET | `/api/products/{id}` | public | `ProductController#findById` |
| GET | `/api/products/search?q&categoryId&page&size` | public | `ProductSearchController#search` (Phase 4) |
| GET | `/api/products/suggest?q` | public | `ProductSearchController#suggest` (Phase 4) |
| POST | `/api/products/reindex` | ADMIN | `ProductSearchController#reindex` (Phase 4) |
| POST | `/api/products` | ADMIN | `ProductController#create` |
| PUT | `/api/products/{id}` | ADMIN | `ProductController#update` |
| DELETE | `/api/products/{id}` | ADMIN | `ProductController#delete` |
| GET | `/api/categories` | public | `CategoryController#findAll` |
| GET | `/api/categories/{id}` | public | `CategoryController#findById` |
| POST | `/api/categories` | ADMIN | `CategoryController#create` |
| PUT | `/api/categories/{id}` | ADMIN | `CategoryController#update` |
| DELETE | `/api/categories/{id}` | ADMIN | `CategoryController#delete` |
| GET | `/api/customers?page&size` | ADMIN | `CustomerController#findAll` |
| GET | `/api/customers/{id}` | authentifié | `CustomerController#findById` |
| POST | `/api/customers` | ADMIN | `CustomerController#create` |
| PUT | `/api/customers/{id}` | ADMIN | `CustomerController#update` |
| DELETE | `/api/customers/{id}` | ADMIN | `CustomerController#delete` |
| GET | `/api/carts/{customerId}` | propriétaire / ADMIN | `CartController#getCart` |
| POST | `/api/carts/{customerId}/items` | propriétaire / ADMIN | `CartController#addItem` |
| DELETE | `/api/carts/{customerId}/items/{itemId}` | propriétaire / ADMIN | `CartController#removeItem` |
| DELETE | `/api/carts/{customerId}` | propriétaire / ADMIN | `CartController#clearCart` |
| GET | `/api/orders?page&size` | authentifié (filtré) | `OrderController#findAll` |
| GET | `/api/orders/{id}` | propriétaire / ADMIN | `OrderController#findById` |
| POST | `/api/orders` | propriétaire / ADMIN | `OrderController#create` |

Notes :

- **Aucune route `GET /api/carts`** (sans `customerId`) n'existe : le chemin non
  reconnu tombe sur le handler d'exception générique. Ce n'est pas une
  régression, seulement une route inexistante.
- `OrderStatus` = `{ CONFIRMED, CANCELLED }` (`OrderStatus.java`). Pas d'état
  « en préparation / expédiée / livrée ».
- ~~Aucune opération `PUT`/`DELETE` sur les catégories.~~ **Résolu (Phase 3)** :
  `PUT`/`DELETE /api/categories/{id}` ajoutés (ADMIN) ; suppression refusée en
  `409` si des produits référencent la catégorie.
- Aucune opération de mise à jour du statut de commande.
- Aucun endpoint de statistiques (compteurs, répartition des statuts).
- ~~Aucun endpoint de recherche/filtrage produits (uniquement pagination
  `page`/`size`).~~ **Résolu (Phase 4)** : `GET /api/products/search` et
  `GET /api/products/suggest` (publics) + `POST /api/products/reindex` (ADMIN),
  adossés à Elasticsearch avec repli PostgreSQL (`degraded=true`). Voir
  `docs/ELASTICSEARCH-SETUP.md`.

### 2.3 Sécurité (EXISTANT)

- Gateway ressource OAuth2 JWT (`api-gateway/.../config/SecurityConfig.java`) :
  émetteur + signature validés (`issuer-uri` public, `jwk-set-uri` interne).
- Matrice d'autorisation : catalogue en lecture public ; écritures
  produits/catégories/clients et listing clients = ADMIN ; reste = authentifié.
- Contrôle de **propriété** panier/commande via `customer_id` du jeton
  (`CurrentUser.requireOwnership`), jamais fourni par le navigateur.
- Actuator du gateway isolé sur un port de gestion interne non publié.
- Realm Keycloak `pfeecom` (`keycloak/realm-pfeecom.json`) :
  - client public `pfeecom-frontend` (Authorization Code + PKCE S256) ;
  - client **public `pfeecom-swagger`** (Authorization Code + **PKCE S256**,
    `directAccessGrantsEnabled=false`, aucun secret) — Swagger UI (Phase 6) ;
  - client `pfeecom-api` (bearer-only) ;
  - rôles realm `ADMIN`, `CUSTOMER` ;
  - mapper d'audience `pfeecom-api` ;
  - mapper d'attribut `customer_id` ;
  - utilisateurs de démo : `admin/admin` (ADMIN), `yassine/yassine` (CUSTOMER,
    `customer_id=1`), `sarah/sarah` (CUSTOMER, `customer_id=2`).

### 2.4 Frontend (EXISTANT — refondu en Phase 1)

`frontend-ecom/` — React 19 + Vite 7 + TypeScript (strict). Depuis la Phase 1
d'UI/UX, la SPA est structurée :

- `src/app/` : `router.tsx` (React Router), `AuthProvider` (identité issue du
  jeton), `CartProvider` (panier partagé), `RequireAdmin`.
- `src/lib/` : `api.ts` (client `fetch` centralisé + `ApiError` — réutilisé),
  `auth.ts` (wrapper `keycloak-js` — enrichi : `register`, `tokenDisplayName`),
  `format.ts` (prix, dates, initiales, email), `useAsync.ts`.
- `src/ui/` : design system (Icon, Button, Field, Modal, Spinner, Skeleton,
  Badge, EmptyState, Pagination, Breadcrumbs, Toast, CardLink).
- `src/features/` : `catalog/` (accueil, catalogue, détail, recherche),
  `cart/`, `orders/`, `account/` (profil), `admin/` (fondation).
- `src/styles/` : `tokens.css`, `base.css`, `components.css`, `layout.css`,
  `pages.css` — thème premium noir / blanc / or.

Dépendances : `keycloak-js`, `react`, `react-dom`, **`react-router-dom`** (seul
ajout Phase 1). L'ancienne SPA mono-fichier (`App.tsx` ≈730 lignes), `api.ts`,
`auth.ts` et `styles.css` ont été supprimés.

### 2.5 Documentation / livrables (EXISTANT)

- `docs/` : `RUNBOOK_ECOM.md`, `FINAL_TECHNICAL_AUDIT.md`,
  `POLYGLOT_PERSISTENCE_DECISION.md`.
- `rapport/` (rapport.md + rapport.docx), `soutenance/` (slides + script),
  `GUIDE_SOUTENANCE.md`, `README.md`, `config-repo/README.md`.
- `archive/pfe-flow/` : monolithe historique archivé (à préserver).

---

## 3. Ce qui manque (MANQUANT)

### 3.1 UI/UX

> ✅ **Résolu en Phase 1** — voir `docs/UI-UX-IMPLEMENTATION-PLAN.md` §9
> (design system, routeur, pages client, états chargement/vide/erreur,
> accessibilité de base, fondation admin). ✅ **Espace d'administration complet
> livré en Phase 3** (voir plan §11) : CRUD produits/catégories/clients, suivi
> des commandes, compteurs réels. Reste, selon besoin du catalogue, l'i18n
> FR/AR + RTL (non revendiquée sans test).

### 3.2 Authentification / Keycloak

> ✅ **Traité en Phase 2** (voir `docs/UI-UX-IMPLEMENTATION-PLAN.md` §10).
> Thème `pfeecom` (habillage noir/or, français par défaut, en-tête de marque)
> monté dans le conteneur et activé sur le realm ; **inscription activée**
> (`registrationAllowed=true`) avec rôle `CUSTOMER` par défaut.

- ~~Thème Keycloak personnalisé : absent~~ → thème `keycloak/themes/pfeecom`
  (parent `keycloak.v2`), `loginTheme=pfeecom`.
- ~~Inscription : `registrationAllowed = false`~~ → `true` (liens connexion /
  inscription en français, sélecteur de langue FR/EN).
- Réinitialisation de mot de passe : portée par le thème (lien « mot de passe
  oublié ») ; dépend de la configuration SMTP du realm (non configurée pour la
  démo).
- ⚠️ `directAccessGrantsEnabled: true` sur le client public `pfeecom-frontend` :
  **conservé** (choix explicite). Le SPA utilise **Authorization Code + PKCE** ;
  le password grant ne sert qu'aux scripts de démonstration/test du runbook. Un
  passage à `false` durcirait la posture mais casserait ces scripts documentés —
  à traiter comme un durcissement séparé, non activé ici.

### 3.3 Espace d'administration

- Aucune interface d'administration (le frontend actuel mélange boutique et
  gestion client pour les ADMIN).
- Pas de tableau de bord, pas de modules CRUD admin dédiés.
- Compteurs/répartition des statuts : non exposés par l'API (dérivables côté
  client à partir des listes existantes, avec les limites de pagination).

### 3.4 Recherche (Elasticsearch)

> ✅ **Traité en Phase 4** (voir `docs/ELASTICSEARCH-SETUP.md` et
> `docs/UI-UX-IMPLEMENTATION-PLAN.md` §12). Service `elasticsearch` (8.13.4,
> nœud unique, non exposé), index `pfeecom-products`, synchronisation par
> **outbox durable** (Flyway V3) vidée toutes les 5 s, endpoints
> `search`/`suggest` (publics) + `reindex` (ADMIN), **repli PostgreSQL** en cas
> d'indisponibilité (`degraded=true`). Analyse limitée aux analyseurs intégrés
> ES (aucune analyse linguistique avancée revendiquée).

### 3.5 Stockage d'images (MinIO / S3)

> ✅ **Traité en Phase 5** (voir `docs/MINIO-S3-SETUP.md` et
> `docs/UI-UX-IMPLEMENTATION-PLAN.md` §13). MinIO (`bitnamilegacy/minio`, épinglé
> par digest, port hôte `127.0.0.1:9000`), bucket `pfeecom-product-images`,
> utilisateur applicatif **moindre privilège** (`pfeecom-app`), migration
> additive `image_key` (Flyway V4), endpoints `POST`/`DELETE` (**ADMIN**) +
> `GET` (public) sur `/api/products/{id}/image`, clés générées côté serveur,
> suppression de l'ancien objet après commit (remplacement sans perte).

### 3.6 OpenAPI / Swagger

> ✅ **Traité en Phase 6** (voir `docs/SWAGGER-OPENAPI.md` et
> `docs/UI-UX-IMPLEMENTATION-PLAN.md` §14). Annotations `@Tag`/`@Operation`/
> `@ApiResponses` + exemples sur les 4 services, métadonnées via
> `OpenApiConfig` (schémas de sécurité `bearerAuth` + `oauth2` PKCE), **Swagger
> UI agrégée** servie par le gateway (`/swagger-ui.html`, specs `/api-docs/<service>`),
> client public `pfeecom-swagger`. Docs **publiques**, opérations métier toujours
> protégées ; tests OpenAPI par service + gateway (103/103).

- ~~Annotations absentes sur product-service~~ → complétées.
- ~~Pas de schéma de sécurité JWT global~~ → `bearerAuth` + `oauth2` (PKCE).
- ~~Pas d'agrégation Swagger sur le gateway~~ → aggregée (`webflux-ui` + routes
  `*-docs`).

---

## 4. Blocages d'environnement (BLOCAGE)

| Blocage | Détail | Contournement |
|---|---|---|
| Port hôte 8080 occupé | `bevaren-keycloak` (hors projet) | gateway sur 8090 ; Keycloak projet sur 8181. Ne pas toucher `bevaren-keycloak`. |
| Port hôte 5432 occupé | PostgreSQL local | `postgres` sans port publié ; `postgres-test` sur 5433. |
| Clés CDN/proxy npm | Non testé dans cet audit | À vérifier lors de l'ajout de dépendances (axios, react-router…). |

Aucun blocage empêchant le développement ; contraintes de ports seulement.

---

## 5. Changements proposés (PROPOSÉ) — par phase

| Phase | Changement | Impact données |
|---|---|---|
| 1 | Refonte UI frontend (design system, routeur, pages, admin séparé) | Aucun (frontend) |
| 2 | Thème Keycloak + activation inscription + branding | Realm (re-import) — non destructif |
| 3 | Modules admin (produits, catégories, clients, commandes) | Ajouts API minimaux (voir ci-dessous) — **réalisé (plan §11)** |
| 4 | Elasticsearch : recherche + index + sync post-commit | Nouvel index (pas de perte Mongo/PG) — **réalisé (plan §12)** |
| 5 | MinIO : upload images produit | Nouveau bucket + clé d'objet (`image_key`) — **réalisé (plan §13)** |
| 6 | OpenAPI : annotations + sécurité JWT + tests | Aucun — **réalisé (plan §14, `docs/SWAGGER-OPENAPI.md`)** |
| 7 | Tests frontend + backend (ES, MinIO, sécurité) | Aucun |
| 8 | Documentation | Aucun |

### 5.1 Extensions d'API envisagées (à justifier, sécurisées ADMIN)

- `PUT /api/categories/{id}`, `DELETE /api/categories/{id}` — pour le module
  catégories admin (aujourd'hui seuls GET/POST existent). ✅ **Ajoutés en
  Phase 3** ; suppression refusée (409) si des produits référencent la
  catégorie.
- Mise à jour du statut de commande : uniquement si retenue ; nécessite une
  règle métier explicite (ex. `CANCELLED` par ADMIN). Sinon, documenter que
  l'API ne le supporte pas. → **Non retenue en Phase 3** : la console
  d'administration est en lecture seule sur les commandes et le signale.
- Statistiques tableau de bord : privilégier la dérivation depuis les listes
  paginées existantes (produits, clients, catégories, commandes) plutôt qu'un
  nouvel endpoint ; documenter la limite (comptage exact mais pagination pour
  répartition des statuts).
- Recherche : nouvel endpoint contrôlé exposé via le gateway
  (ex. `GET /api/products/search`) — jamais d'accès direct navigateur → ES.
  ✅ **Ajouté en Phase 4** (`search`/`suggest` publics, `reindex` ADMIN) ; ES non
  publié (accès service-only, port hôte lié à la boucle locale).
- Upload image : `POST /api/products/{id}/image` (multipart) et `DELETE` ;
  stockage MinIO, `image_key` = clé d'objet **interne** (l'`imageUrl` public
  reste `/api/products/{id}/image`) ; endpoints ADMIN + `GET` public. ✅ **Ajouté
  en Phase 5**.

Toute nouvelle opération est protégée **côté serveur** (gateway + service) et ne
rend jamais publique une opération d'administration.

---

## 6. Risques et contraintes

- **Non-régression sécurité** : toute refonte UI doit conserver la matrice
  d'autorisation et le contrôle de propriété (tests de régression en Phase 7).
- **Préservation des données** : migrations Flyway additives et compatibles ;
  index ES/MinIO reconstructibles sans supprimer Mongo/PG.
- **Cohérence PG ↔ ES** : pas de transaction atomique ; synchronisation
  post-commit documentée (idempotente, réversible).
- **Pas de secret en dur** : identifiants MinIO/ES par variables d'environnement
  (`${VAR:-default}` pour la démo).
- **Réécriture minimale** : réutiliser `keycloak-js`, la configuration Vite/Nginx
  et le client API existants au maximum.

---

## 7. Conclusion de l'audit

Le socle backend (7 services, sécurité, observabilité, persistance polyglotte)
est **fonctionnel et vert** (103/103 depuis la Phase 6). Réalisé depuis : (1) le
**frontend** refondu (Phase 1), (2) le **thème Keycloak** (Phase 2), (3) l'**espace
d'administration** (Phase 3), (4) **Elasticsearch** (Phase 4), (5) **MinIO**
(Phase 5), (6) la documentation **OpenAPI/Swagger** agrégée (Phase 6). Restent :
(7) les **tests** frontend/backend complémentaires et le durcissement, (8) la
documentation finale. Aucun blocage d'environnement majeur. Le plan d'exécution
détaillé se trouve dans `docs/UI-UX-IMPLEMENTATION-PLAN.md`.
