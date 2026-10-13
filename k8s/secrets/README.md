# Secrets — non committes

Le Secret Kubernetes `pfe-secrets` (namespace `pfe-ecommerce`) est **genere a
l'execution** par `k8s/scripts/generate-secrets.ps1`. Il n'est **jamais** stocke
dans ce dossier ni dans Git.

## Pourquoi

Les Secrets Kubernetes ne sont que du base64 (non chiffre). On ne met donc pas
les valeurs en clair dans le depot. Le script reprend les valeurs locales de
`docker-compose.ecom.yml` par defaut, surchargeables :

| Cle du Secret          | Valeur locale par defaut | Usage                                  |
|------------------------|--------------------------|----------------------------------------|
| POSTGRES_PASSWORD      | `postgres`               | superutilisateur PostgreSQL            |
| PRODUCT_DB_PASSWORD    | `product`                | role `product_svc`                     |
| CUSTOMER_DB_PASSWORD   | `customer`               | role `customer_svc`                    |
| PROVISIONER_CLIENT_SECRET | `pfeecom-provisioner-secret` | client Keycloak `pfeecom-provisioner` |
| MINIO_ROOT_USER        | `pfeecom`                | racine MinIO                           |
| MINIO_ROOT_PASSWORD    | `pfeecom123`             | racine MinIO                           |
| MINIO_ACCESS_KEY       | `pfeecom-app`            | utilisateur applicatif MinIO           |
| MINIO_SECRET_KEY       | `pfeecom-app-secret`     | utilisateur applicatif MinIO           |
| KEYCLOAK_ADMIN          | `admin`                 | admin bootstrap Keycloak               |
| KEYCLOAK_ADMIN_PASSWORD | `admin`                 | admin bootstrap Keycloak               |

> Valeurs de **developpement local uniquement**. Ne pas reutiliser en production.

## Generation

```powershell
pwsh -File k8s/scripts/generate-secrets.ps1
```

Le script n'affiche aucune valeur sensible (sortie redigee).
