# Serveur de configuration centralise (Spring Cloud Config)

Ce repertoire est le **depot de configuration** (profil `native`) lu par
`config-server`. Les microservices y recuperent leur configuration au demarrage
via `spring.config.import=configserver:http://config-server:8888`.

## Structure

| Fichier | Portee |
|---|---|
| `application.yml` | Configuration commune a tous les services (actuator, prometheus). |
| `<service>.yml` | Configuration par defaut (profil docker / production locale conteneurisee). |
| `<service>-local.yml` | Surcharges pour le developpement hors Docker (profil `local`). |

Les services : `discovery-service`, `api-gateway`, `product-service`,
`customer-service`, `cart-service`, `order-service`.

## Secrets

Aucun secret n'est stocke ici. Les valeurs sensibles doivent etre injectees par
variables d'environnement et referencees sous la forme `${NOM_VARIABLE:defaut}`.
Exemples deja en place : `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`,
`SPRING_DATA_MONGODB_URI` (cart/order) et, pour product/customer (PostgreSQL),
`PRODUCT_DB_USER`/`PRODUCT_DB_PASSWORD` et `CUSTOMER_DB_USER`/`CUSTOMER_DB_PASSWORD`.

## Developpement local

1. Lancer le serveur de configuration :
   `CONFIG_REPO_LOCATION=file:../../config-repo mvn -f services/config-server spring-boot:run`
2. Lancer un service avec le profil `local` et l'URL locale du serveur :
   `CONFIG_SERVER_IMPORT=configserver:http://localhost:8888 SPRING_PROFILES_ACTIVE=local mvn -f services/product-service spring-boot:run`
