# Rapport d'acceptation — Mission « Complete PFE E-commerce Platform »

Date : 2026-10-12 · Périmètre : `D:\PROJECTS\PFE` · Mode : incrémental (aucun
rebaseline, aucune donnée détruite, aucun commit).

Ce rapport ne consigne que ce qui a été **effectivement exécuté et vérifié**
dans cette session. Tout ce qui n'a pas pu être exécuté est marqué **NOT TESTED**.

> **Mise à jour postérieure — 13/10/2026 (instantané historique préservé).** Ce rapport reste un
> **instantané du 2026-10-12** : ses chiffres (131 tests backend, order-service 13) sont conservés
> tels quels, non falsifiés. Depuis, des tests **OpenAPI** ont été ajoutés sur chaque service ; le
> total **actuel vérifié** est de **135 tests** (product 52, customer 17, cart 19, **order 17** —
> 13 → 17, gateway 30), 0 échec/erreur/ignoré (rapports Surefire du 13/10/2026). La **Phase 7
> (E2E navigateur)** : un **smoke test navigateur non destructif** a depuis été exécuté (13/10/2026,
> **Chrome headless + CDP**, zéro dépendance ajoutée) — rendu SPA, proxy `/api`, recherche
> Elasticsearch, frontières 401 et **redirection OIDC** vérifiés ; le parcours **authentifié
> panier → commande** reste **NOT TESTED** (exigerait d'écrire des données métier). Cf. §5.

---

## 1. Statut par phase

| Phase | Objet | Statut | Preuve |
|-------|-------|--------|--------|
| 1 | Ré-audit runtime / config / tests (baseline) | **PASS** | conteneurs sains, `config-repo/`, schéma V3, tests |
| 2 | Provisionnement automatique du client | **PASS** | 7 tests unitaires + E2E live |
| 3 | Parcours client (panier invité, fusion, checkout) | **PASS** | fusion idempotente live, 19 tests cart |
| 4 | Compte / profil + isolation | **PASS** | matrice live 200/403, IDOR fermé |
| 5 | Espace d'administration + RBAC | **PASS** | ADMIN 200 / CUSTOMER 403, reindex |
| 6 | Cache Redis (correction / résilience) | **PASS** | TTL 299 s, repli couvert par test |
| 7 | E2E navigateur (clics) | **NOT TESTED** | aucun outil d'automatisation installé |
| 8 | Régression complète | **PASS** | 131 tests backend + build frontend |
| 9 | Documentation + rapport | **PASS** | RUNBOOK §3ter, README, ce rapport |

---

## 2. Phase 2 — Provisionnement automatique (livrable central)

**Design** : à la première connexion d'un compte Keycloak sans profil, la SPA
appelle `POST /api/customers/me`. `customer-service` (resource server OIDC, ne
lit que le `sub`) crée le profil **côté serveur**, **clé sur le `sub`**,
**idempotent**, puis matérialise le claim `customer_id` via le client serveur
`pfeecom-provisioner`. L'`id` client **n'est jamais** accepté du navigateur.

- Colonne `keycloak_sub` (UNIQUE, nullable) — migration **V3** appliquée en live.
- Comptes existants **préservés** : un jeton portant déjà `customer_id` **relie**
  le profil (id conservé) au lieu de le recréer.
- Secrets privilégiés **côté serveur** uniquement (`PROVISIONER_CLIENT_SECRET`) ;
  `KeycloakAdminClient` ne journalise ni secret ni jeton ; aucune PII journalisée.
- Erreurs : `401`, `400 INVALID_PROFILE`, `503 PROVISIONING_UNAVAILABLE`.

**Tests unitaires** : `ProvisioningServiceImplTest` **7/7** (nouveau profil,
idempotence, rafraîchissement du claim, liaison d'utilisateur existant, email
dupliqué, email manquant, concurrence).

**E2E live (exécuté)** — nouveau compte Keycloak sans `customer_id` :
1. jeton initial : `customer_id` **absent** ;
2. `POST /api/customers/me` → profil créé (ms) ;
3. rejeu → **même** `id` (idempotent) ;
4. jeton **rafraîchi** → `customer_id` **matérialisé** ;
5. `GET /api/customers/<id>` avec le **nouveau** jeton → **200** ; avec un jeton
   **sans** le claim → **403** (le jeton est la référence, pas le navigateur) ;
6. panier serveur → 200 ; commande créée.
7. **Nettoyage** : profil + utilisateur Keycloak + commande + panier de test
   supprimés ; table `customers` revenue aux comptes seedés (1, 2).

**Cas utilisateur seedé (live)** : `POST /me` pour `yassine` → `id=1` (profil
existant **relié**, id conservé), rejeu idempotent.

**Vérification navigateur du bundle** : le bundle déployé contient
`customers/me`, `provision`, `retryProvision` (fonctionnalité expédiée).

---

## 3. Phase 8 — Régression (relancée intégralement)

| Service | Tests | Résultat |
|---------|-------|----------|
| product-service | 52 | PASS |
| customer-service | 17 | PASS |
| cart-service | 19 | PASS |
| order-service | 13 | PASS |
| api-gateway | 30 | PASS |
| **Total backend** | **131** | **fail=0 err=0 skip=0** |

Frontend (`frontend-ecom`) : `npm run build` **OK** (tsc `--noEmit` + vite) ;
`npm run lint` **0 erreur / 5 avertissements préexistants** ; `silent-check-sso.html`
servi **200**.

---

## 4. Vérifications live via le gateway (`:8090`)

| Vérification | Attendu | Obtenu |
|---|---|---|
| `GET /api/products`, `/categories`, `/search` (public) | 200 | 200 |
| `POST /api/customers/me` sans jeton | 401 | 401 |
| `GET /api/customers/1` jeton propriétaire (id 1) | 200 | 200 |
| `GET /api/customers/2` jeton client 1 (IDOR) | 403 | 403 |
| `GET /api/customers/2` jeton ADMIN | 200 | 200 |
| `POST /api/customers` jeton CUSTOMER | 403 | 403 |
| `POST /api/products/reindex` ADMIN / CUSTOMER | 200 / 403 | `{"indexed":3}` / 403 |
| `POST /api/carts/1/merge` propriétaire + rejeu | 200, idempotent | total stable |
| `GET /api/carts/2` jeton client 1 | 403 | 403 |
| `GET /api/orders` jeton client / anonyme | 200 / 401 | 200 / 401 |
| Cache Redis `TTL products::0-5` | ≤ 300 s | 299 s |
| Recherche `q=docker` | `degraded=false` | `degraded=false`, 1 résultat |

---

## 5. NOT TESTED (honnêteté)

- **E2E navigateur interactif (Phase 7)** : aucun outil d'automatisation
  (Playwright / Puppeteer / Cypress) ni *test runner* JS n'est installé, et
  l'ajout d'une dépendance était hors périmètre. Les parcours cliquables
  (ajout invité → connexion → fusion + toast, écrans admin) n'ont **pas** été
  pilotés par un navigateur. Vérifiés à la place : contrats d'API (ci-dessus),
  bundle déployé, test statique de `silent-check-sso.html`, et rendu/navigation
  non automatisés.
- **Réinitialisation de mot de passe** : SMTP non configuré (démo) — non testée.
- **Observabilité** : Prometheus/Grafana non re-vérifiés exhaustivement cette
  session (déjà couverts par `docs/RUNBOOK_ECOM.md` §10).

---

## 6. Contraintes respectées

Aucun `git commit`/`reset`/`rebase` ; aucun `down -v`/`volume prune` ; données
PostgreSQL/MongoDB/Keycloak/MinIO/ES/Redis préservées ; `bevaren-keycloak`
(8080) non touché ; `rapport/`, `soutenance/`, `archive/pfe-flow/` intacts ;
aucun secret/jeton/PII imprimé ; aucune sécurité affaiblie ; aucun enregistrement
de test laissé en base.

## 7. Limites connues

- L'**import JSON** du realm ne s'applique qu'à un realm **neuf** ; sur un realm
  existant, exécuter `keycloak/scripts/setup-provisioner-client.ps1` (Keycloak 26 :
  `unmanagedAttributePolicy=ADMIN_EDIT`, sinon l'écriture de `customer_id` par
  l'Admin API est silencieusement ignorée).
- La liaison d'un utilisateur seedé ajoute `keycloak_sub` à sa ligne (id/email
  conservés) — comportement voulu, non destructif.
