# Professional Upgrade Plan - PFE E-commerce

## Goal
Extend the existing E-commerce microservices platform (Phases 0-8: 103/103 tests,
aggregated Swagger, Elasticsearch/MinIO, security verified) with a professional
storefront polish: Redis caching, guest cart with post-login merge, guest-friendly
auth navigation, a dedicated admin panel, tests and documentation.

## Constraints
PS 5.1; no destructive git/volume operations; preserve all data volumes; never
touch `bevaren-keycloak:8080`; never print secrets or tokens; never weaken
JWT/RBAC/ownership; label any unexercised browser flow as NOT TESTED.

---

## Phase status

| Phase | Scope | Status | Evidence |
|-------|-------|--------|----------|
| A | Redis infra + product/category caching | PASS | see below |
| B | Guest cart + authoritative merge | PASS | backend + live |
| C | Login/Register nav + guest SSO | PASS | build + live bundle |
| D | Account / checkout routes | PASS | routes wired |
| E | `/admin_panel` with real KPIs | PASS | ADMIN-gated |
| F | UI/UX polish | PARTIAL | scoped to cart/auth UX |
| G | Tests + regression | PASS | 131/131 backend |
| H | Documentation | PASS | this file + runbook |
| I | IDOR hotfix : propriété `GET /api/customers/{id}` | PASS | 6 tests gateway + live 403 |
| J | Provisionnement automatique du client (auto-inscription) | PASS | 7 tests unitaires + E2E live |

---

## Phase 1 - Baseline (re-audit 2026-10-12)

- **Runtime** : compose `pfe` — config-server, discovery, gateway, product,
  customer, cart, order, keycloak, postgres, mongo, redis, elasticsearch, minio,
  prometheus, grafana, frontend ; `minio-init` `Exited(0)` ; `*-test`
  (postgres 5433, mongo 27018, redis 6380) en profil `test`. Tous les services
  applicatifs `healthy` ; matrices d'autorisation et propriété vérifiées via le
  gateway `:8090` (401/403/200).
- **Config** : `config-repo/` distribué par config-server (fail-fast + retry) ;
  aucun secret en clair (variables d'environnement) ; pas de `.env`.
- **Référentiel** : schéma `customer-service` en **V3** (`keycloak_sub`), produits/
  catégories/commandes intacts.
- **Tests avant mission** : 115 (`product 52, customer 10, cart 19, order 13,
  gateway 21`). Après mission : **131**.

## Phase A - Redis caching
- `redis` service added to `docker-compose.ecom.yml` (internal only, appendonly)
  plus `redis-test` (profile `test`, host port `6380`).
- `spring-boot-starter-data-redis`; cache-aside in `ProductServiceImpl` and
  `CategoryController`; 5-minute TTL; JDK value serialization; String keys.
- Graceful degradation: a `CacheErrorHandler` logs and swallows every Redis
  error, so a Redis outage never breaks a business request (verified by
  `ProductCacheOutageTest`).
- Invalidation: product/category writes evict `products`, `product` and
  `categories`.
- Live: listing key `products::0-5` 245 ms -> 42 ms; `categories::all` TTL 299 s,
  171 ms -> 53 ms.

## Phase B - Guest cart + merge
- Guest cart persisted client-side (`localStorage`, schema-validated, no secrets);
  unified `CartProvider` selects guest or server cart by session.
- Authoritative backend merge `POST /api/carts/{customerId}/merge`; ownership is
  enforced server-side; merge is **idempotent** (a product already present keeps
  `max(existing, incoming)`, so a retry after a network failure never doubles a
  line). The guest cart is cleared only after a confirmed merge response.
- Not authenticated -> the checkout area offers "Se connecter pour commander",
  which preserves the current path and returns the shopper to `/cart` after login.

## Phase C - Auth navigation
- `initAuth` now uses `onLoad: 'check-sso'` with `silentCheckSsoRedirectUri`
  (`/silent-check-sso.html`), so guests browse the catalogue and admins/customers
  keep their session across reloads.
- `AuthProvider` is reactive to `onAuthSuccess/onAuthRefreshSuccess/onAuthLogout`.
- Navbar shows `Connexion` / `Inscription` for guests and the account chip +
  `Déconnexion` for authenticated users; registration is enabled in the realm
  (`registrationAllowed: true`).

## Phase D - Account area
Routes: `/account`, `/account/profile`, `/account/orders`, `/orders`, `/orders/:id`,
`/cart`, `/checkout`. `/orders*` and `/account*` are guarded by `RequireAuth`
(UX guard; the gateway still performs the real authorization).

## Phase E - Admin panel
- `/admin_panel` (ADMIN-gated by `RequireAdmin` + backend RBAC) with real KPIs
  (products, categories, customers, orders) and modules. `/admin` redirects to it.
- Customer denial is enforced server-side (gateway returns 403 for non-ADMIN on
  mutation endpoints), not only by the route guard.

## Phase I - IDOR hotfix (`GET /api/customers/{id}`)
A customer could read **any** customer's profile by changing the path id. Fixed in
the gateway with `CustomerProfileAuthorizationManager` : the path id must equal the
tokened `customer_id` (or the caller is ADMIN). Internal calls are unaffected.
`CustomerProfileOwnershipTest` (6) ; live : customer1 `-> /2` = 403, `-> /1` = 200,
ADMIN `-> /2` = 200, anon `-> /2` = 401.

## Phase J - Provisionnement automatique du client
Self-registration created a Keycloak account with **no** business profile, so a new
user could not order. A **server-side**, **`sub`-keyed**, **idempotent**
provisioning flow was added (details and runbook : `docs/RUNBOOK_ECOM.md` §3ter).

- **Endpoint** : `POST /api/customers/me` (`customer-service`, resource server — lit
  uniquement le `sub` du jeton ; gateway : authentifié, règle **avant** le catch-all
  ADMIN). L'`id` client **n'est jamais** pris du navigateur.
- **Nouvelle colonne** : `keycloak_sub` (UNIQUE, nullable) — migration Flyway **V3**.
- **Idempotent** : même `sub` `->` même profil ; email normalisé ; rejeu sans doublon.
- **Comptes existants préservés** : un jeton portant déjà un claim `customer_id`
  (seed ou profil pré-créé) **rattache** le profil au `sub` (id conservé) au lieu
  de recréer ; `yassine` (`customer_id=1`) vérifié en live (id=1, idempotent).
- **Privilèges côté serveur** : client `pfeecom-provisioner` (service account
  `manage-users`) matérialise le claim `customer_id` ; secret via
  `PROVISIONER_CLIENT_SECRET`. `KeycloakAdminClient` ne journalise ni secret ni jeton.
- **Erreurs** : `401`, `400 INVALID_PROFILE`, `503 PROVISIONING_UNAVAILABLE` ;
  aucune PII journalisée ; la ligne créée survit à un échec de matérialisation du claim
  (retentée au prochain appel).
- **Keycloak 26** : le *user profile* déclaratif ignore silencieusement (HTTP 200)
  l'écriture d'un attribut « non managé » tant que `unmanagedAttributePolicy` n'est
  pas `ADMIN_EDIT` (posé par `keycloak/scripts/setup-provisioner-client.ps1` + realm JSON).
- **Tests** : `ProvisioningServiceImplTest` (7). **E2E live** : nouveau compte
  `-> POST /me` id (ms) `->` rejeu id idem `->` jeton rafraîchi `customer_id` matérialisé
  `->` `GET` propre 200, autre 403 `->` panier serveur 200 `->` commande créée.

## Phase G - Test matrix (backend)

| Service | Tests | Result | Command |
|---------|-------|--------|---------|
| product-service | 52 | PASS | `mvn -q test` |
| cart-service | 19 | PASS | `mvn -q test` |
| customer-service | 17 | PASS | `mvn -q test` |
| order-service | 13 | PASS | `mvn -q test` |
| api-gateway | 30 | PASS | `mvn -q test` |
| **Total** | **131** | **PASS** | (baseline 103, delta +28) |

Re-run intégral (2026-10-12) : `tests=131 fail=0 err=0 skip=0` (product 52,
customer 17, cart 19, order 13, gateway 30).

Frontend: `npm run typecheck` = 0 errors; `npm run lint` = 0 errors (5
pre-existing warnings); `npm run build` = OK. `redis-test` and Elasticsearch
tests are reachability-guarded as before.

### Live verification (through the gateway, `:8090`)
```
merge unauth  => 401   (expect 401)
merge other   => 403   (expect 403)   cross-customer, real token
merge owner   => 200   (expect 200)
merge replay  => 200   (expect 200)
qty after 2 identical merges of qty=2 => 2   (idempotent)
GET /silent-check-sso.html => 200
deployed bundle contains: "Se connecter pour commander", "/admin_panel",
                          "silent-check-sso.html", "/merge"
```

---

## Limitations / not done
- **Frontend automated tests**: the project has no JS test runner (no vitest/jest
  and no `test` script). Adding one was out of scope; frontend behaviour is
  covered by `tsc`, eslint, the production build and the backend merge tests.
- **Interactive browser flows NOT TESTED**: full click-through of guest add ->
  login -> merge toast, and the admin panel screens, were not driven by a browser
  automation tool in this environment. The deployed bundle and every API contract
  behind them were verified directly.
- **Self-registered users**: **resolved** (Phase J). Registration still creates
  only the Keycloak account, but the first authenticated call to
  `POST /api/customers/me` now provisions the business profile and materialises
  the `customer_id` claim - no administrator required.
- **Keycloak realm import** applies on a fresh realm only; live realm edits must
  be made through the Admin API. In particular the `customer_id` "unmanaged
  attribute" needs `unmanagedAttributePolicy=ADMIN_EDIT` (Keycloak 26); apply
  `keycloak/scripts/setup-provisioner-client.ps1` on an existing realm.

## New / relevant configuration
- `docker-compose.ecom.yml`: `redis`, `redis-test` (+ `redis-data` volume).
- `config-repo/product-service.yml`: `spring.cache.redis.time-to-live=300000`,
  `spring.data.redis.*`.
- `services/cart-service`: `POST /api/carts/{customerId}/merge` (idempotent).
- Frontend `public/silent-check-sso.html` (served at `/silent-check-sso.html`).
- `services/customer-service`: `POST /api/customers/me` + `V3__keycloak_provisioning.sql`
  (`keycloak_sub`), `config/SecurityConfig.java` (resource server), `client/KeycloakAdminClient.java`,
  `service/impl/ProvisioningServiceImpl.java` ; `app.security.*` / `app.provisioning.*` dans
  `config-repo/customer-service.yml`.
- `keycloak/realm-pfeecom.json` (client `pfeecom-provisioner`, service account,
  `unmanagedAttributePolicy=ADMIN_EDIT`) ; `keycloak/scripts/setup-provisioner-client.ps1`.
- `docker-compose.ecom.yml`: `PROVISIONER_CLIENT_SECRET` (customer-service).
- Frontend `src/lib/api.ts` (`provisionCustomer`), `src/app/AuthProvider.tsx`
  (effet de provisionnement + `retryProvision`), `src/components/AppLayout.tsx` (bannière).
