# Audit de release finale — Plateforme e-commerce microservices (PFE)

**Projet :** `D:\PROJECTS\PFE`
**Objet :** audit de préparation à la publication GitHub et à la soutenance
**Date de l'audit :** 13/10/2026
**Mode :** lecture seule pour l'audit ; l'audit n'a modifié aucune donnée métier, aucun
conteneur, aucune base, aucun secret.
**Auteur du projet :** Chouaib Raichy · **Encadrant :** Pr. Noreddine Abghour

> **Règle de preuve.** Chaque constat est classé **[V]** *vérifié par exécution réelle*,
> **[C]** *vérifié par lecture du code/configuration*, ou **[N]** *non vérifié*. Un élément
> non prouvé est marqué **[N]** et n'est jamais présenté comme validé.

---

## 1. Méthode

1. Inspection read-only du dépôt Git (branche, historique, remotes, fichiers suivis/non suivis).
2. Lecture du code, de la configuration (`docker-compose.ecom.yml`, `config-repo/`,
   `monitoring/`, `keycloak/`, `k8s/`) et des tests.
3. Recherche de secrets (`.env`, clés, certificats, identifiants en dur).
4. Sondes **sur la pile en fonctionnement** : conteneurs, Actuator, Prometheus, Eureka, gateway.
5. Vérification du décompte exact des tests (`@Test` par classe).
6. Relecture croisée de la documentation existante pour détecter les incohérences.

---

## 2. État Git et publication

| Élément | Constat | Statut |
|---|---|---|
| Dépôt Git | présent (`.git`), branche `master` | **[V]** |
| Historique | **7 commits**, dont 6 relevant de l'ancien projet « PFE Flow » | **[V]** |
| Remote | **aucun** (`git remote -v` vide) | **[V]** |
| Arbre de travail | **115 modifications non commitées** = migration en cours (ancien `backend/`/`frontend/` supprimés, nouveau `services/`+`frontend-ecom/` ajoutés) | **[V]** |
| Dernier commit | `207029f chore(slides): refresh slides for soutenance` | **[V]** |
| Publication | **BLOCKED** : aucune URL de dépôt distant fournie par l'utilisateur | **[V]** |

**Conséquence.** L'arbre de travail contient la **plateforme e-commerce actuelle** (non
versionnée) tandis que l'historique Git décrit l'**ancien monolithe PFE Flow**. La publication
professionnelle exige d'organiser cette migration en commits logiques (cf. §8) puis de pousser
vers un dépôt GitHub dont l'URL doit être confirmée par l'utilisateur.

### 2.1 Fichiers mis en quarantaine (ne pas versionner)

| Chemin | Nature | Traitement |
|---|---|---|
| `deploy.log` (41 850 o) | journal de build Compose | **à ignorer** |
| `services/*/reg-*.log` | journaux de démarrage | **à ignorer** |
| `frontend-ecom/node_modules/` | dépendances | **à ignorer** |
| `frontend-ecom/dist/`, `frontend-ecom/fe-*.log` | build + logs | **à ignorer** |
| `dump/` | export Mongo legacy | déjà ignoré (`.gitignore`) |
| `.venv/`, `.idea/` | environnement local / IDE | **à ignorer** (`.idea` déjà couvert) |
| `monitor/` | répertoire **vide** (vestige) | laissé en place, non versionné |

---

## 3. Inventaire composants

| Composant | Implémentation réelle | Preuve | Statut |
|---|---|---|---|
| `config-server` :8888 | Spring Cloud Config (profil `native`, `config-repo/`) | `services/config-server/pom.xml`, `application.yml` | **[V]** sain |
| `discovery-service` :8761 | Eureka Server, 5 applications enregistrées | `http://127.0.0.1:8761/eureka/apps` = 5 | **[V]** |
| `api-gateway` :8090→8080 | Spring Cloud Gateway, resource server JWT, Actuator mgmt 8081 interne | `SecurityConfig`, `config-repo/api-gateway.yml` | **[V]** sain |
| `product-service` :8081 | Catalogue — PostgreSQL + Elasticsearch + Redis + MinIO | `pom.xml` (jpa, elasticsearch, redis, minio) | **[V]** sain |
| `customer-service` :8082 | Clients — PostgreSQL (+ provisionnement clé sur `sub`) | `pom.xml` (jpa, postgresql), `config-repo/customer-service.yml` | **[V]** sain |
| `cart-service` :8083 | Paniers — MongoDB | `pom.xml` (data-mongodb) | **[V]** sain |
| `order-service` :8084 | Commandes — MongoDB, `CASH_ON_DELIVERY`/`PENDING` | `OrderServiceImpl` | **[V]** sain |
| `frontend-ecom` :5173 | SPA React 19 / Vite 7 / TS 5.9 / `keycloak-js` 26 | `frontend-ecom/package.json` | **[V]** 200 |
| Infra | PostgreSQL 16, MongoDB 7, Redis 7.4, Elasticsearch 8.13.4, MinIO, Keycloak 26.1 | `docker-compose.ecom.yml` | **[V]** sains |
| Observabilité | Prometheus 2.54.1 (7 cibles), Grafana 11.2.0 (dashboard 11 panneaux) | `monitoring/prometheus/prometheus.yml` | **[V]** |
| Kubernetes | 14 manifestes + kustomize + scripts (non déployés) | `k8s/` | **[C]** *non déployé* |

---

## 4. Vérifications d'exécution (13/10/2026, pile en fonctionnement)

### 4.1 Conteneurs et santé

| Contrôle | Résultat | Statut |
|---|---|---|
| `docker ps` | **16 services `Up (healthy)`** + `minio-init` `Exited (0)` + `mongo-test`/`postgres-test` `Up` (profil test) | **[V]** |
| Config Server / Eureka | `:8888` UP / `:8761` UP | **[V]** |
| Gateway | `:8090/api/...` répond ; Actuator sur port interne 8081 | **[V]** |
| Frontend | `http://127.0.0.1:5173/` → **200** | **[V]** |
| Keycloak / Prometheus / Grafana | `:8181/realms/pfeecom` → 200 ; `:9090/-/healthy` → 200 ; `:3000/api/health` → 200 | **[V]** |

### 4.2 Tests automatisés — décompte exact `@Test`

| Service | Classes de test | Tests | Résultat |
|---|---|---|---|
| product-service | API 22, images 8, ES 6, cache Redis 5, panne cache 2, MinIO 8, OpenAPI 1 | **52** | PASS |
| customer-service | API 9, provisionnement 7, OpenAPI 1 | **17** | PASS |
| cart-service | API 12, sécurité 6, OpenAPI 1 | **19** | PASS |
| order-service | API 11, sécurité 5, OpenAPI 1 | **17** | PASS |
| api-gateway | sécurité 19, propriété fiche client 6, sécurité docs 5 | **30** | PASS |
| **Total** | **19 classes** | **135** | **0 échec / 0 erreur / 0 ignoré** |

**Sécurité :** 36 tests de rôle/propriété (cart 6 + order 5 + gateway 19 + profil client 6),
plus 5 tests de sécurité des endpoints de documentation. Les rapports Surefire du 13/10/2026
(09:21–09:25) corroborent ce total.

### 4.3 Observabilité et API (live)

| Contrôle | Résultat | Statut |
|---|---|---|
| Prometheus cibles `up` (job `pfe-ecom`) | **7/7 up** | **[V]** |
| Eureka applications | **5** (gateway, customer, cart, order, product) | **[V]** |
| `GET /api/categories` (public) | 200 | **[V]** |
| `GET /api/products/search?q=bluetooth` | 200 | **[V]** |
| `GET /api/customers` (sans jeton) | **401** | **[V]** |

### 4.4 Persistance (lecture seule)

| Base | Contenu observé | Statut |
|---|---|---|
| MongoDB `pfe_carts` (défense) | 0 document | **[V]** |
| MongoDB `pfe_orders` (défense) | 7 documents | **[V]** |
| MongoDB isolé `mongo-test:27018` | `pfe_carts_test`, `pfe_orders_test`, `pfe_customers_test`, `pfe_products_test` | **[V]** |

---

## 5. Audit de sécurité (pré-publication)

| Élément | Constat | Statut |
|---|---|---|
| Fichiers `.env` | **aucun** | **[V]** |
| Clés privées / certificats / `.pem` / `.jks` | **aucun** | **[V]** |
| Secrets d'identifiants en dur | identifiants **de développement** par défaut (`admin/admin`, `yassine/yassine`, `postgres/postgres`, `MINIO_ROOT_PASSWORD:-pfeecom123`, `PROVISIONER_CLIENT_SECRET:-pfeecom-provisioner-secret`) dans `docker-compose.ecom.yml` et `keycloak/realm-pfeecom.json` | **[C]** WARNING |
| Historique Git | les fichiers sensibles actuels (`keycloak/`, `config-repo/`) **ne sont pas encore suivis** ⇒ aucune fuite en historique | **[V]** |
| `.gitignore` | incomplet (n'ignore ni `node_modules`, `dist`, `*.log`, `.venv`) | **[C]** WARNING |
| Tokens / PII | aucun jeton ou donnée personnelle dans les fichiers proposés | **[V]** |

**Évaluation.** Les seuls identifiants présents sont des **valeurs de démonstration locale**,
déjà publiquement documentées dans le README. Ils ne constituent pas des secrets de production,
mais **doivent être considérés comme à changer** avant tout déploiement réel. Aucun fichier
`.env` réel n'est suivi. **Recommandation :** conserver la pile de démo telle quelle (indispensable
à la reproductibilité) mais ajouter un `.env.example` **sans valeurs réelles** et mentionner
explicitement la rotation des identifiants.

---

## 6. Cohérence documentaire (incohérences détectées)

| # | Incohérence | Source | Action recommandée |
|---|---|---|---|
| 1 | `rapport/rapport.md` annonce **52 tests / 13 conteneurs** et l'ancienne architecture frontend | `rapport/rapport.md` | Note de mise à jour datée (doc historique) |
| 2 | `GUIDE_SOUTENANCE.md` : un commentaire de bloc indique « 13 conteneurs » alors que le corps dit **16** | GUIDE §9 | Corriger le commentaire → 16 |
| 3 | Total de tests différent selon les docs : 52 → 103 → 131 → **135** | plusieurs | Les docs historiques portent déjà une note ; le README réécrit fixe l'état courant |
| 4 | « 37 tests de sécurité » (README) vs 36 (GUIDE) | README/GUIDE | Fixer à **36** (rôle/propriété) + 5 (docs) |
| 5 | `PROFESSIONAL-UPGRADE-PLAN.md` : baseline tantôt 115, tantôt 103 | doc historique | Laisser (snapshot daté) |
| 6 | `PLATFORM-INTEGRATION-AUDIT.md` daté 11/10 mais rapporte l'état « Phase 6 » (2 jours plus tard) | doc historique | Laisser (snapshot daté) |

Les documents **courants et de référence** sont : `README.md`, `GUIDE_SOUTENANCE.md`,
`docs/RUNBOOK_ECOM.md`, et le présent audit. Les autres sont des **instantanés datés** à
préserver tels quels.

---

## 7. Limites connues (assumées, vérifiées)

- **Appels inter-services non authentifiés** : `cart`/`order` appellent `product`/`customer`
  sans propagation de jeton ; la protection repose sur l'isolation réseau Docker **[C]**.
- **Pas de transaction distribuée** ; cohérence par snapshots **[C]**.
- **Stock non décrémenté** lors d'une commande **[C]**.
- **Parcours navigateur authentifié panier → commande** : **NOT TESTED** (exigerait d'écrire
  des données métier) ; un smoke test navigateur **non destructif** (CDP) a en revanche été
  exécuté (rendu SPA, proxy `/api`, recherche ES, frontière 401, redirection OIDC) **[V]**.
- **Kubernetes non déployé** : manifestes validés statiquement (`kubectl kustomize`, `--dry-run=client`) ;
  le `--dry-run=server` complet est **BLOCKED** (le namespace éphémère n'est pas persisté).
- **Pas de TLS**, pas d'alerting Prometheus, pas de CI active pour cette pile **[C]**.
- **Réinitialisation de mot de passe** non testée (SMTP non configuré) **[N]**.

---

## 8. Plan de commits proposé (publication GitHub)

L'arbre de travail est une **migration** de l'ancien projet vers la plateforme microservices.
Proposition de commits logiques (aucune réécriture d'historique) :

1. `chore(repo): remove legacy pfe-flow stack and root compose`
2. `feat(services): add microservices backend (config, discovery, gateway, product, customer, cart, order)`
3. `feat(frontend): add React/Vite e-commerce SPA`
4. `chore(infra): add docker-compose, keycloak realm, config-repo, monitoring, k8s manifests`
5. `docs: rewrite README and add release audit`
6. `docs: rebuild PFE defense presentation and speaker notes`
7. `chore: harden .gitignore and drop build/log artifacts`

> Règle : inspecter le diff indexé avant chaque commit ; ne jamais `git add .` ; ne pas pousser
> sans URL de dépôt confirmée.

---

## 9. Verdict

**Prêt pour la soutenance** (pile fonctionnelle de bout en bout, 135 tests verts, sécurité
cohérente, observabilité opérationnelle). **Prêt pour la publication locale** ; la **poussée
GitHub reste BLOCKED** tant que l'URL du dépôt distant n'est pas confirmée. Deux actions de
durcissement sont recommandées avant publication : compléter `.gitignore` et documenter la
rotation des identifiants de démonstration.
