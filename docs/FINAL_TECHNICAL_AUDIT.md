# Audit technique final — Plateforme e-commerce microservices (PFE FSAC)

**Projet :** E-commerce microservices (`D:\PROJECTS\PFE`)
**Date de l'audit :** 11/10/2026
**Auditeur :** revue technique automatisée assistée
**Périmètre :** stack de soutenance `docker-compose.ecom.yml`, services Spring Boot,
`frontend-ecom`, `config-repo/`, `keycloak/`, `monitoring/`.

> **Règle de preuve.** Ce document distingue systématiquement quatre niveaux :
> **[V]** vérifié par exécution réelle pendant cet audit, **[C]** vérifié par lecture du
> code/configuration, **[N]** non vérifié (aucune preuve d'exécution), **[L]** limite assumée
> connue. Aucun résultat n'est extrapolé : un élément non prouvé est marqué **[N]**.

> **Mise à jour post-audit — 13/10/2026 (document historique préservé).** Cet audit est un
> **instantané daté du 11/10/2026** ; ses chiffres (52 tests, 13 conteneurs, 32 modules frontend) sont
> conservés tels quels comme preuve de l'état **à cette date**. Depuis, le périmètre a été étendu :
> recherche **Elasticsearch** (outbox + repli), stockage d'images **MinIO/S3**, cache **Redis**,
> **paiement à la livraison** (`CASH_ON_DELIVERY`/`PENDING`), **provisionnement client** clé sur le
> `sub` Keycloak, **Swagger/OpenAPI agrégé**, et manifestes **Kubernetes** validés statiquement mais
> **non déployés**. Chiffres **actuels re-vérifiés** (rapports Surefire du 13/10/2026) : **135 tests**
> (product 52, customer 17, cart 19, order 17, gateway 30 ; 0 échec/erreur/ignoré), **16 conteneurs
> sains** (+ `minio-init` en `Exited 0`), **7/7 cibles Prometheus**. Sources de l'état courant :
> `README.md`, `GUIDE_SOUTENANCE.md`, `docs/RUNBOOK_ECOM.md`.

---

## 1. Objet

Évaluer l'état réel de la plateforme avant soutenance : fonctionne-t-elle telle que décrite,
quelles sont ses garanties de sécurité et d'observabilité, quelles limites subsistent et quelles
actions prioriser. Il ne s'agit pas d'une revue de style mais d'un **constat d'ingénierie**.

## 2. Méthode

1. Relecture de la configuration (`docker-compose.ecom.yml`, `config-repo/`, `monitoring/`,
   `keycloak/realm-pfeecom.json`) et du code sécurité (`SecurityConfig`, `CurrentUser`).
2. Exécution de contrôles **sur la pile en fonctionnement** : `docker compose config`, `ps`,
   endpoints Actuator, API Prometheus, API Grafana, MongoDB.
3. Exécution de la **matrice d'autorisation** de bout en bout (jetons Keycloak réels) via le gateway.
4. Exécution des **52 tests** des 5 services (rapports Surefire).
5. Contrôles frontend (`typecheck`, `lint`, `build`).

## 3. Inventaire technique

| Couche | Élément | Version / valeur | Source |
|---|---|---|---|
| Runtime | Java | 21 | `services/*/pom.xml` |
| Framework | Spring Boot | 3.3.10 | `services/*/pom.xml` |
| Cloud | Spring Cloud | 2023.0.5 | `services/*/pom.xml` |
| Base | PostgreSQL + MongoDB | 16 / 7 | `docker-compose.ecom.yml` |
| Identité | Keycloak | 26.1 (`start-dev`) | `docker-compose.ecom.yml` |
| Supervision | Prometheus / Grafana | 2.54.1 / 11.2.0 | `docker-compose.ecom.yml` |
| Frontend | React / Vite / TS | 19.1 / 7.1 / 5.9 | `frontend-ecom/package.json` |
| Conteneurs | services `compose` | **13** (dont `mongo-test`/`postgres-test` hors profil) | `docker compose config` **[V]** |

Services métier : `product-service` (8081), `customer-service` (8082), `cart-service` (8083),
`order-service` (8084) ; plateforme : `config-server` (8888), `discovery` (8761), `api-gateway`
(8090→8080). **Persistance polyglotte** : `product`/`customer` → PostgreSQL (`pfe_products`,
`pfe_customers`), `cart`/`order` → MongoDB (`pfe_carts`, `pfe_orders`). Cf.
`docs/POLYGLOT_PERSISTENCE_DECISION.md`.

## 4. Résultats vérifiés

### 4.1 Stabilité et démarrage

| Contrôle | Résultat | Statut |
|---|---|---|
| `docker compose config --quiet` | exit 0 (aucune erreur) | **[V]** |
| `docker compose ps` | 13 services `Up (healthy)` + `mongo-test`/`postgres-test` `Up` | **[V]** |
| Santé config-server / Eureka | `UP` / `UP` | **[V]** |
| Santé gateway (port de gestion interne 8081) | `UP`, 5 applications Eureka enregistrées | **[V]** |
| Santé Grafana / frontend | `database: ok` / HTTP 200 | **[V]** |

### 4.2 Tests automatisés (rapports Surefire, relancés)

| Service | Classes | Tests | Résultat |
|---|---|---|---|
| product-service | `ProductApiIntegrationTest` | 12 | 12/12 |
| customer-service | `CustomerApiIntegrationTest` | 9 | 9/9 |
| cart-service | `CartApiIntegrationTest` (9) + `CartSecurityTest` (4) | 13 | 13/13 |
| order-service | `OrderApiIntegrationTest` (7) + `OrderSecurityTest` (5) | 12 | 12/12 |
| api-gateway | `GatewaySecurityTest` | 6 | 6/6 |
| **Total** | | **52** | **52/52, Failures 0, Errors 0, Skipped 0** **[V]** |

### 4.3 Sécurité (matrice d'autorisation, jetons Keycloak réels via `:8090`)

| Requête | Attendu | Observé | Statut |
|---|---|---|---|
| `GET /api/products` (sans jeton) | 200 (public) | 200 | **[V]** |
| `GET /api/customers` (sans jeton) | 401 | 401 | **[V]** |
| `GET /api/customers` (jeton CUSTOMER) | 403 | 403 | **[V]** |
| `GET /api/customers` (jeton ADMIN) | 200 | 200 | **[V]** |
| `GET /api/carts/1` (jeton `yassine`, propriétaire) | 200 | 200 | **[V]** |
| `GET /api/carts/2` (jeton `yassine`, cross-customer) | 403 | 403 | **[V]** |
| `POST /api/products` (sans jeton) | 401 | 401 | **[V]** |
| `POST /api/products` (jeton CUSTOMER) | 403 | 403 | **[V]** |
| `POST /api/products` (jeton ADMIN, corps invalide) | 400 | 400 (auth OK, validation KO) | **[V]** |
| `GET /actuator/health` (port public 8090) | 404/refusé | 404 | **[V]** |
| `GET /actuator/prometheus` (port public 8090) | 401/refusé | 401 | **[V]** |

Points forts confirmés : le gateway est une véritable **frontière de confiance** ; l'identité client
provient **toujours** du jeton (`customer_id`), jamais du navigateur ; le contrôle de propriété
(cart/order) produit bien un **403 cross-customer** ; l'Actuator du gateway est isolé sur un port de
gestion interne non publié **[C]** et les routes `/*/actuator/**` sont refusées **[C]**.

### 4.4 Observabilité

| Contrôle | Résultat | Statut |
|---|---|---|
| `sum(up)` Prometheus (job `pfe-ecom`) | **7** | **[V]** |
| Cibles `health=up` | **7/7** | **[V]** |
| Séries `http_server_requests_seconds_count` | 44 | **[V]** |
| Datasource Grafana `prometheus` | `OK` | **[V]** |
| Dashboard provisionné | `pfe-ecom-overview` « PFE E-commerce - Vue d'ensemble », **11 panneaux** | **[V]** |

### 4.5 Persistance des données (polyglotte)

| Base | Contenu (vérifié après migration) | Statut |
|---|---|---|
| PostgreSQL `pfe_products` | 3 produits (ids 1–3) + 2 catégories ; séquence `product_seq`=3 | **[V]** |
| PostgreSQL `pfe_customers` | 2 clients (ids 1–2) ; séquence `customer_seq`=2 | **[V]** |
| MongoDB `pfe_carts` | 1 panier | **[V]** |
| MongoDB `pfe_orders` | 5 commandes | **[V]** |
| MongoDB `pfe_products`/`pfe_customers` (legacy) | conservées **gelées** (rollback) | **[V]** |

Les volumes nommés (`postgres-data`, `mongo-data`, `keycloak-data`, `prometheus-data`,
`grafana-data`) survivent aux redémarrages `down`/`up` **[V]** (sans `-v`). Après redémarrage de
`postgres`, les données et le schéma Flyway sont préservés (migrations non rejouées) **[V]**.

### 4.6 Frontend

| Contrôle | Résultat | Statut |
|---|---|---|
| `npm run typecheck` (`tsc --noEmit`) | exit 0 | **[V]** |
| `npm run lint` (`eslint .`) | exit 0 | **[V]** |
| `npm run build` (`vite build`) | exit 0, 32 modules, `dist/` produit | **[V]** |
| Service HTTP | Nginx 200 | **[V]** |

Le frontend est servi par Nginx et lève l'authentification OIDC (Authorization Code + PKCE) via
`keycloak-js` **[C]**.

## 5. Évaluation de sécurité

**Garanties en place**
- OIDC complet (Keycloak), clients public PKCE (frontend) et bearer-only (API), audience `pfeecom-api`.
- Resource servers JWT sur gateway, cart et order ; catalogue en lecture publique.
- Autorisation par rôle (ADMIN/CUSTOMER) au gateway ; propriété applicative sur cart/order.
- Aucun secret en dur : identifiants par variables d'environnement (`${VAR:defaut}`) **[C]**.
- Surface externe minimale : seuls gateway, frontend, config, Eureka, Keycloak, Prometheus et Grafana
  sont publiés ; les 4 services métier restent sur le réseau Docker privé **[C]**.
- Actuator du gateway non exposé publiquement ; actuator des autres services non publié sur l'hôte **[C]**.

**Faiblesses / points de vigilance**
- **Appels inter-services non authentifiés** `[L]` : `product`/`customer` sont ouverts en interne ;
  `cart`/`order` les appellent **sans propager de jeton** (pas de token relay, pas de mTLS). La
  protection repose uniquement sur l'isolation réseau Docker.
- **Éléments de démonstration** `[L]` : utilisateurs Keycloak de démo, `direct access grant`
  (grant *password*) activé pour les tests, identifiants Grafana par défaut surchargeables.
- **Prometheus sans authentification ni alerting** `[L]` (démo) : dashboards uniquement, aucune règle
  Alertmanager.
- **Pas de TLS** `[L]` : la pile est HTTP en local ; `KC_HOSTNAME` impose l'URL publique de l'émetteur.
- **Pas de rotation/expiration renforcée documentée** au-delà de la durée de vie des jetons Keycloak.

## 6. Limites fonctionnelles et dette technique

- Pas de **transaction distribuée** ; cohérence par *snapshots* et validation amont `[C]`.
- **Stock non décrémenté** lors d'une commande `[C]`.
- Pas de **versioning d'API** explicite (`/api/...`, pas de `/api/v1`) `[C]`.
- Tests limités à MockMvc + sécurité : pas de tests de contrat (WireMock) ni d'E2E frontend automatisés
  (pas de Vitest) `[C]`.
- Pas de CI/CD actif dans le dépôt pour cette stack (l'ancien `ci.yml` est archivé sous
  `archive/pfe-flow/`) `[V]`.
- Rendu **navigateur** de Grafana non vérifié par capture automatique (seules l'API datasource et le
  provisioning du dashboard l'ont été) `[N]`.
- Connexion **PKCE en navigateur** non rejouée lors de cet audit (vérifiée lors d'une phase
  antérieure) `[N]`.

## 7. Recommandations priorisées

**P1 — avant soutenance (faible effort)**
1. Retirer les captures `docs/assets/` obsolètes et prendre les captures réelles (SPA, Keycloak,
   Prometheus, Grafana) selon la checklist du guide.
2. Vérifier une dernière fois la démo « from scratch » (machine redémarrée) et la persistance `down`/`up`.

**P2 — robustesse (effort moyen)**
3. Sécuriser les appels inter-services : *client-credentials* (jeton de service) ou mTLS.
4. Ajouter des **règles d'alerte** Prometheus + Alertmanager (latence p95, taux 5xx, cible DOWN).
5. Réintroduire un pipeline CI (build + tests + analyse) pour cette stack.

**P3 — industrialisation (perspectives)**
6. Décrément/réservation de stock (saga), versioning d'API, tests de contrat (WireMock).
7. TLS de bout en bout, secrets externalisés (Vault), déploiement Kubernetes/GitOps.

## 8. Verdict

**Verdict : conforme à la description pour un livrable de PFE, avec limites assumées.** La plateforme
**fonctionne réellement** : 13 conteneurs sains, **52/52 tests verts**, matrice d'autorisation correcte
de bout en bout (401/403/200), **7/7 cibles Prometheus** et dashboard Grafana provisionné, persistance
vérifiée. Les faiblesses identifiées sont **documentées et connues** (appels inter-services sans
jeton, éléments de démo, absence d'alerting/TLS) et n'invalident pas la démonstration. Les actions P1
doivent être traitées avant la soutenance ; P2/P3 relèvent des perspectives.

## Annexe A — Commandes de reproduction

```powershell
# 1. État de la pile
docker compose -f docker-compose.ecom.yml config --quiet
docker compose -f docker-compose.ecom.yml ps

# 2. Cibles Prometheus (7/7 up)
(Invoke-RestMethod http://127.0.0.1:9090/api/v1/targets).data.activeTargets |
  Select-Object @{n='instance';e={$_.labels.instance}}, health

# 3. Santé du gateway (port de gestion interne)
docker exec pfe-api-gateway-1 wget -qO- http://127.0.0.1:8081/actuator/health

# 4. Jeton + matrice d'autorisation (extrait)
$tok = 'http://localhost:8181/realms/pfeecom/protocol/openid-connect/token'
$y = (Invoke-RestMethod -Method Post -Uri $tok -Body @{ grant_type='password'; client_id='pfeecom-frontend'; username='yassine'; password='yassine'; scope='openid' }).access_token
Invoke-WebRequest http://127.0.0.1:8090/api/customers -Headers @{ Authorization = "Bearer $y" }   # 403

# 5. Tests (prérequis : bases de test démarrées)
docker compose -f docker-compose.ecom.yml --profile test up -d mongo-test postgres-test
cd services/product-service ; mvn -B test    # ... idem customer/cart/order, api-gateway

# 6. Frontend
cd frontend-ecom ; npm run typecheck ; npm run lint ; npm run build
```
