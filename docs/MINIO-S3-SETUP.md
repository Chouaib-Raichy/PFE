# Stockage d'images produit — MinIO (S3) — Phase 5

> Objectif : permettre à un **ADMIN** de téléverser/supprimer l'image d'un
> produit, servie publiquement via le catalogue, avec conservation de l'objet
> binaire dans un stockage objet compatible S3 (**MinIO**).

---

## 1. Décision d'image et limite assumée

| Candidat | Résultat | Conclusion |
|---|---|---|
| `minio/minio` (Docker Hub) | **404** (retiré) | indisponible |
| `quay.io/minio/minio` | **401** | indisponible |
| **`bitnamilegacy/minio:2025.7.23-debian-12-r5`** | tiré, digest vérifié | **retenu** |

- Image retenue **épinglée par digest** :
  `sha256:6dabb4a2088c9a79908de3bc05f4586c23ad2182c8908e7e3acbf61c1467fb20`.
- Serveur MinIO authentique `DEVELOPMENT.2025-07-23T15-54-02Z`, licence
  **AGPLv3**.
- **Limite assumée** : espace de noms **`bitnamilegacy`** = miroir **gelé**
  (legacy) — image non mise à jour en amont. Reproductibilité garantie par le
  **digest** épinglé. Repli documenté si un jour unpullable :
  `minio/minio:latest` (`RELEASE.2025-09-07T16-13-09Z`, digest
  `sha256:14cea493…`).
- Exécute en **UID 1001** ; données dans `/bitnami/minio/data`.
- **Pas de console web** : les versions récentes de MinIO ont retiré la console ;
  le port **9001 n'est pas exposé**. Seul `127.0.0.1:9000` (API S3) est publié,
  **lié à la boucle locale**.

---

## 2. Modèle de données

- Migration Flyway **`V4__product_image_key.sql`** : `ALTER TABLE products ADD
  COLUMN image_key VARCHAR(300)` (additive, non destructive).
- `Product.imageUrl` = **URL publique** consommée par le client
  (`/api/products/{id}/image`).
- `Product.imageKey` = **clé d'objet interne S3** (`products/{id}/…`), **jamais
  exposée** dans les réponses API.
- **Clés d'objet générées côté serveur uniquement** :
  `products/{id}/{yyyyMMddHHmmss}-{8 hex}.{ext}` — aucune donnée client
  n'entre dans la clé (pas de path traversal).

---

## 3. Endpoints

| Méthode | Chemin | Accès | Réponse |
|---|---|---|---|
| `POST` | `/api/products/{id}/image` | **ADMIN** | `200` + produit (avec `imageUrl`) |
| `DELETE` | `/api/products/{id}/image` | **ADMIN** | `204` |
| `GET` | `/api/products/{id}/image` | **public** | octets + `Content-Type` + `Cache-Control: public, max-age=86400` |

- `POST` : `multipart/form-data`, champ `file`. Validations :
  - taille **≤ 5 Mo** (`max-image-bytes`) ;
  - type **détecté par signature binaire** (JPEG/PNG/WEBP/GIF) et **cohérent**
    avec le type déclaré ;
  - vide / non supporté / type incohérent → **400** `INVALID_IMAGE` ;
  - trop volumineux → **413** `FILE_TOO_LARGE` ;
  - stockage indisponible/désactivé → **503** `STORAGE_UNAVAILABLE`.
- Sur **sécurité** : `POST`/`DELETE` exigés ADMIN par le gateway ; `GET` public.

### Remplacement sans perte

- Nouvel objet stocké (hors transaction) → `imageKey`/`imageUrl` mis à jour +
  écriture produit/outbox (transaction) → **suppression de l'ancien objet
  uniquement APRÈS COMMIT** (`TransactionSynchronizationManager
  .registerSynchronization(... afterCommit)`).
- Échec de sauvegarde → nouvel objet supprimé (pas d'orphelin).
- Échec d'un **remplacement** (ex. fichier invalide) → l'ancienne image reste
  intacte (aucune modification d'état).

---

## 4. Infrastructure (`docker-compose.ecom.yml`)

- Service **`minio`** :
  - image épinglée + digest ci-dessus ;
  - `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD` (défauts `${VAR:-…}`, jamais en
    clair) ;
  - port **`127.0.0.1:9000:9000`** (boucle locale) ;
  - volume **`minio-data`** → `/bitnami/minio/data` ;
  - healthcheck `curl -f http://localhost:9000/minio/health/live` ;
  - réseau `pfe-net`.
- Service one-shot **`minio-init`** (même image, `HOME=/tmp`) :
  - `depends_on: minio (condition: service_healthy)` ;
  - monte `docker/minio/init-bucket.sh` + `docker/minio/product-images-policy.json` ;
  - entrypoint `/bin/sh /init/init-bucket.sh` ; **sort en 0** une fois fait.
- **`product-service` ne dépend PAS de `minio`** (démarrage indépendant,
  dégradation gracieuse).
- Variables `product-service` : `MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY`
  (défauts `${MINIO_APP_ACCESS_KEY:-pfeecom-app}` /
  `${MINIO_APP_SECRET_KEY:-pfeecom-app-secret}`).

### Bootstrap (`docker/minio/init-bucket.sh`)

1. `mc alias set` (root) + attente ;
2. `mc mb --ignore-existing` du bucket `pfeecom-product-images` ;
3. `sed __BUCKET__ → $MINIO_BUCKET` sur `product-images-policy.json` →
   `/tmp/policy.json` ;
4. `mc admin user add` (utilisateur applicatif `pfeecom-app`) ;
5. `mc admin policy create` + `mc admin policy attach --user`.

### Moindre privilège (`docker/minio/product-images-policy.json`)

- Sur le bucket : `GetBucketLocation`, `ListBucket`.
- Sur `{bucket}/*` : `GetObject`, `PutObject`, `DeleteObject`.
- **Pas de** `s3:CreateBucket`, ni d'accès aux autres buckets : l'application
  n'a que les droits nécessaires (le bucket est créé par `minio-init`).

---

## 5. Configuration service

`config-repo/product-service.yml` :

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 6MB
app:
  storage:
    enabled: ${STORAGE_ENABLED:true}
    endpoint: ${MINIO_ENDPOINT:http://minio:9000}
    access-key: ${MINIO_ACCESS_KEY:pfeecom-app}
    secret-key: ${MINIO_SECRET_KEY:pfeecom-app-secret}
    bucket: ${MINIO_BUCKET:pfeecom-product-images}
    max-image-bytes: 5242880
```

- `product-service-local.yml` : `app.storage.endpoint=${MINIO_ENDPOINT:http://localhost:9000}`.
- `api-gateway.yml` : `spring.codec.max-in-memory-size: 6MB` (le gateway relaie
  le multipart).
- Tests (`src/test/resources/application.yml`) : limites multipart +
  `app.storage.enabled=false`.
- Dépendance : `io.minio:minio:8.5.17`.

Le client MinIO n'est construit que si `app.storage.enabled=true` ; à défaut, la
boutique fonctionne sans images (aucune erreur bloquante).

---

## 6. Vérifications exécutées

| Contrôle | Résultat |
|---|---|
| `mvn test` product-service | **44/44** (22 + 8 images + 6 ES + 8 stockage) |
| `mvn test` api-gateway | **16/16** (+6 autorisation images) |
| Frontend `typecheck`/`lint`/`build` | **0 erreur** (5 avert. `react-refresh` préexistants) / build OK |
| Conteneurs | `minio` **healthy**, `minio-init` **Exited (0)**, `product-service` healthy |
| `minio-init` (logs) | bucket `pfeecom-product-images` + utilisateur `pfeecom-app` créés |
| Upload ADMIN valide | **200**, `imageUrl=/api/products/1/image` |
| Métadonnées persistées | `image_key=products/1/<ts>-<hex>.png` (colonne) |
| Récupération `GET /{id}/image` | **200**, `Content-Type: image/png`, octets identiques (SHA-256) |
| Upload sans jeton | **401** |
| Upload jeton CUSTOMER | **403** |
| Fichier invalide | **400** `INVALID_IMAGE` |
| Fichier > 5 Mo | **413** `FILE_TOO_LARGE` |
| Remplacement | **200**, nouvelle clé ; ancien objet supprimé (1 seul objet dans le bucket) |
| Remplacement échoué (fichier invalide) | **400** ; image précédente conservée (clé + octets inchangés) |
| Utilisateur applicatif | `pfeecom-app` présent, **policy `pfeecom-product-images` attachée** |
| Persistance après `docker restart pfe-minio-1` (volume conservé) | objet toujours présent, `GET` **200** |

> Vérifications réalisées via le **gateway (8090)** et la **même base que la
> production** (PostgreSQL `product postgres`), pas en environnement simulé.

---

## 7. Exploitation rapide

```powershell
# Bucket + objets (identifiants root lus dans l'environnement du conteneur)
docker exec pfe-minio-1 sh -c 'mc alias set a http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null; mc ls --recursive a/pfeecom-product-images'

# Utilisateur applicatif + policy
docker exec pfe-minio-1 sh -c 'mc alias set a http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null; mc admin user list a; mc admin policy entities a --user pfeecom-app'

# Redémarrer le stockage SANS supprimer le volume
docker compose -f docker-compose.ecom.yml restart minio
```

- **Arrêt** : `docker compose -f docker-compose.ecom.yml stop minio` — ne jamais
  faire `down -v` (supprimerait `minio-data`).
- Endpoint interne : `http://minio:9000` ; hôte : `http://127.0.0.1:9000`.
