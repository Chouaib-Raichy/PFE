# Runbook E-commerce Microservices (PFE FSAC Casablanca)

Stack de soutenance. Remplace PFE Flow comme démonstration active.
État vérifié au 13/10/2026 — seuls les éléments marqués ✅ sont prouvés.

## 1. Architecture

```
                       +----------------+
                       |  config-server |  (Spring Cloud Config, mode native)
                       +----------------+  8888  ->  depot config-repo/
                              ^  configuration au demarrage (spring.config.import)
                              |
                  +-------------+        +---------------------+
    Client  ----> |  api-gateway | <----> |  keycloak 26.1       |  8181 hote -> 8080
    (navigateur)  |  resource    |  JWT   |  realm: pfeecom      |  (OIDC : auth code + PKCE)
                  |  server OIDC |        +---------------------+
                  +-------------+                  8080 interne / 8090 hote
                         | lb:// (Eureka, 8761)
       +--------------+----------+-----------+----------------+
       |              |          |           |                |
+-------------+ +------------+ +----------+ +-------------+ +--------------+
|discovery-svc| |product-svc | |customer-svc| |cart-svc    | | order-svc   |
|   8761      | |   8081     | |  8082     | | 8083       | |  8084       |
+-------------+ +-----+------+ +-----+-----+ +-----+------+ +-----+--------+
                PostgreSQL   |   PostgreSQL  |    MongoDB    |    MongoDB
              +--------------+---------------+--------------------------+
              | postgres (PostgreSQL 16)      |  mongo (MongoDB 7)       |
              | pfe_products, pfe_customers   |  pfe_carts, pfe_orders   |
              | (volume postgres-data)        |  (volume mongo-data)     |
              +-------------------------------+--------------------------+
   Persistance POLYGLOTTE : relationnel (PostgreSQL) pour product/customer,
   documentaire (MongoDB) pour cart/order. Cf. docs/POLYGLOT_PERSISTENCE_DECISION.md

   Recherche : Elasticsearch 8.13.4 (index pfeecom-products), alimente par
   product-service via outbox PostgreSQL (latence <=5s). Cf. docs/ELASTICSEARCH-SETUP.md

   Images produit : MinIO (bucket pfeecom-product-images), port hote 127.0.0.1:9000
   (boucle locale). product-service (endpoint interne http://minio:9000) sert les
   octets via GET public. Cf. docs/MINIO-S3-SETUP.md

Appels inter-services (via Eureka + @LoadBalanced RestClient) :
  cart  -> product-service  (nom + prix du produit au moment de l'ajout)
  cart  -> customer-service (validation du client)
  order -> customer-service (nom/email client, validation)
  order -> product-service  (prix unitaire, validation)

Observabilite (reseau interne pfe-net uniquement) :
  chaque service expose /actuator/prometheus (Micrometer + Prometheus registry)
  prometheus (9090 hote)  --scrape-->  les 7 cibles (10s)
  grafana    (3000 hote)  --datasource--> prometheus ; dashboards provisionnes
  NB : l'actuator du gateway vit sur un port de GESTION interne (8081), non
       publie sur l'hote -> ses metriques ne sont jamais accessibles de l'exterieur.
```

**Frontière de confiance** : le gateway est le **seul** point d'entrée externe ;
product/customer/cart/order ne sont pas publiés sur l'hôte (réseau Docker privé).
Le gateway valide le JWT (émetteur, signature, expiration) et applique la matrice
d'autorisation ; cart et order sont eux aussi des resource servers et imposent en
plus la **propriété** (403 cross-customer). product et customer restent ouverts en
interne (appels inter-services sans jeton) — limitation assumée, pas de mTLS.

- **config-server** : Spring Cloud Config (8888), profil `native`, lit `config-repo/` monté en lecture seule — ✅ vérifié (6 clients chargent leur config).
- **discovery-service** : Eureka server (8761) — ✅ vérifié.
- **api-gateway** : routes `lb://product-service`, `lb://customer-service`, `lb://cart-service`, `lb://order-service` + locator Eureka — ✅ vérifié pour les 4. **Resource server OIDC (JWT Keycloak) + matrice d'autorisation** — ✅ vérifié (401/403/public via gateway 8090). Actuator sur **port de gestion interne 8081** (non publié) ; les routes `/**/actuator/**` sont refusées — ✅ vérifié.
- **product-service** : CRUD produits + catégories, **persistance PostgreSQL** (Spring Data JPA + Flyway, `ddl-auto=validate`), **recherche Elasticsearch** (index `pfeecom-products`, synchro par outbox, repli PostgreSQL `degraded`), Actuator, Prometheus, OpenAPI — ✅ vérifié. Ouvert en interne (lecture publique via le gateway). Tests **52/52** (dont 6 ES).
- **customer-service** : CRUD clients, **persistance PostgreSQL** (JPA + Flyway), email unique (409), validation, Swagger, **provisionnement automatique** (`POST /api/customers/me`, clé sur le `sub` Keycloak, idempotent) — ✅ vérifié. Resource server OIDC (lit le `sub` du jeton) ; reste **ouvert en interne** pour les appels inter-services (sans jeton) ; listing/écriture réservés à ADMIN via le gateway. Tests **17/17**.
- **cart-service** : panier par client, **persistance MongoDB** (inchangée), incrément de quantité, total, validation client+produit via inter-services — ✅ vérifié. Resource server OIDC + **contrôle de propriété** (403 cross-customer) — ✅ vérifié.
- **order-service** : commande avec snapshot client + produits, **persistance MongoDB** (inchangée), total, statut CONFIRMED + **paiement à la livraison uniquement** (`paymentMethod=CASH_ON_DELIVERY` obligatoire, `paymentStatus=PENDING`, jamais payée à la création) — ✅ vérifié. Resource server OIDC + propriété + **liste cloisonnée par client** — ✅ vérifié.
- **postgres** : PostgreSQL 16 (`postgres:16-alpine`), bases `pfe_products`/`pfe_customers` (rôles `product_svc`/`customer_svc`), volume `postgres-data`, **aucun port publié sur l'hôte** — ✅ vérifié (schéma + données Flyway, persistance `down`/`up`).
- **mongo** : MongoDB 7, bases `pfe_carts`/`pfe_orders` uniquement (cart/order) ; les anciennes bases `pfe_products`/`pfe_customers` restent présentes mais **gelées** (rollback possible) — ✅ vérifié.
- **elasticsearch** : `docker.elastic.co/elasticsearch/elasticsearch:8.13.4`, nœud unique, `xpack.security.enabled=false` (réseau interne uniquement), heap 512 Mo, volume `elasticsearch-data`, port hôte **`127.0.0.1:9200`** (boucle locale, tests) — ✅ vérifié (`_cluster/health` vert, réindex E2E `{"indexed":3}`). Endpoints `search`/`suggest` (publics) + `reindex` (ADMIN).
- **minio** : `bitnamilegacy/minio` épinglé par **digest** (namespace legacy gelé, AGPLv3, UID 1001), volume `minio-data`, port hôte **`127.0.0.1:9000`** (boucle locale) ; **pas de console web** (retirée des versions récentes). Service one-shot **`minio-init`** (bucket `pfeecom-product-images` + utilisateur `pfeecom-app` moindre privilège, **Exited 0**). `product-service` ne dépend pas de `minio` (dégradation gracieuse) — ✅ vérifié (upload/récupération/remplacement/rejet + persistance après `restart`). Image upload : `POST`/`DELETE` `/api/products/{id}/image` (ADMIN), `GET` public.
- **keycloak** : Keycloak 26.1 (`start-dev`), realm `pfeecom` importé (`keycloak/realm-pfeecom.json`), OIDC Authorization Code + PKCE — ✅ vérifié (découverte, JWKS, flux PKCE complet, jeton JWT).
- **frontend-ecom** : SPA React 19 + Vite + TS strict, `keycloak-js` (PKCE), nginx (`/api` → api-gateway), jeton Bearer + `401→refresh→retry`, UI gating par rôle — ✅ vérifié (typecheck/lint/build verts + flux PKCE + E2E réel via proxy et via nginx).
- **prometheus** : `prom/prometheus:v2.54.1` (9090), scrape `/actuator/prometheus` des 7 services sur le DNS interne — ✅ vérifié (7/7 cibles `up`, métriques réelles).
- **grafana** : `grafana/grafana:11.2.0` (3000), datasource Prometheus + dashboard provisionnés depuis `monitoring/` — ✅ vérifié (datasource OK, 11 panneaux avec données).
- **PFE Flow** (archive/pfe-flow/) : hors stack de soutenance, sources conservées.

## 2. Ports

| Service | Local (JAR) | Docker (hôte) | Note |
|---|---|---|---|
| config-server | 8888 | 8888 | Config centralisée (`config-repo/`) |
| Eureka | 8761 | 8761 | |
| product-service | 8081 | interne | PostgreSQL `pfe_products` |
| customer-service | 8082 | interne | PostgreSQL `pfe_customers` |
| cart-service | 8083 | interne | MongoDB `pfe_carts` |
| order-service | 8084 | interne | MongoDB `pfe_orders` |
| api-gateway | 8090 | **8090 → 8080** | 8080 hôte occupé par `bevaren-keycloak` (infra partagée, ne pas arrêter) ; actuator sur 8081 interne (non publié) |
| keycloak | — | **8181 → 8080** | OIDC, realm `pfeecom` ; 8080 hôte occupé par `bevaren-keycloak` |
| prometheus | — | **9090 → 9090** | scrape `/actuator/prometheus`, volume `prometheus-data` |
| grafana | — | **3000 → 3000** | datasource Prometheus + dashboards provisionnés, volume `grafana-data` |
| frontend-ecom (nginx) | 5173 (dev Vite) | **5173 → 80** | dev : proxy `/api` → 127.0.0.1:8090 ; Docker : nginx proxy `/api` → api-gateway:8080 |
| elasticsearch | 9200 | **127.0.0.1:9200 → 9200** | index `pfeecom-products`, volume `elasticsearch-data` ; **lié à la boucle locale, jamais exposé au LAN** |
| minio | 9000 | **127.0.0.1:9000 → 9000** | images produit, volume `minio-data` ; **lié à la boucle locale** ; console web non exposée (retirée) ; `minio-init` en sortie unique (Exited 0) |
| mongo | — | interne | document store cart/order (bases `pfe_carts`, `pfe_orders`), volume `mongo-data` |
| mongo-test | — | **27018 → 27017** | profil `test`, base des tests d'intégration cart/order |
| postgres | — | interne | PostgreSQL 16 (`pfe_products`, `pfe_customers`), volume `postgres-data` ; **pas de port hôte** (5432 déjà pris) |
| postgres-test | — | **5433 → 5432** | profil `test`, base des tests d'intégration product/customer |

## 3. Configuration centralisée (Spring Cloud Config)

La configuration des 6 services (ports, accès base — URI Mongo pour cart/order, datasource JDBC pour product/customer — zone Eureka, actuator, routes gateway) vit dans `config-repo/`, servi par **config-server** (profil `native`). Chaque service ne garde en local que le bootstrap (nom + `spring.config.import` + retry/fail-fast).

- `config-repo/application.yml` : valeurs communes (actuator, prometheus).
- `config-repo/<service>.yml` : valeurs par défaut (Docker).
- `config-repo/<service>-local.yml` : surcharges dev hors Docker (profil `local`).
- Secrets : **aucun secret en clair** ; les valeurs sensibles passent par variables d'environnement (`${VAR:defaut}`).
- Fail-fast + retry : `spring.cloud.config.fail-fast=true` + 20 tentatives — un service **refuse de démarrer** si le config-server est injoignable (`ConfigClientFailFastException`).

Vérifier la configuration servie :

```powershell
Invoke-RestMethod http://127.0.0.1:8888/actuator/health                 # UP
Invoke-RestMethod http://127.0.0.1:8888/product-service/default         # propertySources : file:/config-repo/product-service.yml
Invoke-RestMethod http://127.0.0.1:8888/application/default
```

Preuve de chargement côté client (logs) :

```powershell
docker compose -f docker-compose.ecom.yml logs product-service | Select-String "Fetching config|Located environment"
```

## 3bis. Authentification OIDC (Keycloak)

Keycloak 26.1 (`start-dev`) porte le realm **`pfeecom`**, importé de façon
reproductible depuis `keycloak/realm-pfeecom.json` (monté en lecture seule).

- **URL publique** : `http://localhost:8181` ; découverte :
  `http://localhost:8181/realms/pfeecom/.well-known/openid-configuration`.
- **Clients** :
  - `pfeecom-frontend` (public, **PKCE S256**, redirect `http://localhost:5173/*`,
    web origin `http://localhost:5173`, direct access grant activé **pour les tests**).
  - `pfeecom-swagger` (public, **PKCE S256**, `directAccessGrantsEnabled=false`,
    **aucun secret**) — utilisé par la **Swagger UI agrégée** (`http://localhost:8090/swagger-ui.html`) ;
    redirect `http://localhost:8081-8090/*`.
  - `pfeecom-api` (bearer-only) — audience attendue dans les jetons.
  - `pfeecom-provisioner` (confidentiel, *service account* + rôle
    `realm-management/manage-users`) — utilisé **côté serveur uniquement** par
    `customer-service` pour matérialiser le claim `customer_id` d'un nouvel
    utilisateur. Secret via `PROVISIONER_CLIENT_SECRET` (jamais exposé au
    navigateur) ; création idempotente par `keycloak/scripts/setup-provisioner-client.ps1`.
- **Rôles realm** : `ADMIN`, `CUSTOMER` (ce dernier ajouté au **rôle par défaut** :
  tout nouvel inscrit l'obtient). **Claim** `customer_id` (attribut utilisateur)
  relie l'utilisateur au client métier.
- **Thème** : habillage personnalisé **`pfeecom`** (`keycloak/themes/pfeecom`,
  parent `keycloak.v2`) monté en lecture seule dans le conteneur ; premium
  noir/or, **français par défaut** (sélecteur FR/EN). **Inscription ouverte**
  (`registrationAllowed=true`).
- **Console d'administration** : `http://localhost:8181/admin` (`admin` / `admin`).
- **Utilisateurs de démo** (jamais des secrets de production) :

  | utilisateur | mot de passe | rôle | customer_id |
  |---|---|---|---|
  | `admin` | `admin` | ADMIN | — |
  | `yassine` | `yassine` | CUSTOMER | 1 |
  | `sarah` | `sarah` | CUSTOMER | 2 |
  | `chouaib` (`raichychouaib@gmail.com`) | *(compte propriétaire)* | ADMIN | — |

  Le **compte propriétaire** (`raichychouaib@gmail.com`) reçoit le rôle realm
  `ADMIN` via le script idempotent `keycloak/scripts/assign-owner-admin.ps1`
  (résolution par **email exact**, unicité vérifiée, aucun mot de passe ni
  attribut modifié). Le realm JSON (`realm-pfeecom.json`) n'étant appliqué qu'à la
  création du realm, le script applique le rôle à un realm **déjà en service**
  (l'attribution est donc « live » ; rejouable après un import à froid).

- **Identité** : l'identifiant client provient **toujours** du jeton (`customer_id`),
  jamais du navigateur. Mot de passe bootstrap admin via env `KEYCLOAK_ADMIN` /
  `KEYCLOAK_ADMIN_PASSWORD` (défaut `admin`/`admin`).

**Validation côté services** : les resource servers construisent un décodeur JWT
explicite — JWKS récupéré sur le **réseau interne** (`http://keycloak:8080/realms/pfeecom/protocol/openid-connect/certs`)
et émetteur attendu = URL publique (`http://localhost:8181/realms/pfeecom`). Le
décodeur est **paresseux** : aucun service ne dépend de Keycloak au démarrage.

### Matrice d'autorisation (appliquée par le gateway)

| Route | Accès |
|---|---|
| `GET /api/products/**`, `GET /api/categories/**`, `/actuator/health\|info`, `OPTIONS` | **public** |
| `GET /api/customers` (listing) | **ADMIN** |
| `POST /api/customers/me` (provisionnement) | **authentifié** (règle placée **avant** le *catch-all* ADMIN ci-dessous) |
| `POST/PUT/PATCH/DELETE /api/products\|categories\|customers/**` | **ADMIN** |
| paniers, commandes, `GET /api/customers/{id}` | **authentifié** (+ propriété cart/order ; fiche client = propriétaire ou ADMIN) |

Propriété : cart exige `customerId` du chemin == `customer_id` du jeton ; order exige
`customerId` du corps == `customer_id` (création), et la liste/fiche sont cloisonnées
par client. ADMIN outrepasse. Violation → **403** `{"code":"FORBIDDEN"}` ; jeton absent → **401**.

### Obtenir un jeton (tests)

```powershell
# Grant « password » (usage test uniquement, client frontend)
$body = @{ grant_type='password'; client_id='pfeecom-frontend'; username='yassine'; password='yassine'; scope='openid' }
$tok = (Invoke-RestMethod -Method Post -Uri 'http://localhost:8181/realms/pfeecom/protocol/openid-connect/token' -Body $body).access_token
Invoke-RestMethod -Uri 'http://localhost:8090/api/orders' -Headers @{ Authorization = "Bearer $tok" }
```

En navigateur, le frontend utilise **Authorization Code + PKCE** via `keycloak-js`
(redirection vers Keycloak au chargement, jeton Bearer sur chaque appel, `401` →
rafraîchissement → nouvel essai). ADMIN garde le sélecteur de client ; un CUSTOMER
est automatiquement lié à son `customer_id` (le listing clients ne lui est pas accessible).

## 3ter. Provisionnement automatique du client (nouveaux inscrits)

Un compte Keycloak créé par **auto-inscription** n'a **pas** de profil métier.
À la première connexion, le frontend appelle `POST /api/customers/me` ; le
`customer-service` crée le profil **côté serveur**.

- **Identité** : clé sur le `sub` **immuable** du jeton Keycloak, jamais un
  identifiant fourni par le navigateur. Colonne `keycloak_sub` (UNIQUE, nullable).
- **Idempotent** : un `sub` déjà provisionné renvoie le même profil ; l'email est
  normalisé ; un rejeu ne crée pas de doublon.
- **Comptes existants préservés** : si le jeton porte déjà un claim `customer_id`
  (utilisateur de démo ou profil pré-créé par un admin), le profil existant est
  **rattaché** au `sub` au lieu d'être recréé — l'`id` est conservé.
- **Claim** `customer_id` matérialisé via le client `pfeecom-provisioner`
  (**côté serveur** ; aucun identifiant privilégié dans le navigateur).
- **Observabilité / erreurs** : pas de PII journalisée ; `401` (jeton absent),
  `400 INVALID_PROFILE` (email manquant), `503 PROVISIONING_UNAVAILABLE`
  (Keycloak injoignable — réessayable).

### Mise en place (une fois, après démarrage de Keycloak)

```powershell
# Crée (idempotent) le client pfeecom-provisioner + service account manage-users
# et active l'attribut libre customer_id (unmanagedAttributePolicy=ADMIN_EDIT).
pwsh -File keycloak/scripts/setup-provisioner-client.ps1
```

> **Keycloak 26 — attributs libres** : le *user profile* déclaratif n'autorise par
> défaut **aucun** attribut « non managé » ; sans `unmanagedAttributePolicy=ADMIN_EDIT`,
> l'écriture de `customer_id` par l'Admin API réussit (HTTP 200) mais est
> **silencieusement ignorée**. Le script et `keycloak/realm-pfeecom.json` positionnent
> cette politique. L'**import JSON** du realm ne s'applique que sur un realm **neuf** ;
> sur un realm existant, appliquer le script.

```powershell
# Vérification : un nouveau compte obtient son profil puis son claim.
Invoke-RestMethod -Method Post -Uri 'http://localhost:8090/api/customers/me' -Headers @{ Authorization = "Bearer $tokNouveau" }
```

## 4. Démarrage local (JARs, sans Docker)

PowerShell 5.1 (pas de `&&` dans ce shell). Prérequis : un **MongoDB local** sur `localhost:27017` (cart/order) **et** un **PostgreSQL local** sur `localhost:5432` (product/customer).

Ordre : **config-server** → discovery → services → gateway.

```powershell
# 1) config-server (depot local)
$env:CONFIG_REPO_LOCATION = "file:../../config-repo"
mvn -B -f services/config-server spring-boot:run

# 2) Dans d'autres fenetres, avec le profil local + l'URL du config-server :
$env:CONFIG_SERVER_IMPORT = "configserver:http://localhost:8888"
$env:SPRING_PROFILES_ACTIVE = "local"
mvn -B -f services/discovery-service spring-boot:run
mvn -B -f services/product-service   spring-boot:run
mvn -B -f services/customer-service  spring-boot:run
mvn -B -f services/cart-service      spring-boot:run
mvn -B -f services/order-service     spring-boot:run
mvn -B -f services/api-gateway       spring-boot:run
```

- Profil `local` : product/customer sur PostgreSQL `pfe_products_local`/`pfe_customers_local` (`localhost:5432`), cart/order sur MongoDB `pfe_carts_local`/`pfe_orders_local` (`localhost:27017`), Eureka `localhost:8761`, gateway sur 8090.
- **Auth en local** : démarrer Keycloak via Docker (`docker compose -f docker-compose.ecom.yml up -d keycloak`) ; le profil `local` pointe le JWKS sur `http://localhost:8181/...` et l'émetteur attendu reste `http://localhost:8181/realms/pfeecom`.
- L'ordre de démarrage compte : les appels inter-services (`http://customer-service`, `http://product-service`) résolus via Eureka exigent que les 4 services soient enregistrés.

## 5. Démarrage Docker (persistance polyglotte : PostgreSQL + MongoDB)

```powershell
docker compose -f docker-compose.ecom.yml up -d --build
docker compose -f docker-compose.ecom.yml ps        # 16 containers healthy (+ minio-init, Exited 0)
```

- Config Server : http://127.0.0.1:8888
- Eureka : http://127.0.0.1:8761
- Gateway : http://127.0.0.1:8090
- Keycloak : http://127.0.0.1:8181 (realm `pfeecom`)
- Prometheus : http://127.0.0.1:9090
- Grafana : http://127.0.0.1:3000 (admin par défaut `admin`/`admin`, surchargeable par `GRAFANA_ADMIN_USER`/`GRAFANA_ADMIN_PASSWORD`)
- Elasticsearch : http://127.0.0.1:9200 (boucle locale uniquement — usage diagnostic/tests)
- Frontend (nginx) : http://127.0.0.1:5173 — **démo à ouvrir ici** (login Keycloak requis)
- Données persistées : PostgreSQL (volume `postgres-data`) pour product/customer, MongoDB (volume `mongo-data`) pour cart/order, Elasticsearch (volume `elasticsearch-data`, reconstructible).

### 5bis. Bases de test

Pour lancer les tests d'intégration (bases dédiées, isolées) :

```powershell
docker compose -f docker-compose.ecom.yml --profile test up -d mongo-test postgres-test   # mongo 27018 / postgres 5433
```

### 5ter. Développement frontend (Vite, dev server)

```powershell
cd frontend-ecom
npm install
npm run dev        # http://localhost:5173 — proxy /api -> 127.0.0.1:8090 (gateway)
npm run typecheck  # tsc --noEmit
npm run lint       # eslint
npm run build      # vite build -> dist/
```

> Le dev server Vite écoute `localhost` (peut être IPv6 `::1` uniquement) : accéder via `http://localhost:5173`, pas `127.0.0.1:5173`. En production (Docker), l'app est servie par nginx (`5173 → 80`) qui proxy `/api` vers `api-gateway:8080`.

## 6. Arrêt (préserve les données)

```powershell
docker compose -f docker-compose.ecom.yml down        # JAMAIS down -v
```

> Règle : **ne jamais** utiliser `down -v`, `volume prune`, `system prune`. Les volumes `postgres-data` et `mongo-data` doivent survivre.

## 7. Health checks & registre

```powershell
Invoke-RestMethod http://127.0.0.1:8888/actuator/health   # Config Server
Invoke-RestMethod http://127.0.0.1:8761/actuator/health   # Eureka
# Gateway : l'actuator est sur un port de gestion INTERNE (8081), non publie ->
# pas d'acces depuis l'hote. Verifier via le conteneur :
docker exec pfe-api-gateway-1 wget -qO- http://127.0.0.1:8081/actuator/health
# chaque service (local) : http://127.0.0.1:808{1,2,3,4}/actuator/health
# chaque service (Docker) : docker exec pfe-<service>-1 wget -qO- http://127.0.0.1:PORT/actuator/health
Invoke-RestMethod -Headers @{Accept="application/json"} http://127.0.0.1:8761/eureka/apps   # registre (5 apps UP)
Invoke-WebRequest http://127.0.0.1:9090/-/healthy          # Prometheus
Invoke-RestMethod http://127.0.0.1:3000/api/health          # Grafana
```

## 8. Exemples d'API vérifiés (via gateway 8090)

> Depuis l'ajout de Keycloak : les lectures catalogue sont **publiques**, tout le
> reste exige un jeton. Définir d'abord (voir §3bis) :
>
> ```powershell
> # Jeton ADMIN (écritures, listing clients)
> $admin = (Invoke-RestMethod -Method Post -Uri 'http://localhost:8181/realms/pfeecom/protocol/openid-connect/token' -Body @{ grant_type='password'; client_id='pfeecom-frontend'; username='admin'; password='admin'; scope='openid' }).access_token
> $auth = @{ Authorization = "Bearer $admin" }
> # Jeton client yassine (customer_id=1) : panier/commandes cloisonnés
> $cust = @{ Authorization = "Bearer $((Invoke-RestMethod -Method Post -Uri 'http://localhost:8181/realms/pfeecom/protocol/openid-connect/token' -Body @{ grant_type='password'; client_id='pfeecom-frontend'; username='yassine'; password='yassine'; scope='openid' }).access_token)" }
> ```

### Catalogue

```powershell
# Catégorie (ADMIN)
Invoke-WebRequest http://127.0.0.1:8090/api/categories -Method Post -ContentType application/json -Headers $auth `
  -Body '{"name":"Laptops","description":"Computers"}'          # 201
Invoke-WebRequest http://127.0.0.1:8090/api/categories          # 200 (public)

# Produit (ADMIN)
Invoke-WebRequest http://127.0.0.1:8090/api/products -Method Post -ContentType application/json -Headers $auth `
  -Body '{"name":"Dell XPS","price":1299.00,"stockQuantity":7,"active":true,"categoryId":1}'   # 201
Invoke-WebRequest "http://127.0.0.1:8090/api/products?page=0&size=10"   # 200 list paginée (public)
Invoke-WebRequest http://127.0.0.1:8090/api/products/1          # 200 (public)
```

### Recherche (Elasticsearch ; repli PostgreSQL marqué `degraded=true`)

```powershell
Invoke-RestMethod "http://127.0.0.1:8090/api/products/search?q=bluetooth"    # 200 public ; content[].highlights.name
Invoke-RestMethod "http://127.0.0.1:8090/api/products/suggest?q=char"        # 200 public ; autocompletion
Invoke-WebRequest http://127.0.0.1:8090/api/products/reindex -Method Post -Headers $auth   # 200 ADMIN ; {"indexed":N} ; sans jeton -> 401 ; jeton client -> 403
# ES arrete : search/suggest -> degraded=true (resultats PostgreSQL) ; reindex -> 503
Invoke-RestMethod 'http://127.0.0.1:9200/pfeecom-products/_count'            # diagnostic direct ES (hote)
```

### Images produit (MinIO ; upload/suppression ADMIN, lecture publique)

```powershell
# multipart : curl.exe (Invoke-WebRequest de PowerShell 5.1 ne gere pas -Form)
curl.exe -s -X POST -H "Authorization: Bearer $tokenAdmin" -F "file=@C:\img\photo.png;type=image/png" `
  http://127.0.0.1:8090/api/products/1/image            # 200 ; imageUrl=/api/products/1/image

Invoke-WebRequest http://127.0.0.1:8090/api/products/1/image                        # 200 public (octets + Content-Type)
curl.exe -s -o NUL -w "%{http_code}" http://127.0.0.1:8090/api/products/1/image      # 200
# sans jeton -> 401 ; jeton client -> 403 ; fichier invalide -> 400 INVALID_IMAGE ; >5 Mo -> 413 FILE_TOO_LARGE

Invoke-WebRequest http://127.0.0.1:8090/api/products/1/image -Method Delete -Headers $auth   # 204 (ADMIN)
```

### Client (ADMIN pour listing/écriture)

```powershell
Invoke-WebRequest http://127.0.0.1:8090/api/customers -Method Post -ContentType application/json -Headers $auth `
  -Body '{"firstName":"Nadia","lastName":"Kabbaj","email":"nadia.kabbaj@demo.com","phone":"0600000000"}'  # 201
Invoke-WebRequest http://127.0.0.1:8090/api/customers -Headers $auth   # 200 listing (ADMIN) ; sans jeton -> 401 ; jeton client -> 403
Invoke-WebRequest http://127.0.0.1:8090/api/customers/1 -Headers $cust # 200 (fiche, authentifié)
# email dupliqué -> 409 {"code":"DUPLICATE_RESOURCE",...}
```

### Panier (appel inter-service vers product-service ; jeton du client propriétaire)

```powershell
Invoke-WebRequest http://127.0.0.1:8090/api/carts/1/items -Method Post -ContentType application/json -Headers $cust `
  -Body '{"productId":1,"quantity":2}'    # 200 ; produit 1 + quantité ; panier client 1
Invoke-WebRequest http://127.0.0.1:8090/api/carts/1 -Headers $cust     # 200 ; items + total
Invoke-WebRequest http://127.0.0.1:8090/api/carts/1/items/1 -Method Delete -Headers $cust   # 200
Invoke-WebRequest http://127.0.0.1:8090/api/carts/1 -Method Delete -Headers $cust           # 204
Invoke-WebRequest http://127.0.0.1:8090/api/carts/2 -Headers $cust     # 403 FORBIDDEN (cross-customer)
# client inconnu / produit inconnu -> 404 {"code":"NOT_FOUND",...}
```

### Commande (appels inter-services vers customer + product ; jeton du client)

```powershell
Invoke-WebRequest http://127.0.0.1:8090/api/orders -Method Post -ContentType application/json -Headers $cust `
  -Body '{"customerId":1,"items":[{"productId":1,"quantity":2},{"productId":2,"quantity":1}]}'   # 201 ; total, CONFIRMED
Invoke-WebRequest http://127.0.0.1:8090/api/orders/1 -Headers $cust    # 200 (si la commande appartient au client 1, sinon 403)
Invoke-WebRequest http://127.0.0.1:8090/api/orders -Headers $cust      # 200 ; liste CLOISONNÉE par client
Invoke-WebRequest http://127.0.0.1:8090/api/orders -Headers $auth      # 200 ; vue ADMIN (toutes les commandes)
# client ou produit inconnu -> 404 {"code":"NOT_FOUND",...}
```

### Erreurs contrôlées communes

- Ressource inexistante -> **404** `{"code":"NOT_FOUND",...}`
- Validation (nom vide, quantité 0, email invalide) -> **400** `{"code":"VALIDATION_FAILED",...}`
- JSON malformé -> **400** `{"code":"MALFORMED_JSON",...}`
- Email client dupliqué -> **409** `{"code":"DUPLICATE_RESOURCE",...}`

OpenAPI par service : `http://127.0.0.1:808X/swagger-ui.html` (local) ou via `docker exec` (Docker, ports non exposés).

## 8bis. Documentation OpenAPI / Swagger (agrégée)

Le gateway agrège les 4 spécifications dans une **Swagger UI** unique. Détails :
`docs/SWAGGER-OPENAPI.md`.

```powershell
# Swagger UI (navigateur) : http://127.0.0.1:8090/swagger-ui.html  (302 -> /webjars/swagger-ui/index.html)
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8090/swagger-ui.html            # 302
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8090/webjars/swagger-ui/index.html  # 200 HTML
Invoke-RestMethod http://127.0.0.1:8090/v3/api-docs/swagger-config                  # 200 ; liste des 4 specs
Invoke-RestMethod http://127.0.0.1:8090/api-docs/product-service                    # 200 ; OpenAPI 3.0.1 (bearerAuth + oauth2)
Invoke-RestMethod http://127.0.0.1:8090/api-docs/customer-service
Invoke-RestMethod http://127.0.0.1:8090/api-docs/cart-service
Invoke-RestMethod http://127.0.0.1:8090/api-docs/order-service
```

- **Auth dans Swagger UI** : bouton **Authorize** → soit coller un `access_token`
  (schéma `bearerAuth`), soit le flux **oauth2 Authorization Code + PKCE** via le
  client public `pfeecom-swagger` (login Keycloak interactif).
- La doc est **publique**, mais les opérations métier restent protégées : les
  appels « Try it out » exigent un jeton valide (401/403 sinon). `/actuator/**`
  n'est **jamais** exposé via Swagger.

## 9. Stratégie base de données (polyglotte)

| Service | Base | Mode | Persistance | Usage |
|---|---|---|---|---|
| product-service | PostgreSQL `pfe_products` | Docker | oui (volume `postgres-data`) | **démo finale** |
| customer-service | PostgreSQL `pfe_customers` | Docker | oui (volume `postgres-data`) | **démo finale** |
| cart-service | MongoDB `pfe_carts` | Docker | oui (volume `mongo-data`) | **démo finale** |
| order-service | MongoDB `pfe_orders` | Docker | oui (volume `mongo-data`) | **démo finale** |
| product/customer (local) | PostgreSQL `localhost:5432`, `pfe_products_local`/`pfe_customers_local` | profil `local` | disque local | dev |
| cart/order (local) | MongoDB `localhost:27017`, `pfe_carts_local`/`pfe_orders_local` | profil `local` | disque local | dev |
| tests product/customer | PostgreSQL `localhost:5433`, `pfe_products_test`/`pfe_customers_test` | tests | isolé | tests d'intégration |
| tests cart/order | MongoDB `localhost:27018`, `pfe_carts_test`/`pfe_orders_test` | tests | isolé | tests d'intégration |

- **Choix polyglotte** : `product`/`customer` sont **relationnels** (intégrité référentielle, unicité,
  contraintes CHECK, transactions ACID) → **PostgreSQL** ; `cart`/`order` sont des **agrégats
  documentaires** (snapshot produit, panier hétérogène, commande immuable) → **MongoDB**. Justification
  complète : `docs/POLYGLOT_PERSISTENCE_DECISION.md`.
- **Schéma & données PostgreSQL** : gérés par **Flyway** (`V1__init_schema.sql`, `V2__seed_data.sql`),
  `jpa.hibernate.ddl-auto=validate` (aucun DDL Hibernate). Contraintes : `UNIQUE(categories.name)`,
  `UNIQUE(customers.email_key)`, `CHECK(price > 0)`, `CHECK(stock_quantity >= 0)`,
  FK `products.category_id → categories.id`.
- **Identifiants** : PostgreSQL via séquences (`product_seq`, `customer_seq`, `category_seq`) ;
  MongoDB via compteur atomique (collection `sequences`).
- **Cart/order** : schéma et données MongoDB **inchangés** par la migration polyglotte.
- Les anciennes bases Mongo `pfe_products`/`pfe_customers` sont conservées **gelées** (rollback =
  restaurer l'URI Mongo dans `config-repo/`) ; purge des données **interdite** sans accord explicite.

## 10. Observabilité (Prometheus + Grafana)

Chaque service Spring Boot expose ses métriques Micrometer au format Prometheus sur
`/actuator/prometheus`. **Prometheus** (`prom/prometheus:v2.54.1`, 9090) scrape les 7
cibles sur le DNS interne `pfe-net` ; **Grafana** (`grafana/grafana:11.2.0`, 3000)
consomme Prometheus via une datasource et des dashboards **provisionnés** (aucune
création manuelle). Fichiers versionnés sous `monitoring/`.

### Cibles de scrape (job `pfe-ecom`, `metrics_path=/actuator/prometheus`, 10s)

| Cible interne | Service | Actuator |
|---|---|---|
| `config-server:8888` | Config Server | public |
| `discovery:8761` | Eureka | public |
| `product-service:8081` | Product | public |
| `customer-service:8082` | Customer | public |
| `cart-service:8083` | Cart | `permitAll` interne |
| `order-service:8084` | Order | `permitAll` interne |
| `api-gateway:8081` | Gateway | **port de gestion interne** (non publié) |

> Sécurité : aucune protection n'a été désactivée. cart/order ne sont pas publiés sur
> l'hôte, leur `/actuator/prometheus` n'est donc joignable qu'en interne. Le gateway
> expose son actuator sur un port de gestion distinct (8081) **non publié** ; le port
> public 8090 ne sert **aucun** endpoint d'actuator (`/actuator/**` → refusé/404) et les
> routes génériques `/<service-id>/actuator/**` sont explicitement refusées.

### Inspecter cibles et métriques

```powershell
# Cibles et santé (health=up pour les 7)
(Invoke-RestMethod http://127.0.0.1:9090/api/v1/targets).data.activeTargets |
  Select-Object @{n='instance';e={$_.labels.instance}}, health

# Requêtes PromQL (exemples réellement utilisés par les dashboards)
$q = 'sum by (application) (rate(http_server_requests_seconds_count{job="pfe-ecom"}[5m]))'
(Invoke-RestMethod "http://127.0.0.1:9090/api/v1/query?query=$([uri]::EscapeDataString($q))").data.result

# Métriques brutes d'un service (interne)
docker exec pfe-cart-service-1 wget -qO- http://127.0.0.1:8083/actuator/prometheus
```

### Grafana

- URL : http://127.0.0.1:3000 — identifiants `admin` / `admin` (surchargeables par
  `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`, jamais de secret en dur).
- Datasource provisionnée : **Prometheus** (`uid=prometheus`, `http://prometheus:9090`).
- Dashboard provisionné : **PFE E-commerce - Vue d'ensemble** (`uid=pfe-ecom-overview`,
  dossier `PFE`), 11 panneaux : cibles UP/DOWN, état des cibles, débit HTTP, taux
  d'erreurs (4xx+5xx), latence p95, heap/non-heap JVM, CPU processus, uptime, durée de scrape.
- Provisioning : `monitoring/grafana/provisioning/` (datasource + provider de dashboards),
  dashboards JSON dans `monitoring/grafana/dashboards/`. Conservés par le volume `grafana-data`.

### Config centrale des métriques

`config-repo/application.yml` centralise l'exposition (`health,info,prometheus`),
`management.metrics.tags.application=${spring.application.name}`, l'histogramme
`http.server.requests` (pour la latence p95) et l'export Prometheus.

### Troubleshooting

- **Cible DOWN** : vérifier que le service est `healthy` (`docker compose ps`) puis
  `docker exec pfe-prometheus-1 wget -qO- http://127.0.0.1:9090/api/v1/targets`.
- **401 sur une cible** : l'endpoint n'est pas exposé (sécurité) — cart/order/gateway
  doivent autoriser `/actuator/prometheus` en interne (voir ci-dessus).
- **Dashboard vide** : confirmer la datasource (`/api/datasources/uid/prometheus/health`)
  et qu'une série existe pour la requête (`/api/v1/query`).
- **Recharger la config Prometheus** (image avec `--web.enable-lifecycle`) :
  `docker exec pfe-prometheus-1 kill -HUP 1` ou `docker compose restart prometheus`.

## 11. Tests automatisés

```powershell
# prérequis : bases de test démarrées
docker compose -f docker-compose.ecom.yml --profile test up -d mongo-test postgres-test

cd services/product-service  ; mvn test   # 52 tests (22 MockMvc DB/repli + 8 images + 6 Elasticsearch + 8 stockage MinIO + 1 OpenAPI + 5 cache Redis + 2 panne Redis)
cd services/customer-service ; mvn test   # 17 tests (9 intégration MockMvc + 1 OpenAPI + 7 provisionnement unitaire)
cd services/cart-service     ; mvn test   # 19 tests (12 métier dont fusion panier + 6 sécurité dont propriété fusion : 401/403 + 1 OpenAPI)
cd services/order-service    ; mvn test   # 17 tests (11 métier dont paiement à la livraison : créé PENDING jamais payé + horodatage createdAt persisté + 5 sécurité : 401/403/cloisonnement + 1 OpenAPI)
cd services/api-gateway      ; mvn test   # 30 tests (matrice + propriété fiche client + provisionnement POST /customers/me + 5 OpenAPI)
```

Total : **135 tests verts** (dont 36 de sécurité rôle/propriété + 5 de sécurité des endpoints de documentation + 3 de contraintes base). Les tests de cache Redis exigent l'instance de test (`docker compose -f docker-compose.ecom.yml --profile test up -d redis-test`, port hôte `6380`) ; sans elle, `ProductCacheRedisTest` est **ignoré** (`Assumptions.assumeTrue`) et `ProductCacheOutageTest` valide le repli. Config client et Eureka désactivés dans les tests (`spring.cloud.config.enabled=false`, `eureka.client.enabled=false`) ; `app.security.*` factices dans `src/test/resources/application.yml` (décodeur paresseux, aucun appel réseau) ; **PostgreSQL de test sur `localhost:5433` (product/customer), MongoDB de test sur `localhost:27018` (cart/order)**, définis dans `src/test/resources/application.yml`. Les tests métier cart/order s'exécutent avec `@WithMockUser(roles="ADMIN")` pour ignorer le contrôle de propriété (couvert séparément).

> Les **6 tests Elasticsearch** (`ProductSearchElasticsearchTest`) exigent un ES
> joignable (`localhost:9200`) ; sinon ils sont **ignorés** (`Assumptions.assumeTrue`),
> jamais présentés comme réussis. Les 22 autres tests `product-service` couvrent le
> **repli PostgreSQL** (recherche hors inactifs, filtre catégorie, `size` borné,
> suggestions, outbox idempotent).
>
> Les **8 tests stockage** (`MinioStorageServiceTest`) s'exécutent sans MinIO
> (client simulé) ; les **8 tests images** (`ProductImageIntegrationTest`) utilisent
> un `StorageService` simulé + le PostgreSQL de test — aucune dépendance à MinIO.
> `app.storage.enabled=false` en test.

## 12. Séquence de démo 10–15 min (vérifiée)

1. Lancer `docker compose -f docker-compose.ecom.yml up -d` ; attendre `docker compose ps` = 16 services healthy (+ `minio-init` en `Exited (0)`), Redis inclus.
2. Config Server : `http://127.0.0.1:8888/product-service/default` → propertySources `config-repo/`.
3. Eureka : montrer `http://127.0.0.1:8761` → 5 applications (API-GATEWAY + 4 services) UP.
4. Afficher la config d'actuator via le conteneur (`docker exec pfe-api-gateway-1 wget -qO- http://127.0.0.1:8081/actuator/health`) — le port public 8090 n'expose pas l'actuator.
5. **Auth** : ouvrir Keycloak `http://127.0.0.1:8181` (realm `pfeecom`), montrer les rôles/users ; se connecter à la SPA comme `admin`/`admin` puis `yassine`/`yassine`.
6. Ouvrir la **SPA** `http://127.0.0.1:5173` (frontend-ecom via nginx) — un **visiteur non connecté** peut parcourir le catalogue et remplir un **panier invité** (barre de navigation avec `Connexion` / `Inscription`). Se connecter comme `yassine`/`yassine` : la sélection invitée est **fusionnée** avec le panier serveur (message de confirmation), puis commander ; la liste est cloisonnée. Montrer aussi un **nouvel inscrit** : `Inscription` → profil créé automatiquement (`POST /api/customers/me`, claim `customer_id` matérialisé), puis commande possible. Montrer aussi `admin`/`admin` → `/admin_panel`.
7. Sécurité via gateway : `GET /api/customers` sans jeton → 401, avec jeton client → 403, avec jeton admin → 200 ; `GET /api/carts/2` avec jeton yassine → 403 (cross-customer) ; `POST /api/carts/2/merge` avec jeton yassine → 403 (fusion idempotente, propriété contrôlée).
7bis. **Paiement à la livraison (COD)** : au checkout, montrer la mention « Paiement à la livraison — الدفع عند الاستلام » ; commander → réponse `201` avec `paymentMethod=CASH_ON_DELIVERY`, `paymentStatus=PENDING` (jamais `PAID`). En API : `POST /api/orders` avec `paymentMethod` absent → 400 `VALIDATION_FAILED`, valeur inconnue (`CREDIT_CARD`) → 400 `MALFORMED_JSON`, jeton d'un autre client → 403. Détail commande + console admin affichent le mode et le statut de paiement.
8. Erreurs contrôlées : 404, 400 validation, 400 JSON malformé, 409 email dupliqué.
8bis. **Recherche Elasticsearch** : dans la SPA, taper « blue » (autocomplétion) puis ouvrir `/search?q=bluetooth` (résultat + surlignage). En API : `POST /api/products/reindex` (ADMIN) → `{"indexed":3}` ; `GET /api/products/search?q=bluetooth` (public). Optionnel : arrêter ES (`docker compose stop elasticsearch`) et remontrer la recherche → `degraded=true`, la boutique reste fonctionnelle (repli PostgreSQL).
8ter. **Images produit (MinIO)** : en ADMIN, téléverser une image depuis l'espace d'administration → aperçu + affichage en boutique (`GET /api/products/{id}/image`). Montrer les rejets : sans jeton → 401, jeton client → 403, fichier invalide → 400, >5 Mo → 413. Montrer que `docker restart pfe-minio-1` conserve l'image (volume `minio-data`).
8quater. **Documentation OpenAPI** : ouvrir `http://localhost:8090/swagger-ui.html` → Swagger UI agrégée ; sélectionner les 4 services (specs `/api-docs/<service>`) ; montrer un **Authorize** `bearerAuth` puis un « Try it out » réussi sur `GET /api/products` (public) et refusé (401) sur `GET /api/carts/1` sans jeton.
9. **Observabilité** : Prometheus `http://127.0.0.1:9090/targets` (7 UP) ; Grafana `http://127.0.0.1:3000` → dashboard « PFE E-commerce - Vue d'ensemble » ; générer du trafic via le gateway et montrer l'évolution du débit HTTP et de la latence p95.
10. Redémarrage du stack (`down` puis `up`) : montrer que les données persistent (volumes `postgres-data` + `mongo-data`) ainsi que les dashboards (volume `grafana-data`).
11. Tests automatisés : `mvn test` sur les 5 services (135 tests verts) + `npm run typecheck/lint/test/build` sur `frontend-ecom`.
12. Architecture : diagramme section 1 ; insister sur Config centralisée, Eureka, gateway **frontière de confiance** + matrice, resource servers cart/order (propriété), appels inter-services, **persistance polyglotte** (PostgreSQL pour product/customer, MongoDB pour cart/order), **recherche Elasticsearch** (outbox + repli dégradé), **stockage d'images MinIO/S3** (moindre privilège), **observabilité Prometheus/Grafana**.

> Hors périmètre actuel : validité juridique/RGPD.

## 13. Cache Redis, panier invité & espace admin

### 13.1 Cache Redis (product-service)
- Service `redis` (interne `pfe-net`, aucune publication d'hôte) + volume `redis-data` ;
  `redis-test` (profil `test`, hôte `6380`) pour les tests.
- Cache-aside `products` / `product` / `categories`, TTL **5 min** ; éviction à
  chaque écriture produit/catégorie.
- **Repli :** toute erreur Redis est journalisée puis ignorée (`CacheErrorHandler`) —
  une panne du cache n'interrompt jamais une requête métier (cf. `ProductCacheOutageTest`).
- Clés observées : `products::0-5`, `categories::all`.

### 13.2 Panier invité et fusion
- Un visiteur non authentifié remplit un panier **local** (`localStorage`,
  schéma validé, jamais de donnée sensible).
- Après connexion, la sélection est fusionnée via
  `POST /api/carts/{customerId}/merge` : **authentifié + propriété contrôlée**,
  **idempotent** (quantité = `max(existant, entrant)`). Le panier invité n'est vidé
  qu'après la réponse de succès.
- `POST /api/carts/{customerId}/merge` — 401 sans jeton, 403 pour le panier d'un
  autre client, 200 sinon ; rejouer la requête ne duplique aucune ligne.

### 13.3 Espace d'administration
- Route SPA `/admin_panel` (`/admin` redirige) ; garde `RequireAdmin` côté client
  **et** RBAC ADMIN côté gateway (403 pour un client sur les écritures).

### 13.4 Fichier statique SSO
- `frontend-ecom/public/silent-check-sso.html` est servi tel quel (`/silent-check-sso.html`)
  et utilisé par `keycloak-js` (`silentCheckSsoRedirectUri`) pour détecter une
  session existante sans iframe tierce.

### 13.5 Provisionnement client (nouveaux inscrits)
- `POST /api/customers/me` (authentifié) crée le profil métier **clé sur le `sub`**
  Keycloak, **idempotent**, et matérialise le claim `customer_id` via le client
  serveur `pfeecom-provisioner`. Les comptes portant déjà un `customer_id` sont
  **rattachés**, jamais recréés (l'`id` est préservé). Détails et mise en place :
  §3ter.
