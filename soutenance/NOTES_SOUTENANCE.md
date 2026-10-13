# Notes de soutenance — Plateforme e-commerce en microservices

> Compagnon du diaporama `Soutenance_PFE_Ecommerce_Microservices.pptx` (18 diapositives, 16:9).
> Durée cible : **13–15 minutes** de présentation + **~10 minutes** de questions.
> Tous les chiffres proviennent des vérifications du **13/10/2026** (voir `README.md`,
> `docs/RUNBOOK_ECOM.md`, `docs/FINAL_RELEASE_AUDIT.md`).

---

## 0. Préparation (avant d'entrer)

- **Matériel** : la pile tourne (`docker ps` → 16 conteneurs `healthy` + `minio-init` `Exited 0`).
- **Onglets à ouvrir d'avance** : boutique `http://localhost:5173`, Keycloak `http://localhost:8181`,
  Swagger `http://localhost:8090/swagger-ui.html`, Prometheus `http://localhost:9090/targets`,
  Grafana `http://localhost:3000/d/pfe-ecom-overview`, Eureka `http://localhost:8761`.
- **Identifiants de démonstration** (valeurs de développement du realm `pfeecom` — à changer en production) :
  `admin` / `admin` (ADMIN), `yassine` / `yassine` (CUSTOMER, possède un `customer_id`).
- **Repli si le réseau/la pile ne répond pas** : utiliser les **captures réelles** intégrées au diaporama
  (`docs/assets/ecom-*.png`) et poursuivre l'oral sans exécuter de requêtes.
- **Ne pas** déclencher de commande destructive (pas de `docker compose down -v`, pas de `prune`).
- **Ne pas** modifier de mot de passe ni créer d'utilisateur pendant la soutenance.

---

## 1. Diapositive 1 — Titre · 0:20

**Message** : annoncer le sujet et le cadre.

**Oral** : « Bonjour, je suis Chouaib Raichy, encadré par le Pr. Noreddine Abghour. Je présente
la conception et l'industrialisation d'une **plateforme e-commerce en microservices** : conteneurisation,
sécurité OIDC, persistance polyglotte et observabilité. »

**Transition** : « Je commence par le plan. »

---

## 2. Diapositive 2 — Plan · 0:20

**Message** : situer le déroulé en cinq parties.

**Oral** : « Cinq parties : le contexte et la problématique, l'architecture et les choix technologiques,
la réalisation, les tests et résultats, puis les perspectives. »

**Transition** : « D'abord, pourquoi les microservices. »

---

## 3. Diapositive 3 — Contexte : du monolithe aux microservices · 0:50

**Message** : le monolithe est simple mais couplé ; les microservices isolent les domaines.

**Oral** : « Un monolithe est un seul déployable : simple à démarrer, mais tout est couplé et on ne
met à l'échelle que globalement. Les microservices découpent le système par domaine métier, avec
un déploiement indépendant par service. Les bénéfices sont l'isolation des pannes et une montée en
charge ciblée. En contrepartie, il faut résoudre la découverte de services, la configuration, la
sécurité distribuée et l'observabilité. C'est exactement le rôle des briques Spring Cloud, de
Keycloak et de Prometheus/Grafana. »

**Détails techniques** : la passerelle est le **point d'entrée unique** ; les services métier ne sont
pas exposés sur l'hôte.

**Transition** : « D'où la problématique. »

---

## 4. Diapositive 4 — Problématique & objectifs · 0:50

**Message** : transformer des services séparés en une plateforme cohérente, reproductible, sécurisée et observable.

**Oral** : « La question : comment passer de services développés séparément à **une** plateforme
cohérente, reproductible, sécurisée et observable ? Mes objectifs : quatre services métier ; une
infrastructure partagée ; une pile Docker Compose de 16 conteneurs avec volumes et healthchecks ;
une sécurité OIDC par rôle et par propriété ; une persistance polyglotte ; et 135 tests automatisés
au vert. »

**Transition** : « Voici les technologies retenues. »

---

## 5. Diapositive 5 — Choix technologiques · 0:50

**Message** : chaque outil répond à un besoin, pas par mode.

**Oral** : « Java 21 et Spring Boot 3.3 pour la robustesse et l'écosystème microservices.
PostgreSQL pour les référentiels relationnels, MongoDB pour les agrégats documentaires.
Elasticsearch pour la recherche produit, Redis pour le cache de lecture, MinIO pour les images
produit en stockage objet S3. Keycloak pour l'identité OIDC. React 19 + Vite + TypeScript côté
frontend. Prometheus et Grafana pour la supervision, provisionnés par code. Docker Compose pour
la pile locale, et des artefacts Kubernetes fournis mais **non déployés**. »

**À ne pas dire** : « Kubernetes est en production » — il ne l'est pas (manifestes seulement).

**Transition** : « Comment tout cela s'articule. »

---

## 6. Diapositive 6 — Architecture générale · 0:50

**Message** : client → passerelle → services privés → infra partagée → observabilité.

**Oral** : « En haut, la SPA React s'authentifie auprès de Keycloak avec le flux Authorization Code
et PKCE. Elle appelle une seule adresse : la passerelle, qui porte le routage, le CORS et la
validation des jetons JWT. Derrière, les quatre services métier écoutent sur des ports internes et
s'enregistrent dans Eureka. En bas, l'infrastructure de données partagée et le server de
configuration. À droite de l'infra, Prometheus scrape sept cibles et alimente le dashboard Grafana. »

**Détails** : passerelle `:8090`, Eureka `:8761`, Config `:8888`, services `:8081-8084`.

**Transition** : « Détail du découpage. »

---

## 7. Diapositive 7 — Découpage en services · 0:40

**Message** : un service par contexte borné, avec sa base.

**Oral** : « product-service gère le catalogue sur PostgreSQL ; customer-service les clients, aussi
sur PostgreSQL ; cart-service les paniers et order-service les commandes, tous deux sur MongoDB. La
passerelle, la configuration et l'annuaire sont des composants transverses. »

**Détails** : bases logiques `pfe_products`, `pfe_customers`, `pfe_carts`, `pfe_orders`.

**Transition** : « Justement, parlons des bases. »

---

## 8. Diapositive 8 — Persistance polyglotte · 0:50

**Message** : un SGBD adapté à chaque type de donnée.

**Oral** : « Les référentiels — catalogue, clients — vont sur PostgreSQL car j'ai besoin d'unicité,
de clés étrangères, de contraintes CHECK et de transactions ACID. Les agrégats — paniers, commandes —
vont sur MongoDB : les lignes sont embarquées, et on garde des snapshots dénormalisés. Le schéma
PostgreSQL est géré par Flyway avec `ddl-auto=validate`. Les images produit sont sur MinIO, la
recherche sur Elasticsearch, le cache de lecture sur Redis. J'assume la limite : pas de transaction
inter-bases ; la cohérence repose sur des snapshots et une validation en amont. »

**Transition** : « La sécurité, maintenant. »

---

## 9. Diapositive 9 — Sécurité OIDC / OAuth2 · 1:00

**Message** : Keycloak émet le jeton ; chaque service vérifie signature, émetteur, audience ; l'accès est par rôle et par propriété.

**Oral** : « Keycloak joue le rôle de fournisseur OIDC. Le frontend obtient un jeton via
Authorization Code + PKCE. Chaque service vérifie la signature, l'émetteur et l'audience. Les rôles :
ADMIN gère le catalogue et les clients, CUSTOMER achète. Point clé sur la propriété : le
`customer_id` est lu **dans le jeton**, jamais fourni par le navigateur. Un client ne peut donc
lire ou modifier que ses propres paniers et commandes — sinon 403. »

**Détails** : le port de gestion Actuator de la passerelle est interne, aucune métrique n'est exposée publiquement.

**Transition** : « Vérifions cette matrice en pratique. »

---

## 10. Diapositive 10 — Matrice d'autorisation · 0:40

**Message** : les réponses 401 / 403 / 200 sont testées de bout en bout.

**Oral** : « Lecture du catalogue publique : 200 pour tous. Écriture catalogue : 401 si anonyme,
403 si client, OK si admin. Liste des clients réservée à l'admin. Sur les paniers et commandes :
anonyme 401 ; un client accède aux siens mais reçoit 403 sur ceux d'un autre ; l'admin a accès.
Cette matrice est couverte par des tests automatisés. »

**Transition** : « Voyons l'application. »

---

## 11. Diapositive 11 — L'application — boutique · 1:00 (démo)

**Message** : la chaîne complète fonctionne côté navigateur.

**Démo (si possible)** : ouvrir `http://localhost:5173` ; montrer le catalogue ; se connecter via
Keycloak ; montrer le panier du client ; ouvrir les commandes. **Résultat attendu** : catalogue
peuplé, connexion redirigée vers Keycloak, jeton utilisé pour les appels API, panier et commandes du
client affichés.

**Repli** : si la connexion échoue, montrer la capture `docs/assets/ecom-app.png` en expliquant le flux.

**Note honnête** : ne pas affirmer avoir rejoué un **checkout authentifié complet** en direct si cela
n'a pas été exécuté ; décrire le support applicatif (paiement à la livraison `CASH_ON_DELIVERY`,
statut initial `PENDING`).

**Transition** : « Comment tout est déployé. »

---

## 12. Diapositive 12 — Conteneurisation & orchestration · 0:50

**Message** : une pile reproductible de 16 conteneurs, ordonnée par les healthchecks.

**Oral** : « Une image par service, construite en JVM ou servie par Nginx pour le frontend. Chaque
service expose une sonde de santé, et le démarrage est ordonné par `depends_on:
condition: service_healthy` : d'abord l'infra, puis les services, puis la passerelle. Les données
sont persistées dans des volumes. Le réseau isole les services métier. Une commande reconstruit
tout : `docker compose -f docker-compose.ecom.yml up -d --build`. »

**Transition** : « Peut-on surveiller cette pile ? »

---

## 13. Diapositive 13 — Observabilité · 0:50

**Message** : métriques → Prometheus (7/7) → Grafana.

**Oral** : « Chaque service expose des métriques via Actuator et Micrometer. Prometheus les scrape
en mode pull — sept cibles, toutes UP, zéro DOWN. Le dashboard Grafana est provisionné par code avec
onze panneaux : disponibilité des cibles, débit, latences p95. »

**Transition** : « Voici le dashboard. »

---

## 14. Diapositive 14 — Dashboard Grafana · 0:40

**Message** : la supervision est opérationnelle et visuelle.

**Oral** : « Capture réelle du dashboard `pfe-ecom-overview`. On voit les sept cibles UP et les
panneaux de latence et de débit alimentés par Prometheus. »

**Repli** : ouvrir `http://localhost:9090/targets` pour montrer les cibles UP.

**Transition** : « Les tests. »

---

## 15. Diapositive 15 — Tests & résultats · 1:00

**Message** : 135 tests verts, dont 41 de sécurité/documentation.

**Oral** : « 135 tests automatisés, zéro échec. Product-service en concentre 52 : API, images,
Elasticsearch, cache Redis — y compris un test de repli en cas de panne de cache — MinIO, OpenAPI.
Customer-service : 17, avec le provisionnement côté Keycloak. Cart-service : 19, dont six de
sécurité. Order-service : 17, dont cinq de sécurité. La passerelle : 30, dont 19 de sécurité, six de
propriété et cinq sur la sécurité des endpoints de documentation. Les tests tournent sur des bases
isolées — PostgreSQL de test `:5433` et MongoDB de test `:27018` — et couvrent le parcours API, la
sécurité 401/403/200 et les contraintes de base. »

**Précision** : 135 = 52 + 17 + 19 + 17 + 30. Sécurité « rôle/propriété » = 36 ; sécurité des endpoints de documentation = 5.

**Transition** : « Bilan de la pile. »

---

## 16. Diapositive 16 — Résultats de la pile complète · 0:50

**Message** : tout est vérifié en conditions réelles.

**Oral** : « Boutique servie sur le port 5173 avec connexion Keycloak. API produits publique en 200,
écriture réservée à l'admin. Sécurité opérationnelle avec la matrice 401/403/200. Prometheus voit
sept cibles up. Grafana expose le dashboard. La pile est reproductible, les volumes persistent à un
`down`/`up` sans `-v`. Concernant Kubernetes : des manifestes sont fournis et validés
**statiquement**, mais ils ne sont **pas déployés** dans ce livrable. »

**Transition** : « Et ensuite ? »

---

## 17. Diapositive 17 — Perspectives · 0:40

**Message** : ce qui prolonge naturellement le travail.

**Oral** : « Sécurité inter-services par jetons de service ou mTLS. Alerting avec Alertmanager.
CI/CD pour construire, tester et publier les images. Cohérence métier : décrément de stock et sagas
pour les transactions distribuées. Traces et logs avec OpenTelemetry et Loki. Et l'industrialisation
complète sur Kubernetes avec Ingress, HPA et une approche GitOps. »

**Transition** : « Je conclus. »

---

## 18. Diapositive 18 — Merci · 0:20

**Oral** : « Merci de votre attention. Je suis prêt à répondre à vos questions, et je peux refaire
une démonstration en direct si vous le souhaitez. »

---

## Discours d'ouverture (si besoin de tout rassembler, ~1 min)

« Ce projet de fin d'études consiste à concevoir et industrialiser une plateforme e-commerce en
microservices. Le fil conducteur : partir de quatre services développés séparément et en faire une
plateforme cohérente, reproductible, sécurisée et observable. J'aborderai le contexte, l'architecture,
la réalisation, les tests et les perspectives. »

## Discours de clôture (~30 s)

« Pour conclure : la plateforme fonctionne de bout en bout, avec 16 conteneurs sains, 135 tests au
vert, la sécurité OIDC opérationnelle et une supervision complète. Les limites sont assumées — pas de
transaction inter-bases, Kubernetes préparé mais non déployé — et les perspectives sont identifiées.
Merci de votre attention. »

---

## Démonstration en direct — scénario et résultats attendus

| # | Action | Résultat attendu | Repli |
|---|--------|------------------|-------|
| 1 | `docker ps --format "{{.Names}}\t{{.Status}}"` | 16 conteneurs `healthy` (+ `minio-init` `Exited 0`) | montrer une capture d'écran du terminal |
| 2 | Ouvrir `http://localhost:5173` | boutique affichée, catalogue paginé | capture `ecom-app.png` |
| 3 | Cliquer « Connexion » | redirection vers Keycloak, login `yassine`/`yassine` | expliquer le flux PKCE |
| 4 | Parcourir le catalogue / la recherche | produits listés ; recherche Elasticsearch | expliquer la source de données |
| 5 | Ouvrir `http://localhost:8090/api/customers` sans jeton | HTTP 401 | lancer avec jeton via Swagger |
| 6 | Ouvrir `http://localhost:9090/targets` | 7 cibles `UP`, 0 `DOWN` | capture `ecom-prometheus.png` |
| 7 | Ouvrir `http://localhost:3000/d/pfe-ecom-overview` | dashboard à 11 panneaux | capture `ecom-grafana.png` |
| 8 | Ouvrir `http://localhost:8090/swagger-ui.html` | documentation agrégée des services | — |

**À éviter** : ne pas provoquer un **checkout authentifié** complet si l'environnement n'est pas
isolé — il écrirait dans les bases de démonstration `pfe_carts` / `pfe_orders`.

---

## Questions probables du jury (15) et éléments de réponse

1. **Pourquoi des microservices et non un monolithe ?**
   Découplage par domaine, déploiement et scalabilité indépendants, isolation des pannes. Le coût est
   la complexité distribuée (découverte, config, sécurité, observabilité), traitée par Spring Cloud,
   Keycloak et Prometheus/Grafana.

2. **Pourquoi deux bases de données différentes ?**
   Polyglotte : PostgreSQL apporte intégrité relationnelle et transactions ACID pour les
   référentiels ; MongoDB facilite les documents embarqués et les snapshots dénormalisés pour les
   agrégats panier/commande.

3. **Comment garantissez-vous la cohérence entre PostgreSQL et MongoDB ?**
   Il n'y a **pas** de transaction inter-bases. La cohérence repose sur des snapshots et une
   validation en amont (par exemple, un produit référencé est vérifié à la création de la ligne).
   C'est une limite assumée ; les sagas sont une perspective.

4. **Comment fonctionne concrètement l'authentification ?**
   Keycloak (OIDC). La SPA utilise Authorization Code + PKCE ; elle reçoit un jeton JWT. Chaque
   service vérifie la signature, l'`iss` et l'audience. La passerelle valide le jeton avant de router.

5. **Comment empêchez-vous un client de voir les commandes d'un autre ?**
   Le `customer_id` est extrait **du jeton**, jamais d'un paramètre client. Un contrôle de propriété
   renvoie 403 si l'identité ne correspond pas (bypass pour ADMIN). C'est testé.

6. **Où sont stockés les mots de passe ?**
   Aucun mot de passe applicatif n'est stocké dans les services : l'identité est fédérée à Keycloak.
   Les identifiants de la pile sont des valeurs de développement, à externaliser en production.

7. **Comment la configuration est-elle centralisée ?**
   Un Spring Cloud Config Server lit un dépôt de configuration (`config-repo`). Les services tirent
   leur configuration au démarrage ; Eureka fournit la découverte.

8. **Question sur la validation des données ?**
   Contraintes au niveau base (UNIQUE email/catégorie, FK produit→catégorie, CHECK prix/stock) avec
   Flyway et `ddl-auto=validate`, plus la validation des DTO côté API.

9. **Comment avez-vous testé ?**
   135 tests automatisés (MockMvc, sécurité, contraintes, intégrations Redis/Elasticsearch/MinIO) sur
   des bases isolées (PostgreSQL `:5433`, MongoDB `:27018`). Zéro échec.

10. **Comment savez-vous que la pile fonctionne vraiment ?**
    Vérifications réelles : 16 conteneurs sains, 7/7 cibles Prometheus UP, endpoints publics en 200,
    `/api/customers` en 401 sans jeton, dashboard Grafana alimenté.

11. **Pourquoi 16 conteneurs ?**
    Config server, discovery, passerelle, quatre services, frontend, plus l'infra : PostgreSQL,
    MongoDB, Elasticsearch, MinIO, Redis, Keycloak, Prometheus, Grafana.

12. **Expliquez Elasticsearch, MinIO et Redis.**
    Elasticsearch indexe les produits pour la recherche full-text ; MinIO stocke les images produit
    en objet compatible S3 ; Redis met en cache les lectures fréquentes, avec un test de repli en
    cas de panne.

13. **Le passage à l'échelle ?**
    Chaque service peut être répliqué indépendamment derrière la passerelle. Les manifestes
    Kubernetes (Deployment, Service, HPA en perspective) préparent ce passage, mais **ne sont pas
    déployés** ici.

14. **Quelles sont les limites de votre travail ?**
    Pas de transaction inter-bases ; pas de traces distribuées ; Kubernetes préparé mais non déployé ;
    identifiants de démonstration à externaliser ; pas d'alerting.

15. **Qu'est-ce que vous feriez différemment ?**
    Ajouter l'alerting et l'observabilité des traces dès le début, externaliser les secrets, et
    écrire plus tôt les tests d'intégration inter-services.

16. **Le frontend est-il testé ?**
    Le typage TypeScript est vérifié, le lint passé, et un test unitaire de formatage est fourni ;
    la couverture frontend reste volontairement légère (une perspective).

---

## Points de vigilance — à ne pas affirmer sans preuve

- **Ne pas** dire que Kubernetes est **déployé** : seuls des manifestes validés statiquement existent.
- **Ne pas** affirmer un **checkout authentifié complet** en direct si non exécuté.
- **Ne pas** citer 13 conteneurs ou 52 tests : les valeurs à jour sont **16** et **135**.
- **Ne pas** présenter les identifiants de démonstration comme des identifiants de production.
- Les **captures** intégrées proviennent de la pile réelle (`docs/assets/ecom-*.png`) ; les anciennes
  images « PFE Flow » ne sont pas utilisées.

## Éléments à renseigner avant la soutenance

- `[date à compléter]` sur la diapositive de titre.
- Vérifier la filière exacte (le rapport mentionne « Licence DevOps ») et l'année universitaire.
- Confirmer la composition du jury (non renseignée dans le livrable).
