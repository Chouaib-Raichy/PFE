---
title: "Projet de Fin d'Études"
subtitle: "Conception et industrialisation d'une plateforme e-commerce en microservices : persistance polyglotte, conteneurisation, sécurité OIDC et observabilité"
author: "[Nom de l'étudiant] — Licence DevOps"
date: "Année universitaire 2025 / 2026 — FSAC Casablanca"
---

> **Note d'état — 13/10/2026.** Le corps de ce rapport reflète un instantané antérieur
> (52 tests, 13 conteneurs, arborescence frontend mono-fichier). **Chiffres actuels vérifiés**
> (rapports Surefire du 13/10/2026) : **135 tests** (product 52, customer 17, cart 19, order 17,
> gateway 30), **16 conteneurs `healthy`** (+ `minio-init` : `Exited 0`), **7/7 cibles
> Prometheus**. Périmètre depuis étendu : recherche **Elasticsearch**, stockage **MinIO**,
> cache **Redis**, **paiement à la livraison** (`CASH_ON_DELIVERY`/`PENDING`), **provisionnement
> client** clé sur le `sub` Keycloak, **Swagger/OpenAPI agrégé**, manifestes **Kubernetes**
> (validés statiquement, **non déployés**). Sources de l'état courant : `README.md`,
> `GUIDE_SOUTENANCE.md`, `docs/RUNBOOK_ECOM.md`, `docs/FINAL_RELEASE_AUDIT.md`.

```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

# Remerciements

Nous tenons à exprimer notre gratitude à notre encadrant(e), **Pr. [Nom de l'encadrant]**,
pour sa disponibilité, ses conseils techniques et son suivi tout au long de ce projet.

Nos remerciements s'adressent également aux membres du jury d'évaluation, ainsi qu'au
corps enseignant de la Faculté des Sciences Aïn Chock (FSAC) pour la formation dispensée
durant le cycle de licence en DevOps.

```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

# Résumé

Ce projet porte sur la **conception et l'industrialisation d'une plateforme e-commerce
en architecture microservices**. Il ne se limite pas au développement fonctionnel :
l'objectif est de livrer un système **reproductible, sécurisé, testé et observable**, depuis
la machine de développement jusqu'à un environnement conteneurisé prêt à être exploité.

La plateforme est décomposée en quatre services métier indépendants — **catalogue
(produits/catégories)**, **clients**, **paniers** et **commandes** — ainsi que trois
services d'infrastructure : un **serveur de configuration** centralisé, un **annuaire
de découverte** (Eureka) et une **passerelle d'API** (API Gateway) point d'entrée unique.
Les services sont développés en **Java 21 / Spring Boot 3.3** avec **Spring Cloud**,
persistés selon une approche **polyglotte** — **PostgreSQL 16** pour les référentiels
relationnels (catalogue, clients) et **MongoDB 7** pour les agrégats documentaires
(paniers, commandes) — et exposés à un frontend **React 19 / Vite / TypeScript**.

L'industrialisation repose sur quatre piliers :

1. **La conteneurisation et l'orchestration** : chaque service est packagé en image Docker
   et orchestré par **Docker Compose** (volumes persistants, healthchecks, chaîne de
   démarrage conditionnelle), pour un total de **13 conteneurs**.
2. **La sécurité**, traitée en profondeur : authentification **OpenID Connect / OAuth2**
   via **Keycloak 26**, jetons **JWT** vérifiés par les services (*resource servers*),
   autorisation par **rôle** (ADMIN / CUSTOMER) et par **propriété** des ressources
   (un client n'accède jamais aux données d'un autre client).
3. **L'observabilité** : chaque service expose ses métriques au format Prometheus via
   **Spring Boot Actuator / Micrometer** ; **Prometheus** collecte les **7 cibles** et un
   tableau de bord **Grafana** pré-provisionné (11 panneaux) restitue le trafic, la latence
   et la disponibilité.
4. **La qualité** : **52 tests** automatisés (parcours API, sécurité et contraintes de base)
   exécutés contre des bases isolées (PostgreSQL et MongoDB), tous au vert.

**Mots-clés** : microservices, Spring Boot, Spring Cloud, Docker Compose, PostgreSQL,
MongoDB, persistance polyglotte, Flyway, JPA/Hibernate, Keycloak, OIDC, OAuth2, JWT,
Prometheus, Grafana, observabilité, React, tests automatisés.

**Abstract** — This project covers the design and industrialization of an e-commerce
platform built as independent microservices (catalog, customers, carts, orders) on
Java 21 / Spring Boot 3.3 with Spring Cloud (config server, Eureka discovery, API
gateway). Persistence is **polyglot**: a relational store (PostgreSQL) for the catalog
and customer repositories, and a document store (MongoDB) for the cart and order
aggregates, served to a React frontend. Security relies on Keycloak (OpenID Connect /
OAuth2) with JWT resource servers, role- and ownership-based authorization. Observability
is provided by Spring Boot Actuator/Prometheus metrics, a Prometheus server scraping seven
targets, and a pre-provisioned Grafana dashboard. The whole stack runs on Docker Compose
(13 containers, persistent volumes, healthchecks) and is validated by 52 automated tests.

```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

# Introduction générale

Pendant longtemps, les applications web ont été développées sous forme de **monolithes** :
un unique déployable regroupant toutes les fonctionnalités. Simple à démarrer, ce modèle
devient difficile à faire évoluer lorsque l'application grandit : chaque modification impose
de redéployer l'ensemble, une fonctionnalité critique peut être fragilisée par un composant
secondaire, et la mise à l'échelle ne peut se faire que globalement.

L'**architecture microservices** répond à ces limites en découpant le système en services
autonomes, chacun centré sur un domaine métier (*bounded context*), déployable et scalable
indépendamment. Ce découpage apporte de la modularité et de la résilience, mais introduit
de nouveaux défis : **découverte de services**, **configuration centralisée**, **sécurité
distribuée**, **observabilité** et **orchestration**. C'est précisément l'ensemble de ces
enjeux que ce projet de fin d'études prend en charge.

Le domaine retenu est celui du **e-commerce**, familier et représentatif : un catalogue de
produits, des clients, des paniers et des commandes. Ce domaine se prête naturellement au
découpage en services, tant les responsabilités y sont distinctes.

Ce projet a donc un double objet :

- **concevoir** une plateforme e-commerce en microservices correctement structurée, avec une
  **persistance adaptée au type de données** (relationnelle ou documentaire) ;
- **l'industrialiser** : la rendre reproductible (conteneurs), sécurisée (authentification
  et autorisation), observable (métriques et tableaux de bord) et vérifiable (tests
  automatisés).

Le rapport est organisé en cinq chapitres. Le premier présente le cadre théorique
(microservices, conteneurisation, sécurité OIDC, observabilité) et la problématique. Le
second expose l'étude de l'existant, les besoins et les choix technologiques. Le troisième
détaille la conception de l'architecture. Le quatrième décrit la réalisation technique. Le
cinquième présente la stratégie de tests, les résultats et la validation de bout en bout.

```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

# Chapitre 1 — Cadre théorique et problématique

## 1.1 Du monolithe aux microservices

Un **microservice** est un service applicatif autonome, faiblement couplé aux autres,
responsable d'un périmètre métier précis et exposant une interface (le plus souvent REST).
L'ensemble des microservices forme le système applicatif.

| Critère | Monolithe | Microservices |
|---|---|---|
| Déploiement | Un seul artefact | Un artefact par service |
| Scalabilité | Globale | Ciblée par service |
| Isolation des pannes | Faible | Bonne (pannes circonscrites) |
| Complexité technique | Faible au départ | Plus élevée (réseau, distribution) |
| Évolutivité des équipes | Limité par le couplage | Équipes autonomes par domaine |

La contrepartie est une **complexité opérationnelle** accrue : les services communiquent par
le réseau, doivent se trouver mutuellement, partager une configuration et être surveillés
individuellement. Les sections suivantes présentent les réponses à ces défis.

## 1.2 Conteneurisation et orchestration

Un **conteneur** est un processus isolé partageant le noyau de l'hôte, empaqueté avec ses
dépendances. **Docker** a standardisé le format d'image et son cycle de vie (`build`, `push`,
`pull`, `run`). La conteneurisation élimine le classique « ça marche sur ma machine » :
l'image construite pour le développement est identique à celle exécutée ailleurs.

**Docker Compose** orchestre plusieurs conteneurs à partir d'un fichier YAML déclaratif :
réseaux, volumes, variables d'environnement, `healthchecks` et dépendances conditionnelles
(`depends_on: condition: service_healthy`). C'est l'outil adapté à une pile multi-services à
échelle unique, suffisant ici et nettement plus simple que Kubernetes.

## 1.3 Configuration centralisée, découverte et passerelle

Trois briques de la famille **Spring Cloud** structurent l'infrastructure :

- **Spring Cloud Config** : un serveur distribue la configuration de chaque service depuis un
  dépôt (ici, le dossier `config-repo/`). On évite ainsi de dupliquer et de figer la
  configuration dans chaque artefact.
- **Netflix Eureka** : un **annuaire de découverte**. Chaque service s'y enregistre sous un
  nom logique (`product-service`, `cart-service`, …) et peut localiser les autres par nom,
  sans connaître d'adresse IP fixe.
- **Spring Cloud Gateway** : une **passerelle d'API** (*API Gateway*) qui joue le rôle de
  point d'entrée unique. Elle route les requêtes vers le bon service d'après le chemin
  (`/api/products/**` → catalogue), applique le **CORS** et constitue la **frontière de
  sécurité**.

```
Client ──► API Gateway ──► (routage) ──► product / customer / cart / order service
              │                                   ▲
              └── s'appuie sur Eureka ────────────┘ (découverte par nom)
              └── configuration servie par Config Server
```

## 1.4 Sécurité : OpenID Connect, OAuth2 et JWT

Dans un système distribué, l'authentification ne peut plus être locale à une application :
elle doit être **fédérée** et vérifiable par chaque service. **OpenID Connect (OIDC)**,
construit au-dessus d'**OAuth2**, fournit ce cadre :

- un **fournisseur d'identité** (*Identity Provider*) délivre des **jetons** signés ;
- le client s'authentifie via le flux **Authorization Code + PKCE** (adapté aux applications
  publiques telles qu'un SPA) ;
- chaque service (*resource server*) **vérifie** le jeton (signature, émetteur, audience)
  sans détenir l'identité de l'utilisateur.

Les informations de l'utilisateur (identité, rôles, attributs) voyagent dans un **JWT**
(*JSON Web Token*) — un jeton auto-porteur, signé et lisible. Les **rôles** portent
l'**autorisation** (qui a le droit de faire quoi), tandis que des **attributs** (ici
`customer_id`) permettent des contrôles fins de **propriété** des ressources.

## 1.5 Observabilité

La supervision classique répond à « le service est-il up ? ». L'**observabilité** va plus
loin : comprendre *pourquoi* le système se comporte ainsi. Ses trois piliers sont les
**métriques**, les **logs** et les **traces**. Ce projet se concentre sur les métriques :

- chaque service expose ses métriques au **format Prometheus** via **Micrometer** ;
- **Prometheus** collecte ces métriques selon un modèle *pull* (il interroge chaque cible) ;
- **Grafana** interroge Prometheus en **PromQL** et affiche les tableaux de bord.

Exemples de requêtes PromQL utilisées :

```promql
sum by (application) (rate(http_server_requests_seconds_count[1m]))                 # débit par service
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[5m]))) # latence p95
up{job="pfe-ecom"}                                                                    # disponibilité des cibles
```

## 1.6 Problématique

Comment passer d'un ensemble de services développés séparément à une **plateforme cohérente,
reproductible, sécurisée et observable**, capable d'être démarrée d'une seule commande et
surveillée en continu — sans dépendre de manipulations manuelles ni d'états implicites ?

## 1.7 Objectifs du projet

1. Concevoir une architecture microservices e-commerce (catalogue, clients, paniers,
   commandes) avec les briques d'infrastructure (configuration, découverte, passerelle).
2. Assurer la **sécurité** : authentification OIDC (Keycloak), autorisation par rôle et par
   propriété, isolation des données d'un client à l'autre.
3. Adopter une **persistance adaptée à chaque domaine** (polyglotte) : relationnelle
   (PostgreSQL) pour les référentiels soumis à des contraintes d'intégrité, documentaire
   (MongoDB) pour les agrégats dénormalisés.
4. **Conteneuriser et orchestrer** toute la pile avec Docker Compose (volumes, healthchecks).
5. Mettre en place l'**observabilité** : exposition Prometheus et tableau de bord Grafana
   *as code*.
6. Garantir la **qualité** par des tests automatisés des parcours API, de la sécurité et des
   contraintes de base.

```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

# Chapitre 2 — Étude de l'existant, besoins et choix technologiques

## 2.1 Étude de l'existant

**Approche d'architecture.** Le point de départ naturel est le monolithe (déjà décrit au
chapitre 1). S'il reste pertinent pour de petites applications, il contraint la scalabilité
et l'évolutivité. Des architectures intermédiaires (modules bien séparés dans un même
déployable) existent, mais ne répondent pas à l'objectif pédagogique d'un véritable système
distribué. Le **découpage en microservices** est retenu, avec les briques de support
associées.

**Briques transverses.**

| Préoccupation | Solutions envisagées | Retenu | Justification |
|---|---|---|---|
| Configuration | Fichiers locaux par service ; Config Server | **Spring Cloud Config** | Source unique, externalisée, rechargeable |
| Découverte | DNS statique ; Consul ; Eureka | **Netflix Eureka** | Intégration native Spring Cloud, simple |
| Passerelle | Nginx ; Zuul ; Spring Cloud Gateway | **Spring Cloud Gateway** | Réactif, filtres, intégré à la sécurité |
| Identité | Auth maison (JWT) ; Auth0 ; Keycloak | **Keycloak** | Open source, OIDC complet, rôles/attributs |
| Base | Tout relationnel ; tout documentaire ; polyglotte | **Polyglotte : PostgreSQL (référentiels) + MongoDB (agrégats)** | Chaque modèle de données sert le domaine le plus adapté (section 2.5) |
| Supervision | Prometheus + Grafana ; stack ELK | **Prometheus + Grafana** | Standard cloud-native, adapté aux métriques |
| Frontend | Rendu serveur ; SPA | **React (SPA)** | Séparation client/API, flux OIDC PKCE |

## 2.2 Besoins fonctionnels

- **Catalogue** : consulter, créer, modifier et supprimer des produits ; gérer les catégories.
- **Clients** : consulter, créer, modifier et supprimer des fiches clients.
- **Paniers** : consulter le panier d'un client, y ajouter un produit, retirer une ligne,
  vider le panier.
- **Commandes** : créer une commande à partir du panier, consulter ses commandes, suivre le
  statut.
- **Sécurité** : se connecter via Keycloak ; un client ne voit et ne modifie que ses propres
  paniers et commandes ; un administrateur gère le catalogue et l'ensemble des clients.
- **API REST** documentée (OpenAPI / Swagger) et frontend SPA consommant cette API.

## 2.3 Besoins non fonctionnels

- **Reproductibilité** : une seule commande démarre toute la pile.
- **Sécurité** : authentification fédérée, autorisation par rôle et propriété, aucun secret
  en clair dans le dépôt, surface d'exposition minimale.
- **Observabilité** : métriques par service, tableau de bord pré-provisionné.
- **Maintenabilité** : découpage clair, configuration centralisée, tests automatisés.
- **Portabilité** : exécution conteneurisée (Linux/Docker) et développement local (Windows).

## 2.4 Choix technologiques

| Besoin | Choix | Justification |
|---|---|---|
| Langage / runtime | **Java 21** | Robustesse, typage, écosystème entreprise |
| Framework services | **Spring Boot 3.3.10** | Standard, intégration Spring Cloud/Security |
| Infra microservices | **Spring Cloud 2023.0.5** | Config, Eureka, Gateway |
| Base de données | **PostgreSQL 16 + MongoDB 7 (polyglotte)** | Relationnel pour les référentiels, documentaire pour les agrégats |
| Identité | **Keycloak 26.1** | OIDC/OAuth2, rôles et attributs |
| Frontend | **React 19 + Vite + TypeScript** | SPA typée, build rapide, `keycloak-js` |
| Documentation API | **springdoc-openapi 2.6** | Swagger UI généré automatiquement |
| Mapping DTO | **MapStruct 1.6** | Conversion entité ↔ DTO sans réflexion |
| Métriques | **Micrometer / Actuator** | Export Prometheus standard |
| Supervision | **Prometheus + Grafana** | Collecte et visualisation *as code* |
| Orchestration | **Docker Compose** | Pile locale reproductible |

## 2.5 Choix de persistance : pourquoi plusieurs SGBD (polyglotte)

Le domaine recouvre deux familles de données aux besoins opposés :

| Famille | Services | Besoins | SGBD retenu |
|---|---|---|---|
| **Référentiels d'identité** | catalogue, clients | Unicité (email, nom de catégorie), intégrité référentielle (produit → catégorie), contraintes (`prix > 0`, `stock ≥ 0`), transactions ACID | **PostgreSQL 16** |
| **Agrégats transactionnels** | paniers, commandes | Lecture/écriture en bloc, lignes embarquées, dénormalisation volontaire (snapshots) | **MongoDB 7** |

Le choix retenu est donc **polyglotte mais minimal** : on fait migrer **le minimum de services**
(les deux référentiels) vers le relationnel, on conserve les deux agrégats documentaires sur
MongoDB, et l'on **ne touche ni aux contrats d'API ni à la sécurité**. Le schéma et les données
PostgreSQL sont gérés par **Flyway** (migrations versionnées), avec `ddl-auto=validate` : les
contraintes d'intégrité sont garanties **au niveau de la base**. La justification détaillée et les
alternatives écartées figurent dans `docs/POLYGLOT_PERSISTENCE_DECISION.md`.

> Contrepartie assumée : les transactions **inter-bases** ne sont pas atomiques. La cohérence est
> assurée par validation amont et *snapshots* (section 3.4), comme dans une architecture
> microservices classique.

```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

# Chapitre 3 — Conception

## 3.1 Architecture globale

```
                                   ┌───────────────────────────────┐
                                   │           Keycloak :8181       │
                                   │  fournisseur OIDC (JWT, rôles)  │
                                   └──────────────▲────────────────┘
                                                  │ (jetons)
      ┌──────────────┐   Authorization Code+PKCE  │
      │  Navigateur   │────────────────────────────┘
      │ (SPA React)   │
      └──────┬────────┘
             │ HTTPS/JSON
             ▼
      ┌───────────────────────────┐        ┌──────────────────────┐
      │  API Gateway :8090 (host) │◄──────►│  Eureka :8761        │
      │  point d'entrée, CORS,    │        │  annuaire de services │
      │  sécurité (resource srv)  │        └──────────────────────┘
      └──────┬────────────────────┘
             │ lb:// (routage par nom)
   ┌─────────┼──────────┬───────────┬───────────┐
   ▼         ▼          ▼           ▼
┌────────┐┌────────┐┌────────┐┌────────┐
│product ││customer││  cart  ││ order  │   services métier (REST)
│ :8081  ││ :8082  ││ :8083  ││ :8084  │   (non publiés sur l'hôte)
└───┬────┘└───┬────┘└───┬────┘└───┬────┘
    │         │         │         │
    └────┬────┘         └────┬────┘
         ▼                   ▼
  ┌───────────────┐   ┌───────────────┐        ┌──────────────────────────────┐
  │PostgreSQL:5432│   │ MongoDB :27017 │        │  Config Server :8888         │
  │ pfe_products  │   │  pfe_carts     │        │  configuration centralisée   │
  │ pfe_customers │   │  pfe_orders    │        └──────────────────────────────┘
  └───────────────┘   └───────────────┘
     persistance polyglotte (volumes persistants)

   Observabilité : Prometheus :9090 scrape les 7 services ──► Grafana :3000 (dashboard)
```

Les **quatre services métier** et le **frontend** ne sont jamais sollicités directement :
tout transite par la **passerelle**, qui applique l'autorisation. Le **serveur de
configuration** et la **découverte** sont des services d'infrastructure transparents pour
l'utilisateur. **Keycloak** est le seul fournisseur d'identité.

## 3.2 Découpage en services (bounded contexts)

| Service | Domaine (bounded context) | Base logique | SGBD | Port (interne) |
|---|---|---|---|---|
| `product-service` | Catalogue : produits et catégories | `pfe_products` | PostgreSQL | 8081 |
| `customer-service` | Clients : identité et contact | `pfe_customers` | PostgreSQL | 8082 |
| `cart-service` | Paniers : lignes et quantités | `pfe_carts` | MongoDB | 8083 |
| `order-service` | Commandes : validation et statut | `pfe_orders` | MongoDB | 8084 |
| `api-gateway` | Passerelle : routage, CORS, sécurité | — | — | 8080 (8090 hôte) |
| `config-server` | Configuration centralisée | (dépôt `config-repo/`) | — | 8888 |
| `discovery` | Découverte de services (Eureka) | — | — | 8761 |

## 3.3 Modèle de données (polyglotte)

Chaque service possède ses propres données ; il n'y a pas de jointures inter-services.

- **PostgreSQL** (relationnel) héberge les **référentiels** : `product-service` (produits +
  catégories) et `customer-service` (clients). Le schéma est normalisé, l'intégrité est
  garantie par des contraintes de base : clé primaire, séquences pour les identifiants,
  `UNIQUE(name)` sur les catégories, `UNIQUE(email_key)` sur les clients, `CHECK(price > 0)`
  et `CHECK(stock_quantity >= 0)` sur les produits, et une clé étrangère
  `products.category_id → categories.id`.
- **MongoDB** (documentaire) héberge les **agrégats** : `cart-service` (panier + lignes
  embarquées) et `order-service` (commande + lignes + snapshots client). Les identifiants sont
  générés par une collection `sequences` (compteurs atomiques).

```
Product (PostgreSQL)             Category (PostgreSQL)    Customer (PostgreSQL)
─────────────────────────        ─────────────────        ─────────────────────────
id            Long (PK)          id        Long (PK)      id           Long (PK)
name          String             name      String (UQ)    firstName    String
description   String             description String       lastName     String
price         NUMERIC(12,2) CHECK>0                       email        String
stockQuantity Integer  CHECK>=0  Cart (MongoDB)           emailKey     String (UQ)
imageUrl      VARCHAR(1000)      ─────────────────        phone        String
categoryId    Long (FK─┐)        id          Long         createdAt    Date
categoryName  String   │         customerId  Long         updatedAt    Date
active        Boolean  │         items       [CartItem]
createdAt     Date     │         createdAt   Date         Order (MongoDB)
updatedAt     Date     ▼                                  ─────────────────────────
                    Category     CartItem (embarqué)      id           Long
                                 ─────────────────        customerId   Long
                                 id         Long          customerName String
                                 productId  Long          customerEmail String
                                 productName String       status       OrderStatus
                                 unitPrice  BigDecimal    items        [OrderItem]
                                 quantity   Integer       createdAt    Date

OrderItem (embarqué)
─────────────────
productId   Long
productName String
unitPrice   BigDecimal
quantity    Integer
```

`OrderStatus` est une énumération (`CONFIRMED`, `CANCELLED`). Le choix de **dénormaliser**
certaines données (par exemple `productName` et `unitPrice` figés dans une ligne de commande,
`customerName` dans la commande) est volontaire : il garantit qu'une commande conserve
l'information telle qu'elle était au moment de l'achat, même si le produit ou le client évolue
ensuite.

## 3.4 Communication inter-services

Les services communiquent en **REST synchrone**, via Eureka : `cart-service` et
`order-service` peuvent solliciter `product-service` ou `customer-service` par nom logique.
Pour éviter un couplage fort, on applique deux principes :

- **validation amont** : avant d'ajouter une ligne ou de créer une commande, le service
  vérifie que le produit et le client existent ;
- **pattern snapshot** : les données nécessaires (nom, prix, email) sont **copiées** dans
  l'agrégat au moment de l'opération, ce qui évite de rappeler le service distant à chaque
  lecture.

## 3.5 Sécurité et matrice d'autorisation

L'identité provient **exclusivement du jeton** : le `customer_id` est lu dans le JWT et
n'est **jamais** accepté depuis le corps de requête ou les paramètres contrôlés par le
navigateur. La matrice ci-dessous résume les règles appliquées au niveau de la passerelle
(rôles) et des services cart/order (propriété) :

| Ressource / opération | Anonyme | CUSTOMER | ADMIN |
|---|---|---|---|
| `GET /api/products`, `GET /api/categories` | ✅ 200 | ✅ 200 | ✅ 200 |
| `POST/PUT/DELETE` produits et catégories | ⛔ 401 | ⛔ 403 | ✅ |
| `GET /api/customers` (liste) | ⛔ 401 | ⛔ 403 | ✅ 200 |
| `GET /api/customers/{id}` (profil) | ⛔ 401 | ✅ (le sien) | ✅ |
| `GET/POST/DELETE /api/carts/{customerId}` | ⛔ 401 | ✅ (le sien) | ✅ |
| `GET /api/orders`, `GET /api/orders/{id}` | ⛔ 401 | ✅ (les siennes) | ✅ (toutes) |
| `POST /api/orders` | ⛔ 401 | ✅ (pour soi) | ✅ |

Points clés : le **catalogue est public en lecture** ; toutes les **écritures** et la **liste
des clients** sont réservées à l'**ADMIN** ; les **paniers et commandes** sont protégés par
**propriété** — un client accède aux siens, l'ADMIN à tous.

## 3.6 Architecture de l'observabilité

- **Instrumentation** : chaque service embarque Micrometer ; l'Actuator expose
  `/actuator/prometheus` (histogrammes de latence activés, tag `application`).
- **Collecte** : Prometheus interroge les **7 cibles** (`config-server`, `discovery`,
  `api-gateway`, les 4 services métier) via le réseau Docker interne.
- **Restitution** : Grafana, provisionné par code (datasource + dashboard JSON), affiche le
  débit, la latence p95, l'utilisation et la disponibilité.

Le port de gestion de la passerelle est **séparé** du port public (Actuator non exposé sur
l'hôte), afin de ne pas élargir la surface d'attaque.

## 3.7 Diagramme de séquence — ajout panier puis commande

```
Navigateur        Gateway          cart-service      product-service     order-service     Mongo
   │  POST /api/carts/1/items (Bearer)  │                  │                  │              │
   │───────────────►│  vérif JWT + rôle │                  │                  │              │
   │                │─────────────────►│ requireOwnership(customerId=1)       │              │
   │                │                  │ GET /api/products/{id} (interne)     │              │
   │                │                  │─────────────────►│ (valide le produit)            │
   │                │                  │◄─────────────────│                │              │
   │                │                  │ upsert CartItem + snapshot prix     │              │
   │                │                  │──────────────────────────────────────────────────►│
   │  200 Cart      │◄─────────────────│                  │                  │              │
   │◄───────────────│                  │                  │                  │              │

   │  POST /api/orders (Bearer, customerId=1)             │                  │              │
   │───────────────►│  vérif JWT + rôle │                  │                  │              │
   │                │─────────────────────────────────────────────────────►│ requireOwnership│
   │                │                  │                  │  GET customer (interne)         │
   │                │                  │                  │◄──────────────── │              │
   │                │                  │                  │  create Order + snapshot        │
   │                │                  │                  │────────────────────────────────►│
   │  201 Order     │◄─────────────────────────────────────────────────────│              │
   │◄───────────────│                  │                  │                  │              │
```

```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

# Chapitre 4 — Réalisation

## 4.1 Structure du dépôt

```
PFE/
├── services/
│   ├── product-service/     # catalogue (produits, catégories) — JPA + PostgreSQL + Flyway
│   ├── customer-service/    # clients — JPA + PostgreSQL + Flyway
│   ├── cart-service/        # paniers (+ sécurité propriété) — MongoDB
│   ├── order-service/       # commandes (+ sécurité propriété) — MongoDB
│   ├── api-gateway/         # passerelle, CORS, resource server JWT
│   ├── config-server/       # serveur de configuration
│   └── discovery/           # annuaire Eureka
├── config-repo/             # configuration centralisée (application.yml, *.yml)
├── frontend-ecom/           # SPA React + keycloak-js (auth.ts, api.ts, App.tsx)
├── keycloak/                # realm-pfeecom.json (import automatique)
├── monitoring/
│   ├── prometheus/          # prometheus.yml (7 cibles)
│   └── grafana/             # provisioning + dashboards (pfe-ecom-overview.json)
├── docs/                    # RUNBOOK_ECOM.md, FINAL_TECHNICAL_AUDIT.md, POLYGLOT_PERSISTENCE_DECISION.md
├── docker-compose.ecom.yml  # orchestration de la pile (13 conteneurs)
└── archive/pfe-flow/        # ancien projet (hors soutenance)
```

## 4.2 Configuration centralisée

Le **Config Server** sert la configuration depuis `config-repo/`. Deux niveaux sont
distingués : `application.yml` (commun à tous les services) et un fichier par service. La
configuration globale active, entre autres, l'exposition des métriques :

```yaml
# config-repo/application.yml (extrait)
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    tags:
      application: ${spring.application.name}
    distribution:
      percentiles-histogram:
        http.server.requests: true
  prometheus:
    metrics:
      export:
        enabled: true
```

Aucun secret n'est écrit en clair : les valeurs sensibles sont injectées par variables
d'environnement avec des valeurs par défaut non sensibles (`${VAR:defaut}`).

## 4.3 Découverte et passerelle

La passerelle route par chemin, en s'appuyant sur Eureka (`lb://`) :

```yaml
# config-repo/api-gateway.yml (extrait)
spring:
  cloud:
    gateway:
      routes:
        - id: product-service
          uri: lb://product-service
          predicates:
            - Path=/api/products/**, /api/categories/**
        - id: cart-service
          uri: lb://cart-service
          predicates:
            - Path=/api/carts/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins: ["http://localhost:5173"]
            allowedMethods: [GET, POST, PUT, PATCH, DELETE, OPTIONS]
            allowCredentials: true
```

La découverte par nom (`lower-case-service-id`) évite toute adresse IP fixe : un service
démarré ou redémarré est immédiatement localisable.

## 4.4 Sécurité en pratique

**Côté passerelle et services** : la passerelle, `cart-service` et `order-service` sont des
*resource servers*. Ils valident les JWT par rapport à l'émetteur Keycloak et à l'audience
attendue. Le décodeur est explicite, ce qui évite toute ambiguïté entre l'URL publique (vue
par le navigateur) et l'URL interne Docker (utilisée pour récupérer les clés) :

```yaml
# extrait de la configuration de sécurité
app:
  security:
    issuer-uri:   ${KEYCLOAK_ISSUER_URI:http://localhost:8181/realms/pfeecom}
    jwk-set-uri:  ${KEYCLOAK_JWK_SET_URI:http://keycloak:8080/realms/pfeecom/protocol/openid-connect/certs}
```

**Côté applicatif** : un composant `CurrentUser` traduit le jeton en décisions simples :

```java
// cart-service — vérification de propriété (extrait)
public void requireOwnership(Authentication auth, Long customerId) {
    if (isAdmin(auth)) return;                 // l'ADMIN bypass la propriété
    Long own = customerId(auth);               // lu dans le claim customer_id du JWT
    if (own == null || !own.equals(customerId)) {
        throw new AccessDeniedException("Acces refuse a la ressource d'un autre client.");
    }
}
```

**Côté frontend** : la SPA utilise `keycloak-js` en **Authorization Code + PKCE** ; elle
joint le jeton d'accès à chaque appel d'API (`Authorization: Bearer …`).

## 4.5 Le frontend React

Le frontend est une **SPA** (React 19, Vite, TypeScript) organisée simplement : `auth.ts`
gère le cycle OIDC (login/logout, rafraîchissement du jeton), `api.ts` centralise les appels
vers la passerelle en y injectant le jeton, `App.tsx` compose l'interface (catalogue, panier,
commandes). Des types partagés (`types.ts`) garantissent la cohérence avec les DTO exposés par
l'API. Le build est un ensemble statique servi par Nginx.

## 4.6 Persistance polyglotte (PostgreSQL + MongoDB)

La persistance suit le choix de la section 2.5 : **relationnelle pour les référentiels**,
**documentaire pour les agrégats**.

**PostgreSQL 16** (`postgres:16-alpine`, conteneur `postgres`, volume `postgres-data`,
**aucun port publié sur l'hôte**) héberge deux bases — `pfe_products` et `pfe_customers` — avec
deux rôles applicatifs dédiés (principe du moindre privilège). Les services `product` et
`customer` utilisent **Spring Data JPA (Hibernate)**. Le schéma et les données sont gérés par
**Flyway** : `V1__init_schema.sql` (schéma + contraintes) et `V2__seed_data.sql` (données de
démonstration). Hibernate est en mode `validate` : il **ne modifie jamais** le schéma, il le
vérifie au démarrage, de sorte que la base reste la source de vérité du modèle relationnel.

```sql
-- V1__init_schema.sql (extrait)
CREATE TABLE categories (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500)
);
CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price NUMERIC(12,2) NOT NULL CHECK (price > 0),
    stock_quantity INTEGER NOT NULL CHECK (stock_quantity >= 0),
    category_id BIGINT REFERENCES categories(id),
    ...
);
```

Les identifiants proviennent de **séquences** PostgreSQL (`category_seq`, `product_seq`,
`customer_seq`) ; les données de démonstration reprennent les identifiants de la maquette
(3 produits, 2 catégories, 2 clients : Yassine Alami et Sarah Berrada). L'unicité de l'email,
la validité de la clé étrangère et les contraintes `CHECK` sont ainsi **garanties par la base**
(et non seulement par le code applicatif).

**MongoDB 7** (conteneur `mongo`, volume `mongo-data`) héberge les deux agrégats
documentaires — `pfe_carts` et `pfe_orders` — **inchangés** : lignes embarquées, dénormalisation
volontaire (snapshots) et identifiants générés par une collection `sequences`. La migration a
laissé ces données et ces bases **intactes** ; les anciennes collections Mongo
`pfe_products`/`pfe_customers` sont conservées **gelées** (rollback possible en rétablissant
l'URI Mongo dans la configuration).

> Le port de test `postgres-test` (profil `test`) écoute sur l'hôte `5433` (l'hôte `5432` étant
> déjà occupé par une instance locale) ; `mongo-test` écoute sur `27018`.

## 4.7 Orchestration Docker Compose

La pile complète comprend **13 conteneurs**, orchestrés par `docker-compose.ecom.yml` :

```
config-server ─┐
               ├─► (Eureka) ─► product / customer / cart / order ─► api-gateway ─► frontend
discovery ─────┘                    ▲                                   ▲
postgres (volume) ── product/customer┘                                  │
mongo (volume) ───── cart/order ────────────────────────────────────────┘
keycloak (volume) ────────────────────────────────────────────────────┘
prometheus (volume) ─► scrape les 7 services ─► grafana (volume)
```

Points notables :

- **healthchecks** sur chaque service ; le démarrage suit une chaîne de confiance
  (`depends_on: condition: service_healthy`) : infrastructure (config, bases) → services →
  passerelle → frontend ;
- **volumes nommés** (`postgres-data`, `mongo-data`, `keycloak-data`, `prometheus-data`,
  `grafana-data`) : les données survivent à un `down`/`up` ;
- **surface d'exposition minimale** : seuls la passerelle, le frontend, le Config Server,
  Eureka, Keycloak, Prometheus et Grafana publient un port. Les quatre services métier restent
  sur le réseau Docker privé, et **PostgreSQL n'expose aucun port sur l'hôte** (surface réduite) ;
- le port de **gestion** de la passerelle (Actuator) est **interne**, non publié sur l'hôte.

## 4.8 Observabilité en pratique

**Métriques** (extrait du format Prometheus) :

```
http_server_requests_seconds_count{application="product-service",method="GET",uri="/api/products",status="200"} 42
http_server_requests_seconds_bucket{le="0.05", ...} 38
up{job="pfe-ecom",instance="product-service:8081"} 1
```

**Collecte** : Prometheus interroge les **7 cibles** de la pile (modèle *pull*).

**Dashboard Grafana** `pfe-ecom-overview` (« PFE E-commerce — Vue d'ensemble »), **11
panneaux**, dont :

| Panneau | Requête PromQL (principe) |
|---|---|
| Débit par service | `sum by (application) (rate(http_server_requests_seconds_count[1m]))` |
| Latence p95 | `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[5m])))` |
| Taux d'erreurs 5xx | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))` |
| Disponibilité des cibles | `up{job="pfe-ecom"}` |

Le dashboard et la datasource sont **provisionnés par code** (fichiers versionnés montés en
volume) : la supervision est ainsi « as code », reproductible au même titre que
l'application.

```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

# Chapitre 5 — Tests, résultats et validation

## 5.1 Stratégie de tests

Les tests vérifient à la fois les **parcours fonctionnels** (API), les **règles de sécurité** et
les **contraintes de base**. Ils s'exécutent contre des instances isolées — **PostgreSQL de test**
(`postgres-test`, port 5433, bases `pfe_products_test`/`pfe_customers_test`) pour `product`/
`customer` et **MongoDB de test** (`mongo-test`, port 27018) pour `cart`/`order` — dont les données
sont nettoyées avant chaque test, afin de ne jamais toucher aux données de démonstration. Les
appels authentifiés utilisent un **jeton simulé** (`mockJwt`) et les règles d'autorisation sont
testées par service. Flyway applique les migrations sur la base de test, avec `ddl-auto=validate`.

## 5.2 Résultats

| Service | Classes de test | Tests | Résultat |
|---|---|---|---|
| `product-service` | `ProductApiIntegrationTest` | 12 | 12/12 |
| `customer-service` | `CustomerApiIntegrationTest` | 9 | 9/9 |
| `cart-service` | `CartApiIntegrationTest` + `CartSecurityTest` | 9 + 4 | 13/13 |
| `order-service` | `OrderApiIntegrationTest` + `OrderSecurityTest` | 7 + 5 | 12/12 |
| `api-gateway` | `GatewaySecurityTest` | 6 | 6/6 |
| **Total** | | **52** | **52/52 (0 échec, 0 erreur, 0 ignoré)** |

Les tests de sécurité couvrent notamment : accès sans jeton (401), accès avec le mauvais rôle
(403), accès inter-clients (403) et accès légitime (2xx). Trois tests supplémentaires vérifient
les **contraintes de base** : unicité de l'email client (409), clé étrangère `category_id`
invalide, et contraintes `CHECK` sur le prix et le stock.

## 5.3 Validation de la pile complète

Scénario exécuté sur la pile réelle :

1. `docker compose -f docker-compose.ecom.yml up -d --build` — construction et démarrage.
2. Attente de l'état **healthy** des conteneurs : **13 services** sains.
3. Contrôle de la **santé** de la passerelle (Actuator interne) : `UP`, avec les 5
   applications enregistrées dans Eureka.
4. Contrôle des **cibles Prometheus** : **7/7** en état `up`.
5. Ouverture du frontend (`:5173`), de Grafana (`:3000`, dashboard `pfe-ecom-overview`) et de
   Prometheus (`:9090`).
6. Vérification de la **persistance** : les données subsistent après un `down`/`up` (volumes).

## 5.4 Validation de la sécurité (matrice observée)

| Requête | Attendu | Observé |
|---|---|---|
| `GET /api/products` (sans jeton) | 200 | 200 |
| `GET /api/customers` (sans jeton) | 401 | 401 |
| `GET /api/customers` (jeton CUSTOMER) | 403 | 403 |
| `GET /api/customers` (jeton ADMIN) | 200 | 200 |
| `GET /api/carts/1` (propriétaire) | 200 | 200 |
| `GET /api/carts/2` (cross-client) | 403 | 403 |
| `POST /api/products` (sans jeton) | 401 | 401 |
| `POST /api/products` (CUSTOMER) | 403 | 403 |
| `GET /actuator/health` (port public) | refusé | 404 |
| `GET /actuator/prometheus` (port public) | refusé | 401 |

Ces vérifications démontrent que la passerelle constitue une **frontière de sécurité
effective** et que la **propriété** des ressources est réellement contrôlée.

## 5.5 Limites connues

- Les **appels inter-services ne propagent pas de jeton** (pas de *token relay* ni de mTLS) :
  la protection repose sur l'isolation du réseau Docker.
- Les **utilisateurs Keycloak** et le *grant* direct sont des éléments de **démonstration**.
- **Prometheus est sans authentification et sans alerting** (dashboards uniquement) ; il n'y a
  pas de TLS de bout en bout.
- Le **stock n'est pas décrémenté** lors d'une commande ; il n'existe pas de transaction
  distribuée (cohérence par *snapshots* et validation amont). Avec la persistance polyglotte,
  les transactions **inter-bases** (PostgreSQL ↔ MongoDB) ne sont pas atomiques non plus — limite
  assumée et documentée.
- Pas de pipeline CI/CD actif dans le dépôt pour cette pile (proposé en perspective).

```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

# Conclusion générale

Ce projet a permis de mettre en œuvre **l'ensemble de la chaîne** d'un système distribué
moderne : conception d'une architecture microservices e-commerce, configuration centralisée
et découverte de services, passerelle d'API, sécurité fédérée par **OIDC/OAuth2 (Keycloak)**
avec autorisation par rôle et par propriété, **persistance polyglotte** (PostgreSQL pour les
référentiels, MongoDB pour les agrégats), conteneurisation et orchestration **Docker Compose**,
et **observabilité** par **Prometheus** et **Grafana** provisionnés par code.

Le résultat n'est pas seulement un ensemble de services fonctionnels : c'est une
**plateforme reproductible** — une commande démarre les 13 conteneurs — **sécurisée** — la
matrice d'autorisation est vérifiée de bout en bout (401/403/200) — **observable** — 7 cibles
Prometheus et un dashboard de 11 panneaux — et **vérifiable** — **52 tests automatisés** au
vert.

## Perspectives

- **Sécuriser les appels inter-services** : jetons de service (*client credentials*) ou mTLS.
- **Alerting** : règles Prometheus et Alertmanager (latence p95, taux 5xx, cible indisponible).
- **CI/CD** : pipeline de build, tests et analyse pour cette pile ; publication d'images.
- **Cohérence métier** : décrément/réservation de stock, motifs de *saga* pour les
  transactions distribuées.
- **Traces et logs** : OpenTelemetry, collecte des logs (Loki) pour compléter les métriques.
- **Industrialisation** : migration vers Kubernetes (Deployments, Services, Ingress, HPA) et
  approche GitOps.
- **Tests de charge** (k6) et définition de SLO.

```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

# Bibliographie

1. Richardson, C. — *Microservices Patterns*, Manning, 2018.
2. Newman, S. — *Building Microservices* (2ᵉ éd.), O'Reilly, 2021.
3. Documentation Spring Boot — https://docs.spring.io/spring-boot/
4. Documentation Spring Cloud Gateway — https://docs.spring.io/spring-cloud-gateway/
5. Documentation Keycloak — https://www.keycloak.org/documentation
6. OpenID Connect Core 1.0 — https://openid.net/specs/openid-connect-core-1_0.html
7. Documentation MongoDB — https://www.mongodb.com/docs/
8. Documentation PostgreSQL — https://www.postgresql.org/docs/
9. Documentation Flyway — https://documentation.red-gate.com/flyway
10. Documentation Prometheus — https://prometheus.io/docs/
11. Documentation Grafana — https://grafana.com/docs/
12. Documentation Docker Compose — https://docs.docker.com/compose/
