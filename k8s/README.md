# Deploiement Kubernetes local — PFE E-commerce

Variante Kubernetes **locale** de la pile e-commerce PFE, destinee a
**Docker Desktop** (Windows). Elle **coexiste** avec Docker Compose : elle ne
modifie ni les fichiers Compose, ni les donnees existantes, ni la configuration
applicative.

## Objectif et portee

- Reproduire la meme pile que `docker-compose.ecom.yml` dans un namespace dedie
  `pfe-ecommerce`.
- Reutiliser **les images applicatives locales telles quelles** (aucune
  modification de code, de config ou de securite).
- Bases de donnees, Keycloak, Elasticsearch, MinIO et Redis **neufs** dans le
  cluster (PVC dedies). **Aucune donnee Compose n'est reprise** : c'est un
  environnement isole.

## Prerequis

- Docker Desktop avec **Kubernetes active** (Settings > Kubernetes > Enable Kubernetes).
- `kubectl` (fourni par Docker Desktop).
- Images applicatives deja construites (`docker compose -f docker-compose.ecom.yml build`) :
  `pfe-config-server`, `pfe-discovery`, `pfe-product-service`,
  `pfe-customer-service`, `pfe-cart-service`, `pfe-order-service`,
  `pfe-api-gateway`, `pfe-frontend-ecom` (`:latest`).
- Docker Desktop doit partager ces images avec le cluster (cas par defaut).

## Arborescence

```
k8s/
  namespace.yaml                 Namespace pfe-ecommerce
  kustomization.yaml             Point d'entree kustomize
  configmaps/
    postgres-init.yaml           Script d'init PostgreSQL (non sensible)
    minio-init.yaml              Script + politique MinIO (non sensible)
  secrets/
    README.md                    Le Secret est genere (jamais committe)
  infrastructure/                postgres, mongo, redis, elasticsearch,
                                 minio, minio-init (Job), keycloak
  services/                      config-server, discovery, product, customer,
                                 cart, order, api-gateway
  frontend/frontend-ecom.yaml    Nginx (proxy /api/ -> api-gateway:8080)
  keycloak/Dockerfile            Image Keycloak + theme "pfeecom" + royaume
  scripts/
    deploy.ps1                   Deploiement complet
    generate-secrets.ps1         Creation du Secret pfe-secrets
    port-forward.ps1             Acces local (frontend/API/Keycloak)
```

## Deploiement

```powershell
# 1. Construire les images applicatives (une fois ; via le processus existant)
docker compose -f docker-compose.ecom.yml build

# 2. Deployer
pwsh -File k8s/scripts/deploy.ps1

# 3. Acceder a l'application
pwsh -File k8s/scripts/port-forward.ps1
```

`deploy.ps1` : verifie le cluster, construit l'image Keycloak derivee si besoin,
cree le namespace, projette `config-repo/` en ConfigMap, genere le Secret
`pfe-secrets`, applique les manifestes puis attend les deploiements.

## Acces

| Service   | URL locale              | Via                |
|-----------|-------------------------|--------------------|
| Frontend  | http://localhost:5173   | port-forward 5173  |
| API       | http://localhost:8090   | port-forward 8090  |
| Keycloak  | http://localhost:8181   | port-forward 8181  |

`KC_HOSTNAME=http://localhost:8181` et l'`issuer-uri` par defaut correspondent
exactement a ce port-forward : les jetons sont valides sans surcharge.

## Principes retenus (pourquoi cela fonctionne sans rebuild)

1. **Services nommes comme Compose** (`config-server`, `discovery`, `postgres`,
   `mongo`, `redis`, `elasticsearch`, `minio`, `keycloak`, `api-gateway`,
   `product-service`, ...). Les URL `configserver:http://config-server:8888`,
   `http://discovery:8761/eureka`, `mongodb://mongo:27017/...` etc. sont donc
   resolues par le DNS du cluster **sans changer `config-repo/`**.
2. **`config-repo/` projete en ConfigMap** et monte sur `/config-repo` :
   source unique de verite, aucune duplication de configuration.
3. **Secrets generes a l'execution** (jamais committes) ; references via
   `secretKeyRef` uniquement. Aucun identifiant en clair dans les manifestes.
4. **Keycloak** : le theme `pfeecom` (sous-repertoires) et le royaume ne
   peuvent pas tenir dans un ConfigMap ; une image derivee
   (`pfe-keycloak-ecom:latest`) les embarque, comme Compose les monte.
5. **Pas de `depends_on` en K8s** : des `initContainers` (busybox) attendent
   `config-server`, `discovery` et la base requise avant le demarrage.
6. **Probes reelles** sur `/actuator/health` (ou `/health/ready` pour Keycloak)
   + `startupProbe` pour absorber les demarrages longs.

## Isolation des donnees

Chaque dependance a un PVC dedie dans le namespace, independant des volumes
Compose (`pfe_*`) :

`postgres-data`, `mongo-data`, `redis-data`, `elasticsearch-data`,
`minio-data`, `keycloak-data`.

## Teardown (sur, sans toucher a Compose)

```powershell
# Supprime uniquement les ressources du namespace K8s.
kubectl delete namespace pfe-ecommerce
```

> N'utilisez **jamais** `docker compose down -v`, `docker volume prune` ni
> `docker system prune` : cela detruirait les donnees Compose.

## Limites connues

- Elasticsearch : necessite `vm.max_map_count >= 262144` sur le noeud. La VM
  Docker Desktop le fournit generalement (Compose utilise le meme moteur). En
  cas d'echec au demarrage, voir les logs du pod `elasticsearch`.
- `minio` : image Bitnami "legacy" epinglee (comme Compose).
- Pas d'Ingress / NodePort : acces par `port-forward` uniquement (choix
  deliberé pour ne pas exposer les services hors loopback).
- Observabilite (Prometheus/Grafana) : **non incluse** ici ; la pile Compose
  `monitoring/` reste la reference. Peut etre ajoutee ulterieurement.
- `imagePullPolicy: IfNotPresent` : les images `:latest` doivent etre presentes
  localement (Docker Desktop les partage avec le cluster).

## Securite

- Bases et Keycloak-admin ne sont **pas** exposes : pas de NodePort/LoadBalancer,
  acces uniquement via `port-forward` (loopback).
- Le Secret `pfe-secrets` est local, genere, jamais committe.
- Le royaume Keycloak embarque des identifiants de **developpement** : image
  locale uniquement.
