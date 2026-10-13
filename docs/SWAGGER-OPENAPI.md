# Documentation OpenAPI / Swagger — PFE E-commerce

> Phase 6. Contrat d'API documenté et **Swagger UI agrégée** exposée par l'API
> Gateway. Ce document ne décrit que des éléments **vérifiés par exécution**
> (tests + appels live via le gateway 8090). État : 2026-10-12.

---

## 1. Vue d'ensemble

Chaque service métier expose sa spécification **OpenAPI 3** (springdoc-openapi
`2.6.0`). Le gateway **agrège** les quatre spécifications dans une Swagger UI
unique et re-sert chaque document sous `/api-docs/<service>`.

| Composant | Techno | Spéc propre | Swagger UI |
|---|---|---|---|
| product-service | springdoc `webmvc-ui` | `/v3/api-docs` | `/swagger-ui.html` |
| customer-service | springdoc `webmvc-ui` | `/v3/api-docs` | `/swagger-ui.html` |
| cart-service | springdoc `webmvc-ui` | `/v3/api-docs` | `/swagger-ui.html` |
| order-service | springdoc `webmvc-ui` | `/v3/api-docs` | `/swagger-ui.html` |
| **api-gateway** | springdoc `webflux-ui` | — (agrégation) | **`/swagger-ui.html`** |

Les quatre services métier ne sont **pas publiés** sur l'hôte en Docker ; leurs
spécifications passent donc par le gateway. En exécution locale (JAR), chaque UI
reste joignable sur son port (`8081`–`8084`).

## 2. URLs vérifiées (Docker, via le gateway)

| URL | Résultat observé |
|---|---|
| `http://localhost:8090/swagger-ui.html` | **302** → `/webjars/swagger-ui/index.html` |
| `http://localhost:8090/webjars/swagger-ui/index.html` | **200** `text/html` (charge l'UI) |
| `http://localhost:8090/v3/api-docs/swagger-config` | **200** `application/json` (liste des 4 specs) |
| `http://localhost:8090/api-docs/product-service` | **200** `application/json` (OpenAPI 3.0.1) |
| `http://localhost:8090/api-docs/customer-service` | **200** `application/json` (OpenAPI 3.0.1) |
| `http://localhost:8090/api-docs/cart-service` | **200** `application/json` (OpenAPI 3.0.1) |
| `http://localhost:8090/api-docs/order-service` | **200** `application/json` (OpenAPI 3.0.1) |

Le `swagger-config` renvoie la liste attendue :

```json
{"configUrl":"/v3/api-docs/swagger-config",
 "oauth2RedirectUrl":"http://localhost:8090/webjars/swagger-ui/oauth2-redirect.html",
 "operationsSorter":"method",
 "urls":[{"url":"/api-docs/product-service","name":"Product Service"},
         {"url":"/api-docs/customer-service","name":"Customer Service"},
         {"url":"/api-docs/cart-service","name":"Cart Service"},
         {"url":"/api-docs/order-service","name":"Order Service"}],
 "validatorUrl":""}
```

## 3. Contenu du contrat (vérifié)

- `openapi: 3.0.1`, titres `Product/Customer/Cart/Order Service API`.
- **Chemins** : tous les endpoints métier (CRUD produits/catégories, images
  `/{id}/image`, recherche `search`/`suggest`/`reindex`, clients, paniers,
  commandes) présents dans les documents.
- **Schemes de sécurité** (sur les 4 services) :
  - `bearerAuth` : `type=http`, `scheme=bearer`, `bearerFormat=JWT` — coller un
    `access_token` Keycloak.
  - `oauth2` : `type=oauth2`, flux `authorizationCode` + **PKCE**, URLs
    `http://localhost:8181/realms/pfeecom/protocol/openid-connect/{auth,token}`,
    scope `openid`.
  - Le security requirement global est `bearerAuth`.
- `servers: [{"url":"/"}]` (serveur **relatif**) : « Try it out » cible
  l'origine qui sert la page (le gateway 8090 quand la doc est agrégée).
- Exemples d'erreurs documentés (`400 VALIDATION_FAILED`, `404 NOT_FOUND`,
  `409 DUPLICATE_RESOURCE`, `413 FILE_TOO_LARGE`, `503 STORAGE_UNAVAILABLE`,
  `401`, `403`).

## 4. Authentification (Keycloak)

### 4.1 Scheme bearer (manuel)

Récupérer un jeton puis coller sa valeur dans **Authorize → bearerAuth**.

```powershell
$body = @{ grant_type='password'; client_id='pfeecom-frontend'; username='admin'; password='admin'; scope='openid' }
$tok  = (Invoke-RestMethod -Method Post -Uri 'http://localhost:8181/realms/pfeecom/protocol/openid-connect/token' -Body $body).access_token
```

> Le grant « password » n'est utilisé que pour les scripts de démonstration/test
> (le client `pfeecom-frontend` l'autorise explicitement). Il ne sert **pas** à
> la SPA, qui reste en Authorization Code + PKCE.

### 4.2 Scheme oauth2 (Authorization Code + PKCE)

Swagger UI réalise le flux **PKCE S256** contre un client **public dédié**,
`pfeecom-swagger` — aucun secret n'est embarqué.

| Propriété | Valeur vérifiée (realm `pfeecom`) |
|---|---|
| `publicClient` | `true` |
| `standardFlowEnabled` | `true` (authorization code) |
| `directAccessGrantsEnabled` | `false` |
| `implicitFlowEnabled` | `false` |
| `serviceAccountsEnabled` | `false` |
| secret client | **absent** |
| `pkce.code.challenge.method` | `S256` |
| `redirectUris` | `http://localhost:8081/*`, `...8082/*`, `...8083/*`, `...8084/*`, **`...8090/*`** |
| `webOrigins` | `http://localhost:8081` … `http://localhost:8090` |

L'endpoint d'autorisation
`/realms/pfeecom/protocol/openid-connect/auth?...&code_challenge_method=S256`
renvoie **200** avec le formulaire de connexion Keycloak (aucune erreur
`invalid_client`). L'échange final du code exige une **saisie interactive** :
non automatisé (voir §7).

## 5. Tests

| Suite | Commande | Résultat |
|---|---|---|
| product-service | `mvn test` | **45/45** (43 + `ProductOpenApiTest`) |
| customer-service | `mvn test` | **10/10** (+ `CustomerOpenApiTest`) |
| cart-service | `mvn test` | **14/14** (+ `CartOpenApiTest`) |
| order-service | `mvn test` | **13/13** (+ `OrderOpenApiTest`) |
| api-gateway | `mvn test` | **21/21** (16 + 5 `GatewayDocsSecurityTest`) |
| **Backend total** | | **103 / 103** |

Les tests OpenAPI vérifient, sans réseau externe, que `/v3/api-docs` renvoie un
document valide avec les chemins, schémas et **schemes de sécurité** attendus. Le
test gateway vérifie que `/swagger-ui/**`, `/webjars/**`, `/v3/api-docs/**` et
`/api-docs/**` sont **publics**, alors qu'`/actuator/**` reste **refusé**.

## 6. Sécurité — la documentation est publique, pas les opérations

La Swagger UI et les spécifications sont **publiques** (elles n'exposent que la
description de l'API). Les appels « Try it out » restent soumis à
l'authentification et à la matrice d'autorisation du gateway. Vérifications
live (via 8090) :

| Requête | Jeton | Résultat |
|---|---|---|
| `GET /api/products`, `/api/categories`, `/api/products/search` | — | 200 (public) |
| `GET /api/carts/1`, `/api/orders`, `/api/customers`, `/api/customers/1` | — | 401 |
| `POST /api/products` | — | 401 |
| `GET /api/carts/1` (panier de yassine) | yassine | 200 |
| `GET /api/carts/2` (panier d'un autre) | yassine | **403** (propriété) |
| `GET /api/carts/1` (panier de yassine) | sarah | **403** (propriété) |
| `GET /api/customers` (listing) | yassine | 403 (non-ADMIN) |
| `GET /api/customers` (listing) | admin | 200 |
| `POST /api/products` `{}` | admin | 400 (auth/role passés, validation) |
| `GET /actuator/prometheus`, `/api-gateway/actuator/health` | — | 401 (non exposé) |

Aucune opération métier n'a été rendue publique par l'ajout de la documentation ;
aucune donnée métier n'a été créée lors de ces vérifications (le `POST` ADMIN
échoue volontairement sur la validation avant persistance).

## 7. Limites connues

- **Client sur realm existant** : l'import de `keycloak/realm-pfeecom.json`
  **n'applique pas** au realm déjà présent (Keycloak n'importe qu'à la création).
  Le client `pfeecom-swagger` a donc été créé **en direct** via l'API admin sur
  le realm en cours ; un déploiement **frais** l'obtient depuis l'export JSON.
  Pour un realm existant, recréer le client (ou supprimer/recréer le realm).
- **Login interactif NON TESTÉ** : le flux PKCE complet (saisie des identifiants
  puis échange du code) n'est pas automatisable sans navigateur ; seules la
  configuration du client et la réponse de l'endpoint d'autorisation (200,
  formulaire de connexion) sont vérifiées.
- **`springdoc.api-docs.enabled` doit rester `true` sur le gateway** : le passer
  à `false` supprime aussi les ressources statiques de Swagger UI
  (`SwaggerWebFluxConfigurer` non enregistré → `/webjars/swagger-ui/**` en 404).
  Le gateway expose donc aussi sa propre spécification minimale sur
  `/v3/api-docs`, utilisée par l'endpoint `/v3/api-docs/swagger-config`.
- Les specs re-servies sont **relatives à l'origine courante** ; les URLs
  OAuth2 des services pointent la **valeur publique** `http://localhost:8181`
  (le navigateur parle à Keycloak directement).

## 8. Référence de configuration

- `config-repo/{product,customer,cart,order}-service.yml` : `springdoc.api-docs.path`,
  `springdoc.swagger-ui.path`, `springdoc.swagger-ui.oauth.*`
  (`client-id: pfeecom-swagger`, `use-pkce-with-authorization-code-grant: true`,
  `scopes: [openid]`) et `app.openapi.oauth.{authorization-url,token-url}`.
- `config-repo/api-gateway.yml` : routes `*-docs` (`/api-docs/<service>` →
  `SetPath=/v3/api-docs`), `springdoc.api-docs.enabled: true`,
  `springdoc.swagger-ui.urls` (les 4 services), `disable-swagger-default-url: true`.
- `<service>/.../config/OpenApiConfig.java` : métadonnées (`info`, `servers`,
  licence) + `bearerAuth` + `oauth2`.
- `services/api-gateway/.../config/SecurityConfig.java` : `permitAll` sur
  `/swagger-ui.html`, `/swagger-ui/**`, `/webjars/**`, `/api-docs/**`,
  `/v3/api-docs/**` ; `/actuator/**` et `/*/actuator/**` restent `denyAll`.
- `keycloak/realm-pfeecom.json` : client public `pfeecom-swagger`.
