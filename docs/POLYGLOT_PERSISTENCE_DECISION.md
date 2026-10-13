# Décision de persistance polyglotte — PostgreSQL + MongoDB

**Statut : IMPLÉMENTÉE et VÉRIFIÉE (Phase C + Phase F, 11/10/2026).** Ce document reste le
livrable de la Phase B (proposition) ; il est conservé tel quel. L'allocation proposée a été
**validée** puis **implémentée** : `product-service` et `customer-service` sont passés sur
PostgreSQL (Spring Data JPA + Flyway), `cart-service` et `order-service` sont restés sur
MongoDB. Vérifications : **52/52 tests verts**, 13 conteneurs sains, données relues après
`down`/`up`, données MongoDB historiques préservées. Les écarts d'implémentation par rapport à
la proposition sont consignés en **section 11 (Bilan d'implémentation)**.

- Projet : plateforme e-commerce microservices (PFE FSAC), racine `D:\PROJECTS\PFE`
- Date : 11/10/2026
- Portée : allocation de la persistance par service métier (product, customer, cart, order)

---

## 0. Résumé exécutif

Aujourd'hui, **les quatre services métier utilisent MongoDB**. L'inspection du code réel
montre que le domaine recouvre en fait **deux types de données bien distincts** :

1. **Données de référence / d'identité** (`product-service` : produits + catégories ;
   `customer-service` : clients) — normalisées, soumises à des **contraintes d'intégrité et
   d'unicité** (nom de catégorie unique, email unique, `product → category`, prix > 0).
2. **Agrégats transactionnels à lignes embarquées** (`cart-service` : panier + lignes ;
   `order-service` : commande + lignes + instantané client) — lus/écrits **en bloc**, avec
   **dénormalisation volontaire** (snapshots de nom/prix).

**Allocation recommandée (plus petite architecture cohérente démontrant les deux SGBD) :**

| Service | SGBD | Nature |
|---|---|---|
| `customer-service` | **PostgreSQL** | Référentiel d'identité, unicité email, intégrité |
| `product-service` | **PostgreSQL** | Référentiel catalogue, FK produit→catégorie, prix `NUMERIC` |
| `cart-service` | **MongoDB** (inchangé) | Agrégat document, lignes embarquées, lecture/écriture en bloc |
| `order-service` | **MongoDB** (inchangé) | Agrégat immuable (snapshot), lignes embarquées |

Cette répartition **n'est pas « un SGBD par service »** : elle regroupe les deux services de
référence côté PostgreSQL et conserve les deux agrégats documentaires côté MongoDB. Elle est
défendable sur le plan technique (section 2), migre **le minimum de services** (2 sur 4),
**préserve intégralement** les données MongoDB existantes (paniers, commandes, et même les
collections produits/clients historiques) et **ne modifie pas** les contrats d'API ni la
sécurité.

> Décision clé : **les transactions inter-bases ne sont pas atomiques** et ne sont pas
> requises ici. La cohérence est assurée par validation amont + snapshots (section 5), comme
> aujourd'hui (section 7 du runbook et section 12 du guide).

---

## 1. Inventaire de l'état actuel (preuves)

### 1.1 Structure et dépendances

- Pas de POM racine ; 7 modules Maven indépendants dans `services/`.
- Les 4 services métier embarquent `spring-boot-starter-data-mongodb`
  (`services/*/pom.xml`). **Aucun** `spring-boot-starter-data-jpa`, **aucun** driver
  PostgreSQL, **aucune** dépendance Flyway/Liquibase.
- `cart-service` et `order-service` embarquent en plus
  `spring-boot-starter-oauth2-resource-server` (+ `spring-security-test`).
- Aucun fichier `.sql` dans le dépôt ; aucun répertoire `db/migration`.
- Le seul usage historique de JPA/PostgreSQL est le **projet archivé** `archive/pfe-flow/`
  (monolithe `Project`/`Task`, `application.properties` avec
  `jdbc:postgresql://localhost:5432/pfe`). **Il ne fait pas partie de la pile e-commerce.**

### 1.2 Modèle de données actuel (MongoDB)

| Service | Collections | Ids | Index |
|---|---|---|---|
| product | `products`, `categories` | `Long` via collection `sequences` | `categories.name` unique |
| customer | `customers` | `Long` via `sequences` | `customers.emailKey` unique |
| cart | `carts` (lignes embarquées `CartItem`) | `Long` via `sequences` | `carts.customerId` unique |
| order | `orders` (lignes embarquées `OrderItem`) | `Long` via `sequences` | `orders.customerId`, `orders.createdAt` |

`SequenceGenerator.nextSeq(...)` (`findAndModify` avec `upsert`) émulé **en Mongo** ce qu'une
séquence SQL ferait nativement : des identifiants `Long` stables et triables.

### 1.3 Contraintes et règles métier réellement codées

| Règle | Où | Nature |
|---|---|---|
| Email client unique (insensible à la casse) | `CustomerServiceImpl` (`existsByEmailKey` + `DuplicateKeyException`) | Unicité **métier** |
| Nom de catégorie unique | `@Indexed(unique=true)` sur `Category.name` | Unicité |
| `product.categoryId` → `Category` existante | `ProductServiceImpl.applyCategory` (copie `categoryName`) | Référence **non contrainte par le SGBD** |
| Prix ≥ 0.01, stock ≥ 0 | `ProductRequest` (Bean Validation) | Contrainte **applicative** uniquement |
| `cart.customerId` unique ; `cart.productId`/`order.productId`/`order.customerId` → services distants | clients REST | Références **inter-services** |

### 1.4 Frontières d'accès et sécurité (à préserver)

- Aucune base n'est exposée au frontend ; seul le gateway est public ; les services métier ne
  sont pas publiés (`docker-compose.ecom.yml`).
- L'autorisation est **applicative** (gateway + `CurrentUser`), jamais déléguée à la base.
  **Aucune migration ne doit toucher** aux rôles, à la possession ni au JWT.

### 1.5 Données réellement présentes (instantané, lecture seule)

| Base | Contenu |
|---|---|
| `pfe_products` | `products` = 3, `categories` = 2, `sequences` = 2 |
| `pfe_customers` | `customers` = 2, `sequences` = 1 |
| `pfe_carts` | `carts` = 1, `sequences` = 1 |
| `pfe_orders` | `orders` = 5, `sequences` = 1 |

Volume `mongo-data` (à **conserver**, jamais supprimé). Base `mongo-test` (profil `test`,
port 27018) pour les tests d'intégration.

### 1.6 Faits d'architecture déterminants

- **Aucune requête relationnelle** aujourd'hui : pas de `JOIN`, pas de filtre ad-hoc, pas
  d'agrégation. Uniquement `findAll(page)` / `findById` / `findByCustomerId(page)`.
- **Dénormalisation** : panier et commande embarquent des **snapshots** (nom/prix produit ;
  nom/email client) — fidélité historique volontaire.
- **Identifiants `Long`** : le front et les URLs dépendent de ces ids (contrat à préserver).

---

## 2. Grille de comparaison par service

Notation : **++** très adapté, **+** adapté, **–** neutre/faible, **––** inadapté.

| Critère | product | customer | cart | order |
|---|---|---|---|---|
| Intégrité référentielle (FK) | **++** (product→category) | **+** (identité centrale) | – (réf. inter-services) | – (réf. inter-services) |
| Unicité DB | ++ (category.name) | **++** (email) | + (customerId) | – |
| Transactions (multi-lignes ACID) | + (lire catégorie + écrire produit) | + | – | – (écriture d'agrégat unique) |
| Requêtes / jointures | + (join catégorie pour `categoryName`) | – | **––** (jamais de jointure) | **––** |
| Modèle documentaire (agrégat) | – | – | **++** | **++** |
| Flexibilité de schéma | + | + | + | + |
| Indexation | + (name, categoryId) | + (emailKey) | **++** (customerId unique) | + (customerId, createdAt) |
| Complexité opérationnelle | + | + | + | + |
| Tests / maintenabilité | + | + | **++** (déjà en place) | **++** (déjà en place) |
| **Justification PFE** | SGBDR pour **référentiel contraint** | SGBDR pour **identité unique** | NoSQL pour **agrégat lu en bloc** | NoSQL pour **agrégat immuable snapshot** |
| **Verdict** | **PostgreSQL** | **PostgreSQL** | **MongoDB** | **MongoDB** |

### 2.1 Détail par service

**product-service → PostgreSQL.** Le couple produit/catégorie est une relation 1-N classique
avec une **clé étrangère réelle** et une **unicité** (`categories.name`). Les montants gagnent
à être des `NUMERIC(12,2)` (précision exacte, vérifiable côté SGBD) et la création/édition
d'un produit lit la catégorie puis écrit le produit : une **transaction locale** + FK
`ON DELETE RESTRICT` protègent mieux que la copie applicative actuelle. La dénormalisation
`categoryName` sera **conservée dans le contrat** (`ProductResponse`), alimentée par jointure.

**customer-service → PostgreSQL.** Le client est le **référentiel d'identité** ; la règle
« email unique » est aujourd'hui émulée (`emailKey` + index). PostgreSQL exprime cela
nativement (index unique sur `email_key`, `NOT NULL`, tailles de colonnes). C'est le cas
d'école du passage d'une contrainte applicative à une **contrainte de schéma**.

**cart-service → MongoDB (conservé).** Le panier est **un agrégat** : `customerId` unique +
lignes embarquées, toujours lu/écrit **en entier** (`findByCustomerId` → `save`). Aucune
jointure, aucun besoin de transaction multi-documents. Le modèle document est ici le meilleur
choix, et la collection + son index existent déjà.

**order-service → MongoDB (conservé).** La commande est un **agrégat immuable** créé en un
coup avec un **instantané** client + lignes, puis lu seul ou par client (paginé). Les
références `customerId`/`productId` sont **inter-services** (aucune FK possible dans AUCUN
SGBD). L'écriture d'un agrégat unique est **atomique** en MongoDB. Migration sans gain
d'intégrité → risque inutile.

---

## 3. Allocation retenue et alternatives rejetées

### 3.1 Allocation retenue

| Service | SGBD | Base logique | Base de test |
|---|---|---|---|
| `customer-service` | PostgreSQL 16 | `pfe_customers` | `pfe_customers_test` |
| `product-service` | PostgreSQL 16 | `pfe_products` | `pfe_products_test` |
| `cart-service` | MongoDB 7 (inchangé) | `pfe_carts` | `pfe_carts_test` |
| `order-service` | MongoDB 7 (inchangé) | `pfe_orders` | `pfe_orders_test` |

Un **seul conteneur PostgreSQL** héberge les 2 bases (miroir exact du « un Mongo, 4 bases
logiques » actuel), chaque base étant **possédée par un rôle dédié** (section 4).

### 3.2 Alternatives rejetées

| Alternative | Pourquoi rejetée |
|---|---|
| **H1 : product, customer, order → PG ; cart → Mongo** (hypothèse de départ) | Migrer `order` n'apporte **aucune** intégrité supplémentaire : ses références (`customerId`, `productId`) sont **inter-services**, donc sans FK possible. L'agrégat est un *snapshot* immuable → modèle document. Coût : migration de données (5 commandes) + réécriture de 12 tests pour un gain nul. |
| **Tout PostgreSQL** | Ne démontre pas la persistance polyglotte ; force un schéma relationnel sur des agrégats documentaires (panier/commande) ou des jointures inutiles. |
| **Tout MongoDB** | État actuel ; laisse des contraintes d'unicité/référence émulées applicativement au lieu d'être garanties par le SGBD. Aucun apport pédagogique. |
| **Un SGBD par service (4 technologies)** | Complexité opérationnelle disproportionnée ; la mission demande la **plus petite** architecture cohérente. |
| **customer seul en PG** | Encore plus petit, mais perd la démonstration **la plus forte** (FK produit→catégorie + jointure pour `categoryName`). On garde product+customer comme **palier « référentiel »** cohérent. |

### 3.3 Portée de la migration

- **2 services migrés** (product, customer) : dépendances Maven, config, entités
  JPA/repositories, migrations Flyway, données.
- **2 services non touchés** (cart, order) : code, tests, données Mongo intacts.
- **Mapping id direct** : les ids `Long` existants sont **conservés** dans PostgreSQL
  (insertion d'ids explicites puis alignement des séquences) pour préserver URLs et
  références.

---

## 4. Frontières de propriété des données

- **Une base par service** ; aucun service n'accède à la base d'un autre (comme aujourd'hui).
- Côté PostgreSQL : **un rôle = une base** :
  - rôle `product_svc` propriétaire de `pfe_products` ;
  - rôle `customer_svc` propriétaire de `pfe_customers` ;
  - aucun `GRANT` croisé ; le super-utilisateur d'init ne sert qu'à créer bases/rôles.
- Côté MongoDB : inchangé (`pfe_carts`, `pfe_orders` restent la propriété unique de
  cart/order ; les collections `pfe_products`/`pfe_customers` historiques sont **conservées
  mais gelées** — jamais supprimées).
- **Aucune jointure inter-services** : les données distantes restent obtenues **par API**
  (`ProductServiceClient`, `CustomerServiceClient`) ou figées en **snapshot**.
- Le **catalogue reste lecture publique** via le gateway ; la migration ne change **pas**
  ces frontières.

---

## 5. Stratégie de transaction et de cohérence

- **Transactions locales uniquement.** Chaque service gère ses écritures dans **une**
  transaction sur **sa** base. `product` : lecture catégorie + écriture produit dans une
  transaction (la FK garantit la validité). Aucune transaction **inter-bases** n'est requise.
- **Pas de transaction distribuée / 2PC / saga.** Motif : les opérations inter-services
  actuelles sont des **validations amont** (vérifier l'existence d'un client/produit) suivies
  d'une écriture **locale** figée en snapshot. Le système assume une **cohérence éventuelle**,
  déjà documentée (guide §12, runbook §9).
- **Comportement en panne** (à préserver) : si un service distant est indisponible, l'appel
  REST échoue proprement ; `cart`/`order` propagent une erreur structurée
  (`GlobalExceptionHandler` : 404/500) **sans écriture partielle** (`ResourceNotFoundException`,
  pas de produit « bricolé »).
- **Chemin critique préservé** : `POST /api/orders` reste **validé en amont** (client) puis
  écrit un agrégat unique **atomique** dans Mongo.
- **Unicité concurrente** : côté PostgreSQL, l'unicité email sera garantie **par contrainte**
  (et non par un `exists` applicatif seul) → l'exécution concurrente est sûre au niveau SGBD.
- Frontière assumée : `order.customerId`/`productId` et `cart.productId` restent des
  références **non contraintes par le SGBD** (elles visent d'autres services) ; l'intégrité
  est assurée par la validation à l'écriture. **Documenté, non corrigé** (pas de FK
  cross-service possible).

---

## 6. Indexation et unicité

### 6.1 PostgreSQL (nouveau)

| Table | Clé primaire | Contraintes / index | Vérification |
|---|---|---|---|
| `categories` | `id BIGINT` (séquence dédiée) | `name VARCHAR(100) NOT NULL UNIQUE` | Nom unique |
| `products` | `id BIGINT` (séquence dédiée) | `category_id BIGINT NULL REFERENCES categories(id) ON DELETE RESTRICT` ; `price NUMERIC(12,2) NOT NULL CHECK (price > 0)` ; `stock_quantity INT NOT NULL CHECK (stock_quantity >= 0)` ; index sur `category_id` | FK + CHECK |
| `customers` | `id BIGINT` (séquence dédiée) | `email_key VARCHAR(150) NOT NULL UNIQUE` ; `email VARCHAR(150) NOT NULL` ; `first_name/last_name VARCHAR(150) NOT NULL` | Email unique |

- **`categoryName`** dans `ProductResponse` est **reconstruit par jointure** (ou conservé en
  colonne lue) — le contrat REST est identique.
- **Séquences** : une séquence par table ; après l'insertion des données existantes, la
  séquence est repositionnée (`setval`) au `max(id)` pour éviter toute collision.

### 6.2 MongoDB (inchangé)

- `carts.customerId` **unique** ; `orders.customerId`, `orders.createdAt` indexés.
- Génération d'ids par `sequences` (conservée) ; aucun index « à créer pour l'exemple ».
- Les collections historiques `pfe_products`/`pfe_customers` restent en place (gelées) tant
  que la validation n'est pas faite et validée par l'utilisateur.

---

## 7. Plan de migration et de retour arrière (réversible, service par service)

> Aucune étape destructive ; sauvegarde d'abord, suppression **jamais** avant validation et
> accord explicite.

### 7.1 Préparation (avant tout)

1. **Export de sauvegarde** des bases concernées (lecture seule) :
   `docker exec pfe-mongo-1 mongosh --quiet --eval ...` → `dump/mongo_pre_pg_<date>/`
   (`products`, `categories`, `customers`, plus `sequences`).
2. **Instantané de l'état** : compteurs et champs critiques (section 1.5) consignés.
3. Ajout d'un service `postgres` (compose) : image `postgres:16-alpine` **épinglée**, volume
   nommé `postgres-data`, `healthcheck` `pg_isready`, init créant les 2 bases + 2 rôles.
   Mot de passe via **variable d'environnement** (jamais commité).

### 7.2 Migration par service (ordre : customer, puis product)

Pour **customer-service** :

1. Ajouter au POM `spring-boot-starter-data-jpa` + driver `postgresql` +
   `flyway-core` + `flyway-database-postgresql`.
2. `config-repo/customer-service.yml` : remplacer l'URI Mongo par `spring.datasource.*`
   (host/port/db non secrets) ; mot de passe via env ; `jpa.hibernate.ddl-auto=validate` ;
   `flyway.enabled=true`.
3. Écrire la migration Flyway `V1__schema.sql` (table `customers` + contrainte téléphone/email).
4. Convertir l'entité en entité JPA (`@Entity @Table`), le repository en
   `JpaRepository` + `findByEmailKey`/`existsByEmailKey` (contrat de requête identique).
5. **Charger les données réelles** : `V2__data.sql` **généré depuis l'export** (ids explicites,
   valeurs exactes), puis `SELECT setval('customers_id_seq', (SELECT max(id) FROM customers))`.
6. **Valider** : `SELECT count(*)` = 2 ; vérifier emails/noms ; test HTTP `GET /api/customers/{id}`,
   `POST` (409 sur email dupliqué).
7. **Conserver** `pfe_customers` dans Mongo (gelée) pour un retour arrière immédiat.

Puis **product-service** selon le même schéma (table `categories` + `products`, FK, jointure
`categoryName`), validation produits=3 / catégories=2.

### 7.3 Retour arrière (rollback)

- **Niveau service** : rétablir l'URI Mongo dans `config-repo/<svc>.yml` et la dépendance
  Mongo dans le POM (la branche précédente est conservée). La base Mongo source **n'a jamais
  été modifiée** → retour instantané.
- **Niveau données** : les bases Mongo restent la référence tant que la validation n'est pas
  explicitement approuvée.
- **Suppression des collections Mongo historiques** : **uniquement** après validation
  fonctionnelle ET accord explicite de l'utilisateur (hors de cette proposition).

### 7.4 Critères de succès (avant toute suppression)

Compteurs identiques source/destination ; contrôle des champs critiques ; 0 test en échec ;
gateway OK ; autorisation et possession inchangées ; redémarrage avec persistance.

---

## 8. Considérations de sécurité

- **Aucune base exposée** : PostgreSQL reste sur le réseau Docker `pfe-net`, **aucun port
  publié sur l'hôte** (contrairement à `mongo-test`, uniquement en profil `test`).
- **Secrets** : mot de passe PostgreSQL **uniquement** par variable d'environnement
  (`${POSTGRES_PASSWORD}`), jamais dans `config-repo/` ni dans le compose.
- **Principe du moindre privilège** : rôles PostgreSQL dédiés par base ; pas de compte
  applicatif super-utilisateur.
- **Sécurité applicative inchangée** : Keycloak/JWT, matrice gateway, contrôle de possession
  `CurrentUser` **intouchés**. La base **n'est jamais** un substitut à l'autorisation API.
- **Fail-fast** préservé : si la base est indisponible, le service échoue clairement
  (healthcheck DOWN) plutôt que de démarrer en mode dégradé silencieux.

---

## 9. Stratégie de tests

- **Baseline préservée : 49 tests** (product 10, customer 8, cart 13, order 12, gateway 6).
  Aucun test supprimé ni affaibli.
- **Testcontainers indisponible** dans cet environnement Windows/Docker (contrainte connue).
  Solution retenue, symétrique de `mongo-test` : un conteneur **`postgres-test`** (profil
  `test`) ; les tests product/customer pointent vers `pfe_products_test` / `pfe_customers_test`.
- **Nouveaux tests ajoutés** (au-dessus de la baseline) :
  - contrainte **unicité email** (409 `DUPLICATE_RESOURCE` sur insertion en double) ;
  - **FK** `products.category_id` invalide → 404/409 ;
  - **CHECK** prix > 0 et stock ≥ 0 ;
  - **séquence/ids** : création après migration → id = max+1, pas de collision ;
  - **rollback transactionnel** (écriture échouée → aucune ligne).
- **cart/order** : tests existants **inchangés** (toujours `mongo-test`).
- Rapport des **nouveaux compteurs** exacts après implémentation ; toute vérification
  impossible sera marquée `BLOCKED` / `NOT TESTED` (jamais « passed » par défaut).

---

## 10. Risques et décisions non tranchées

| # | Risque / décision | Impact | Mitigation proposée |
|---|---|---|---|
| R1 | **Alignement des séquences** après insertion d'ids explicites | Collision d'id à la 1ʳᵉ création | `setval(max(id))` + test dédié |
| R2 | **`categoryName`** : jointure vs colonne dénormalisée | Contrat API | Jointure (projection) d'abord ; colonne si besoin de perf |
| R3 | **Compatibilité JPA + MapStruct + Lombok** | Build | Config annotation-processors déjà éprouvée dans l'écosystème ; valider sur customer d'abord |
| R4 | **Base de test PostgreSQL** sans Testcontainers | Tests | Conteneur `postgres-test` dédié (profil `test`), base vidée par test |
| R5 | **Flyway 10** nécessite le module `flyway-database-postgresql` (Boot 3.3) | Migrations KO | Ajouter les 2 artefacts Flyway |
| R6 | **Cohérence inter-services** (pas de FK cross-service) | Intégrité | Assumé, documenté ; validation amont + snapshots (section 5) |
| R7 | **Données Mongo historiques** produits/clients | Perte de données | **Ne rien supprimer** avant validation + accord explicite |
| R8 | **Ordre de migration** customer puis product | Dépendances | Migrer customer d'abord (indépendant), valider, puis product |
| D1 | **Version PostgreSQL** : 16 vs 17 | Compat | Proposition : `postgres:16-alpine` (majeure stable épinglée) — ajustable |
| D2 | **Une base PostgreSQL vs deux conteneurs** | Ops | Un conteneur, deux bases, deux rôles (miroir du Mongo actuel) |
| D3 | **`order` en PG ?** | Débat | Rejeté (section 3.2) ; réversible si le jury l'exige |

---

## 11. Bilan d'implémentation (Phase C + Phase F — 11/10/2026)

**Résultat : allocation implémentée conforme à la proposition, avec les arbitrages ci-dessous.**

### 11.1 Écarts par rapport à la proposition

| Réf. | Proposition (Phase B) | Implémentation retenue | Raison |
|---|---|---|---|
| R2 | Jointure (projection) pour `categoryName` | **Colonne dénormalisée `category_name` conservée + FK `category_id`** | Préserve à l'identique le contrat REST (`categoryName` dans la réponse) sans jointure ; la FK garantit l'intégrité côté base. |
| R4 | Base de test `postgres-test` (profil `test`) | **Conforme** — `postgres-test` sur l'hôte `5433` | Le port hôte `5432` est déjà occupé par PostgreSQL local (PID 7936) → `5432` réservé ; le conteneur `postgres` de prod **ne publie aucun port hôte**. |
| D1 | `postgres:16-alpine` | **Conforme** — `postgres:16-alpine` (PostgreSQL 16.14) | Décision confirmée. |
| R5 | Ajouter les 2 artefacts Flyway | **Conforme** — `flyway-core` + `flyway-database-postgresql` | Migrations OK. |
| — | DataSeeder Mongo | **Supprimé**, remplacé par **Flyway `V2__seed_data.sql`** | Flyway est propriétaire du schéma **et** des données (ids préservés + `setval`). |

### 11.2 Fichiers créés / modifiés (résumé)

- `docker-compose.ecom.yml` : service `postgres` (prod, sans port hôte, volume `postgres-data`,
  healthcheck `pg_isready`, init `docker/postgres/init-ecom.sh`) ; service `postgres-test`
  (profil `test`, `5433→5432`, init `docker/postgres/init-ecom-test.sh`) ; `depends_on: postgres`
  pour product/customer.
- `services/{product,customer}-service` : POM (JPA + driver PostgreSQL + Flyway), entités JPA
  (`@Entity`/`@Table`/`@SequenceGenerator`), repositories `JpaRepository`, `JpaConfig`
  (`@EnableJpaAuditing`) ; suppression `MongoConfig`/`SequenceGenerator`/`DataSeeder` ;
  `db/migration/V1__init_schema.sql` + `V2__seed_data.sql`.
- `config-repo/{product,customer}-service.yml` (+ `-local.yml`) : datasource JDBC, `ddl-auto=validate`,
  `open-in-view=false`, `flyway.enabled=true`.
- `services/{product,customer}-service/src/test/resources/application.yml` : PostgreSQL `localhost:5433`.

### 11.3 Vérifications (Phase F)

- **Tests** : **52/52 verts** (product 12, customer 9, cart 13, order 12, gateway 6) — baseline 49
  préservée + 3 tests de contraintes (UNIQUE email, FK catégorie, CHECK prix/stock).
- **API via gateway** : `GET /api/products` 200 (3), `GET /api/products/999` 404, `GET /api/categories`
  200, `GET /api/customers` sans jeton 401 / admin 200 ; POST produit catégorie valide 201 (`categoryName`
  renseigné), catégorie inconnue 404, email dupliqué 409, DELETE produit/client 204/200.
- **Propriété** : `GET /api/carts/1` (yassine, propriétaire) 200 ; `GET /api/carts/2` 403 ; sans jeton 401.
- **Persistance** : redémarrage `postgres` → données et schéma Flyway préservés, migrations non
  rejouées ; `down`/`up` → volumes `postgres-data` **et** `mongo-data` conservés.
- **Non-régression Mongo** : collection `pfe_carts` (1) et `pfe_orders` (5) intactes ; bases Mongo
  historiques `pfe_products`/`pfe_customers` **non supprimées**.
- **Observabilité** : Prometheus **7/7 cibles UP** après migration.

### 11.4 Reste à faire (rollback / nettoyage)

- Les collections Mongo historiques `pfe_products`/`pfe_customers` restent **gelées** ; leur
  suppression éventuelle exige une **validation explicite** (hors périmètre de la migration).
- Rollback à chaud : rétablir l'URI Mongo dans `config-repo/<svc>.yml` + la dépendance Mongo du POM.

---

## Annexe A — Preuves consultées

- `docker-compose.ecom.yml` (services, volumes, healthchecks, ports, absence de Postgres)
- `services/*/pom.xml` (Mongo uniquement ; oauth2-resource-server sur cart/order)
- `services/*/repository/*.java`, `services/*/service/impl/*.java`
- `services/*/domain/entity/*.java` (documents, `@Indexed`, snapshots embarqués)
- `services/*/config/SequenceGenerator.java`, `MongoConfig.java`, `DataSeeder.java`
- `config-repo/*.yml` (`spring.data.mongodb.uri`, sécurité OIDC)
- `services/*/src/test/resources/application.yml` (Mongo test 27018) ; tests `*Test.java`
- `frontend-ecom/src/api.ts` (contrat REST consommé)
- `docs/RUNBOOK_ECOM.md` (§9 stratégie base), `docs/FINAL_TECHNICAL_AUDIT.md`
- `archive/pfe-flow/` (seul historique JPA/PostgreSQL — hors périmètre)

## Annexe B — Décision demandée

**Valider (ou amender) l'allocation suivante avant toute implémentation :**

- `customer-service` → **PostgreSQL**
- `product-service` → **PostgreSQL**
- `cart-service` → **MongoDB** (inchangé)
- `order-service` → **MongoDB** (inchangé)

Un conteneur PostgreSQL unique, deux bases dédiées, migrations Flyway, ids `Long` préservés,
aucune donnée MongoDB supprimée, contrats API et sécurité inchangés.
