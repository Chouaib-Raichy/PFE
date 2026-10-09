---
title: "Projet de Fin d'Études"
subtitle: "Industrialisation d'une application web de gestion de projets : CI/CD, conteneurisation et observabilité"
author: "[Nom de l'étudiant] — Licence DevOps"
date: "Année universitaire 2025 / 2026 — FSAC Casablanca"
---

```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

# Remerciements

Nous tenons à exprimer notre gratitude à notre encadrant(e), **Pr. [Nom de l'encadrant]**,
pour sa disponibilité, ses conseils techniques et son suivi tout au long de ce projet.

Nos remerciements s'adressent également aux membres du jury d'évaluation, ainsi qu'au
corps enseignant de la Faculté des Sciences Aïn Chock (FSAC) pour la formation dispensée
durant le cycle de licence en DevOps.

```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

# Résumé

Ce projet s'inscrit dans le domaine de l'ingénierie logicielle moderne et plus
particulièrement dans la culture **DevOps**. Il porte sur la conception et
l'**industrialisation** d'une application web de gestion des projets de fin
d'études, c'est-à-dire sa mise en état d'être livrée, déployée et opérée de
manière reproductible et observable.

L'application, développée en **Python (FastAPI)**, permet de créer, consulter,
filtrer et suivre des projets (étudiant, encadrant, statut, tâches, date de
soutenance). Elle est empaquetée dans une **image Docker multi-stage**, exécutée
sous un utilisateur non-root et orchestrée par **Docker Compose** avec une base
**PostgreSQL**, des healthchecks et des volumes persistants.

L'industrialisation repose sur trois piliers :

1. **Un pipeline CI/CD GitHub Actions** : analyse statique (Ruff), tests
   automatisés avec seuil de couverture (≥ 90 %), build d'image, scan de
   vulnérabilités (Trivy), publication sur un registre GHCR et déploiement SSH
   conditionnel.
2. **La conteneurisation** : reproductibilité de l'environnement de
   développement à la production, isolation, mise à l'échelle horizontale.
3. **L'observabilité** : exposition de métriques au format Prometheus
   (`/metrics`), collecte par Prometheus et tableaux de bord **Grafana**
   pré-provisionnés (requêtes HTTP, latence p95, disponibilité).

**Mots-clés** : DevOps, CI/CD, Docker, conteneurisation, GitHub Actions,
Prometheus, Grafana, observabilité, FastAPI, tests automatisés.

**Abstract** — This project covers the design and industrialization of a web
application for managing final-year projects: a FastAPI application packaged
into a multi-stage Docker image, orchestrated with Docker Compose (PostgreSQL,
healthchecks), delivered through a GitHub Actions CI/CD pipeline (linting,
99% test coverage, image build, Trivy scanning, GHCR publication, conditional
SSH deployment) and instrumented with Prometheus metrics and a pre-provisioned
Grafana dashboard for observability.

```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

# Introduction générale

L'industrie logicielle a connu une transformation profonde au cours de la
dernière décennie. Le modèle en cascade, où les équipes de développement et
d'exploitation travaillaient de manière cloisonnée, a progressivement laissé
place à une approche intégrée : le **DevOps**. L'objectif est clair : livrer du
logiciel plus vite, plus souvent et avec une qualité supérieure, sans sacrifier
la stabilité de la production.

Un logiciel « livrable » ne se limite plus au code source. Il faut qu'il soit :

- **reproductible** — l'environnement de production doit être identique à celui
  de développement et de test (conteneurisation) ;
- **vérifié automatiquement** — chaque modification est validée par des tests et
  des contrôles qualité avant d'être fusionnée (intégration continue) ;
- **déployable automatiquement** — le passage du code à l'application en
  production ne doit pas dépendre d'une manipulation manuelle (déploiement
  continu) ;
- **observable** — une fois en production, l'application doit exposer son état
  de santé et ses métriques pour permettre la détection rapide des incidents.

Ce projet de fin d'études a pour objet de concrétiser l'ensemble de cette chaîne
à travers un cas réel : une application web de gestion des projets de fin
d'études, dénommée **PFE Flow**. Au-delà du développement de l'application
elle-même, l'essentiel du travail réside dans son **industrialisation** :

- image Docker multi-stage, exécution non-root, healthchecks ;
- orchestration Docker Compose (application + PostgreSQL + Prometheus + Grafana) ;
- pipeline CI/CD complet sur GitHub Actions ;
- instrumentation métriques et tableaux de bord Grafana provisionnés par code ;
- tests automatisés atteignant 99 % de couverture.

Le rapport est organisé en cinq chapitres. Le premier présente le cadre
théorique (DevOps, CI/CD, conteneurisation, observabilité) et la problématique.
Le second traite de l'étude de l'existant, des besoins et des choix
technologiques. Le troisième expose la conception. Le quatrième détaille la
réalisation technique. Le cinquième présente la stratégie de tests et les
résultats de validation.

```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

# Chapitre 1 — Cadre théorique et problématique

## 1.1 La culture DevOps

DevOps (contraction de *Development* et *Operations*) est à la fois une culture
et un ensemble de pratiques visant à rapprocher les équipes de développement
logiciel et les équipes d'exploitation. Ses principes fondamentaux sont :

| Principe | Description |
|---|---|
| **Collaboration** | Développeurs et opérateurs responsables ensemble du logiciel, de la conception à la production. |
| **Automatisation** | Construire, tester, déployer et monitorer sans intervention manuelle. |
| **Amélioration continue** | Mesurer (délais, taux d'échec, temps de restauration) puis améliorer. |
| **Livraison fréchente** | Petits lots, intégration continue, déploiements en continu. |

Les métriques *DORA* (Deployment Frequency, Lead Time for Changes, Change
Failure Rate, Time to Restore) sont les indicateurs de référence de la maturité
DevOps d'une organisation.

## 1.2 L'intégration et le déploiement continus (CI/CD)

L'**intégration continue (CI)** consiste à fusionner les modifications de code
plusieurs fois par jour dans une branche partagée, en déclenchant à chaque fois
une construction automatique et une série de vérifications (compilation, tests,
analyse statique). Le **déploiement continu (CD)** prolonge ce principe : si
toutes les vérifications passent, l'application est publiée en production sans
intervention humaine.

Un pipeline CI/CD typique comporte les étapes suivantes :

```
commit ──► lint ──► tests ──► build image ──► scan sécurité ──► publication ──► déploiement
```

Chaque étape est un *garde-fou* : elle bloque la chaîne en cas d'échec, ce qui
évite qu'un code défaillant n'atteigne la production (principe de *quality
gate*).

## 1.3 La conteneurisation

Un conteneur est un processus isolé partageant le noyau de l'hôte, empaqueté
avec ses dépendances. **Docker** a popularisé ce modèle en standardisant le
format d'image et son cycle de vie (`build`, `push`, `pull`, `run`).

- **Dockerfile** : recette déclarative de construction de l'image.
- **Build multi-stage** : séparer la phase de compilation (lourde) de l'image
  finale (légère, sans outils de build).
- **Docker Compose** : orchestration multi-conteneurs locains via un fichier
  YAML (services, réseaux, volumes, healthchecks).

La conteneurisation répond directement au problème du « ça marche sur ma
machine » : l'image construite dans le pipeline est exactement celle exécutée
en production.

## 1.4 L'observabilité

La supervision classique répond à la question « le service est-il up ? ».
L'**observabilité** va plus loin : elle permet de comprendre *pourquoi* le
service se comporte ainsi. Trois piliers :

- les **métriques** ( Prometheus ) : valeurs numériques agrégées dans le temps ;
- les **logs** : événements ponctuels ;
- les **traces** : parcours d'une requête à travers les services.

Ce projet se concentre sur le premier pilier : l'application expose un endpoint
`/metrics` au format *Prometheus Exposition Format*, collecté par un serveur
Prometheus (*pull model*), puis visualisé dans **Grafana** via des requêtes
**PromQL** :

```promql
sum by (status) (rate(http_requests_total[1m]))                       # débit par code HTTP
histogram_quantile(0.95, sum by (le) (rate(http_request_duration_seconds_bucket[5m])))  # latence p95
up{job="pfe-flow"}                                                    # disponibilité
```

## 1.5 Problématique

Comment passer d'un code source fonctionnel sur un poste de développement à une
application **livrable, reproductible, testée et observable**, en supprimant les
différences entre environnements et les déploiements manuels ?

## 1.6 Objectifs du projet

1. Développer une application web de gestion de projets de fin d'études
   (CRUD complet, recherche, filtres, API REST documentée).
2. Conteneuriser l'application avec une image multi-stage, sécurisée
   (utilisateur non-root) et sanitaire (healthcheck).
3. Orchestrer l'application et sa base de données avec Docker Compose
   (volumes, healthchecks, dépendances conditionnelles).
4. Mettre en place un pipeline CI/CD : qualité, tests, build, scan, publication,
   déploiement.
5. Instrumenter l'application et provisionner une plateforme de supervision
   (Prometheus + Grafana) entièrement décrite en code.
6. Garantir la qualité : tests automatisés, analyse statique, couverture ≥ 90 %.

```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

# Chapitre 2 — Étude de l'existant, besoins et choix technologiques

## 2.1 Étude de l'existant

| Outil | Type | Forces | Limites |
|---|---|---|---|
| **Jenkins** | Serveur CI/CD | Écosystème de plugins très riche | Serveur à administrer, interface vieillissante |
| **GitLab CI** | Intégré au forage Git | Intégration native, `.gitlab-ci.yml` | Hébergement à prévoir |
| **GitHub Actions** | CI/CD cloud-native | Sans serveur, marketplace, gratuit pour les dépôts publics | Dépendance à GitHub |
| **Azure DevOps** | Suite complète | Très intégré à l'écosystème Microsoft | Complexe pour un petit projet |
| **Docker / Compose** | Conteneurisation | Standard de fait, reproductible | Orchestration limitée (non distribué) |
| **Kubernetes** | Orchestration | Haute disponibilité, à l'échelle | Complexité disproportionnée pour ce projet |

**Décision** : adopter **GitHub Actions** (aucun serveur à maintenir, exécution
directe dans le dépôt) et **Docker Compose** (suffisant pour une application
multi-services à échelle unique ; Kubernetes reste une perspective).

## 2.2 Besoins fonctionnels

- Créer, consulter, modifier et supprimer un projet de fin d'études.
- Renseigner : titre, étudiant, encadrant, statut (*en cours / soutenu /
  validé*), description, date de soutenance.
- Gérer une liste de tâches par projet (ajout, coché/décoché).
- Rechercher par titre ou étudiant ; filtrer par statut.
- Tableau de bord avec compteurs (projets, tâches, tâches terminées) et
  barres d'avancement.
- Exposer une API REST JSON documentée (OpenAPI / Swagger).

## 2.3 Besoins non fonctionnels

- **Reproductibilité** : une seule commande pour démarrer toute la pile.
- **Performance** : réponse < 200 ms en local ; histogramme de latence exposé.
- **Sécurité** : image non-root, aucun secret dans le dépôt, scan de
  vulnérabilités dans le pipeline, en-têtes de sécurité de base.
- **Maintenabilité** : lint/formatage bloquants en CI, code structuré, tests.
- **Observabilité** : `/healthz` (santé), `/metrics` (métriques), dashboard
  Grafana pré-chargé.
- **Portabilité** : Linux (containers) et Windows (développement local).

## 2.4 Choix technologiques

| Besoin | Choix | Justification |
|---|---|---|
| Langage applicatif | **Python 3.14** | Écosystème mûr, rapidité de développement, équipe formée |
| Framework web | **FastAPI** | Async natif, validation intégrée, OpenAPI automatique |
| ORM | **SQLAlchemy 2.0** | Typage moderne (`Mapped[]`), portable SQLite/PostgreSQL |
| Templates | **Jinja2** | Rendu serveur, zéro build front, fonctionne hors-ligne |
| Base de données | **PostgreSQL 17** | Standard industriel, volumes persistants |
| Conteneurs | **Docker / Compose** | Standard de fait, profils pour l'observabilité |
| CI/CD | **GitHub Actions** | Sans serveur, YAML déclaratif, GHCR intégré |
| Qualité de code | **Ruff** | Lint + format ultra-rapides, un seul outil |
| Tests | **pytest + pytest-cov** | Standard Python, rapports de couverture |
| Sécurité | **Trivy** | Scan vulnérabilités + secrets, léger en CI |
| Métriques | **prometheus-client** | Bibliothèque officielle, instrumentation manuelle minimale |
| Supervision | **Prometheus + Grafana** | Standard de l'observabilité cloud-native |

```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

# Chapitre 3 — Conception

## 3.1 Architecture globale

```
                        ┌───────────────────────────────────────────┐
                        │           GitHub Actions (CI/CD)         │
                        │  quality ──► docker ──► deploy (optionnel)│
                        └──────┬────────────────────────────────────┘
                               │ push / pull request
                               ▼
┌──────────────┐      ┌──────────────────────┐      ┌────────────────┐
│   Navigateur │─────►│  app :8000 (FastAPI) │◄─────│   Prometheus   │
│  (utilisateur)│     │  uvicorn, non-root   │ scrape│    :9090       │
└──────────────┘      │  /  /projects  /api  │ /metrics└───────┬────────┘
                      └──────────┬───────────┘                 │ query
                                 │ SQLAlchemy                  ▼
                      ┌──────────▼───────────┐         ┌────────────────┐
                      │   PostgreSQL :5432   │         │  Grafana :3000 │
                      │   volume db-data     │         │ dashboard auto │
                      └──────────────────────┘         └────────────────┘
```

Le profil Docker Compose `observability` ajoute Prometheus et Grafana ; la pile
de base reste légère (app + base) pour le développement quotidien.

## 3.2 Modèle de données

Deux entités, relation **1 — N** (un projet possède zéro ou plusieurs tâches) :

```
┌─────────────────────────┐          ┌─────────────────────────┐
│        Project          │          │          Task           │
├─────────────────────────┤          ├─────────────────────────┤
│ id        PK  int       │───┐      │ id        PK  int       │
│ title     varchar(200)  │   │      │ label     varchar(200)  │
│ student   varchar(120)  │   1    N │ done      boolean       │
│ supervisor varchar(120) │   └──────│ project_id FK  int      │
│ status    varchar(20)   │          └─────────────────────────┘
│ description text        │
│ defense_date date NULL  │   status ∈ { en_cours, soutenu, valide }
└─────────────────────────┘
```

## 3.3 Cas d'utilisation

```
        └─── Consulter le tableau de bord (compteurs, filtres)
        ├─── Rechercher / filtrer les projets
Acteur ─┼─── Créer un projet
        ├─── Modifier le statut / la description
        ├─── Gérer les tâches (ajouter, cocher)
        ├─── Supprimer un projet
        └─── Consulter l'API REST (Swagger)
```

## 3.4 Diagramme de séquence — création d'un projet

```
Navigateur          FastAPI            Middleware metrics       Base (SQL)
    │  POST /projects   │                      │                   │
    │──────────────────►│                      │                   │
    │                   │ observe(start)       │                   │
    │                   │─────────────────────►│                   │
    │                   │  validation Formulaire (422 si invalide) │
    │                   │  INSERT project      │                   │
    │                   │─────────────────────────────────────────►│
    │                   │◄─────────────────────────────────────────│
    │                   │  inc pfe_projects_total                  │
    │  303 See Other     │                      │                   │
    │◄──────────────────│  observe(status, duration)               │
    │                   │─────────────────────►│                   │
    │  GET /projects/1  │                      │                   │
    │──────────────────►│  SELECT + tasks      │                   │
    │                   │─────────────────────────────────────────►│
    │  200 HTML          │                      │                   │
    │◄──────────────────│                      │                   │
```

## 3.5 Architecture du pipeline CI/CD

```
push / pull request sur main
        │
        ▼
┌─ job quality ─────────────────────────────┐
│ pip install -r requirements-dev.txt      │
│ ruff check app tests          (lint)     │
│ ruff format --check app tests (format)   │
│ pytest --cov-fail-under=90    (tests)    │
│ upload artefact coverage.xml             │
└────────────────┬─────────────────────────┘
                 │ réussite + event=push
                 ▼
┌─ job docker ─────────────────────────────┐
│ login GHCR (GITHUB_TOKEN)               │
│ docker/metadata-action (tags: sha,latest)│
│ build-push-action (cache GHA, push)      │
│ Trivy : scan vulnérabilités + secrets    │
└────────────────┬─────────────────────────┘
                 │ main + DEPLOY_ENABLED=true
                 ▼
┌─ job deploy ─────────────────────────────┐
│ SSH vers le serveur                     │
│ docker compose pull && up -d             │
└─────────────────────────────────────────┘
```

## 3.6 Architecture de l'observabilité

- **Instrumentation** : middleware Starlette horodatant chaque requête
  (hors `/static`, `/metrics`, `/healthz`) et alimentant un compteur
  `http_requests_total{method,path,status}` ainsi qu'un histogramme
  `http_request_duration_seconds`.
- **Collecte** : Prometheus (intervalle 5 s) cible `app:8000/metrics`.
- **Restitution** : Grafana se provisionne au démarrage (datasource +
  dashboard JSON montés en volume) — aucune configuration manuelle le jour J.

```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

# Chapitre 4 — Réalisation

## 4.1 Structure du dépôt

```
PFE/
├── app/
│   ├── main.py            # application FastAPI, routes, middleware métriques
│   ├── models.py          # entités SQLAlchemy (Project, Task)
│   ├── database.py        # moteur, sessions, get_db
│   ├── config.py          # DATABASE_URL, nom de l'application
│   ├── metrics.py         # compteurs / histogrammes Prometheus
│   ├── templates/         # Jinja2 (index, détail, formulaire)
│   └── static/style.css   # feuille de style (aucun CDN, hors-ligne)
├── tests/                 # pytest (9 tests, 99 % de couverture)
├── config/
│   ├── prometheus.yml     # cible de collecte
│   └── grafana/provisioning/   # datasource + dashboard
├── .github/workflows/ci.yml    # pipeline CI/CD
├── scripts/demo.ps1        # scénario de démonstration
├── Dockerfile              # image multi-stage, non-root
└── docker-compose.yml      # app + postgres (+ profil observability)
```

## 4.2 L'application FastAPI

Extrait du middleware d'instrumentation, cœur de l'observabilité :

```python
@app.middleware("http")
async def observe(request: Request, call_next):
    start = time.perf_counter()
    response = await call_next(request)
    path = request.url.path
    if not path.startswith(("/static", "/metrics", "/healthz")):
        elapsed = time.perf_counter() - start
        metrics.HTTP_REQUESTS.labels(request.method, path, str(response.status_code)).inc()
        metrics.HTTP_LATENCY.labels(request.method, path).observe(elapsed)
    return response
```

L'endpoint de santé vérifie simultanément l'application et la base, ce qui en
fait une *readiness probe* exploitable par Docker et Prometheus :

```python
@app.get("/healthz")
def healthz(db: Session = Depends(get_db)) -> dict:
    db.execute(text("SELECT 1"))
    return {"status": "ok", "db": "up", ...}
```

Le schéma OpenAPI est généré automatiquement et exposé sur `/api/docs`
(Swagger UI) : atout de démonstration pendant la soutenance.

## 4.3 L'image Docker

```dockerfile
FROM python:3.14-slim AS builder          # 1. étape build : lourde
ENV PIP_NO_CACHE_DIR=1
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
COPY requirements.txt .
RUN pip install -r requirements.txt       # couche cachée tant que les deps ne changent pas

FROM python:3.14-slim                     # 2. étape runtime : légère
ENV PATH="/opt/venv/bin:$PATH" PYTHONUNBUFFERED=1
RUN useradd --system --uid 10001 --no-create-home appuser   # non-root
WORKDIR /app
COPY --from=builder /opt/venv /opt/venv   # on ne copie que le venv
COPY app ./app
USER appuser                              # exécution sans privilèges
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/healthz', timeout=4)"
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

Points notables : multi-stage (image finale sans pip ni cache), utilisateur
`appuser` (uid 10001), healthcheck natif permettant à Docker et Compose de
détecter les pannes et de déclencher des redémarrages (`restart: unless-stopped`).

## 4.4 Orchestration Docker Compose

```yaml
app:
  build: .
  ports: ["8000:8000"]
  environment:
    DATABASE_URL: postgresql+psycopg://pfe:pfe@db:5432/pfe
  depends_on:
    db:
      condition: service_healthy    # attend que pg_isready réponde
  healthcheck:
    test: ["CMD", "python", "-c", "import urllib.request; ..."]
    interval: 15s
  restart: unless-stopped

db:
  image: postgres:17-alpine
  volumes: [db-data:/var/lib/postgresql/data]   # persistance
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U pfe -d pfe"]
```

Les *healthchecks* forment une chaîne de confiance : Compose ne démarre l'API
qu'une fois PostgreSQL prêt, et ne démarre Prometheus qu'une fois l'API saine.
Le profil `observability` s'active par `docker compose --profile observability up -d`.

## 4.5 Le pipeline GitHub Actions

Le workflow (`.github/workflows/ci.yml`) définit trois métiers :

1. **quality** — sur *push* et *pull request* : installation des dépendances,
   `ruff check`, `ruff format --check`, `pytest --cov-fail-under=90`, upload du
   rapport de couverture en artefact.
2. **docker** — sur *push* uniquement : connexion au registre GHCR, tags
   automatiques (`latest`, sha du commit), build avec cache GHA
   (`cache-from/to: type=gha`), publication, puis scan **Trivy**
   (vulnérabilités HIGH/CRITICAL + détection de secrets).
3. **deploy** — déclenché uniquement sur `main` **et** si la variable
   `DEPLOY_ENABLED=true` : connexion SSH, `docker compose pull && up -d`.
   Les identifiants (host, user, clé) vivent dans les *secrets* du dépôt —
   jamais dans le code.

Le *quality gate* (`--cov-fail-under=90`) illustre le principe de garde-fou :
une régression de couverture casse la CI et empêche la publication.

## 4.6 Observabilité en pratique

**Métriques exposées** :

```
# HELP http_requests_total Requetes HTTP
# TYPE http_requests_total counter
http_requests_total{method="GET",path="/",status="200"} 61
# TYPE http_request_duration_seconds histogram
http_request_duration_seconds_bucket{le="0.005"} 12
pfe_projects_total 3
```

**Dashboard Grafana** (fichier JSON versionné dans `config/grafana/dashboards/`) :

| Panneau | Requête PromQL |
|---|---|
| Débit par code HTTP | `sum by (status) (rate(http_requests_total[1m]))` |
| Latence p95 | `histogram_quantile(0.95, sum by (le) (rate(http_request_duration_seconds_bucket[5m])))` |
| Projets enregistrés | `pfe_projects_total` |
| Requêtes / minute | `sum(increase(http_requests_total[5m])) / 5` |
| Disponibilité | `up{job="pfe-flow"}` |

Grafana démarre avec la datasource Prometheus et le dashboard déjà en place
(*provisioning* déclaratif) : la supervision est ainsi « as code », versionnée
et reproductible, exactement comme l'application.

```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

# Chapitre 5 — Tests, résultats et validation

## 5.1 Stratégie de tests

Les tests couvrent les principaux parcours utilisateurs et les interfaces
techniques :

| Test | Ce qu'il vérifie |
|---|---|
| `test_healthz` | Endpoint de santé, réponse `{"status": "ok"}` |
| `test_index_empty` | État initial du tableau de bord |
| `test_create_and_view_project` | Création (303), affichage, compteurs, date FR |
| `test_search_filter` | Recherche `q` et filtre `status` |
| `test_tasks_flow` | Ajout de tâche + bascule terminé/non terminé |
| `test_update_status` | Modification du statut et de la description |
| `test_delete_project` | Suppression (API) puis 404 |
| `test_api_projects` | API REST JSON |
| `test_metrics_endpoint` | Présence des métriques Prometheus |

Les tests s'exécutent sur une base SQLite isolée (variable `DATABASE_URL`)
pour ne jamais toucher aux données de développement.

## 5.2 Résultats

```
$ pytest --cov=app
9 passed in 1.97s

Name              Stmts   Miss  Cover
-------------------------------------
app/config.py         3      0   100%
app/database.py      13      0   100%
app/main.py         105      1    99%
app/metrics.py        4      0   100%
app/models.py        25      0   100%
-------------------------------------
TOTAL               150      1    99%
```

Le pipeline CI applique un seuil de **90 %** : toute modification qui ferait
chuter la couverture en dessous casse la construction.

## 5.3 Validation de la pile complète

Scénario exécuté par `scripts/demo.ps1` :

1. `docker compose --profile observability up -d --build` — construction et
   démarrage des 4 services.
2. Attente de la sonde `/healthz` (app + base).
3. Création de 3 projets de démonstration et de tâches.
4. Génération de 60 requêtes pour alimenter les graphiques.
5. Ouverture de l'application, du dashboard Grafana et de Prometheus.

Vérifications effectuées : cible Prometheus `up`, dashboard Grafana
« PFE Flow — Observabilité » présent, données métriques collectées.

## 5.4 Sécurité

- Image exécutée en tant qu'utilisateur non-root (`uid 10001`).
- Scan Trivy (vulnérabilités et secrets) dans le pipeline.
- Aucun secret en clair : variables d'environnement et *secrets* GitHub.
- Base de données isolée sur le réseau Docker interne (seule l'application
  expose un port).

```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

# Conclusion générale

Ce projet a permis de parcourir l'ensemble de la chaîne de livraison d'un
logiciel moderne, de la première ligne de code jusqu'à son exploitation :
développement d'une application web de gestion de projets, conteneurisation
avec Docker (image multi-stage, non-root, healthchecks), orchestration par
Docker Compose avec PostgreSQL et volumes persistants, mise en place d'un
pipeline CI/CD GitHub Actions (lint, tests avec seuil de couverture, build,
scan de sécurité, publication GHCR, déploiement SSH conditionnel) et
construction d'une plateforme d'observabilité (métriques Prometheus,
tableaux de bord Grafana provisionnés par code).

Le résultat principal n'est pas seulement une application fonctionnelle :
c'est un **système reproductible** — une commande démarre toute la pile — et
**vérifiable** — 9 tests automatisés, 99 % de couverture, qualité contrôlée à
chaque *push*.

## Perspectives

- Migration vers **Kubernetes** (Deployments, Services, Ingress, HPA).
- **GitOps** avec ArgoCD : la production suit l'état du dépôt Git.
- Ajout des **logs structurés** collectés par Loki et des **traces** OpenTelemetry.
- Tests de charge (k6) et SLO/Alertmanager (alertes Slack).
- Intégration SonarQube pour la qualité de code et les *code smells*.

```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

# Bibliographie

1. Kim, G., Debois, P., Willis, J., Humble, J. — *The DevOps Handbook*, IT Revolution, 2016.
2. Forsgren, N., Humble, J., Kim, G. — *Accelerate*, IT Revolution, 2018.
3. Documentation Docker — https://docs.docker.com
4. Documentation GitHub Actions — https://docs.github.com/en/actions
5. Documentation Prometheus — https://prometheus.io/docs/introduction/overview/
6. Documentation Grafana — https://grafana.com/docs/grafana/latest/
7. Documentation FastAPI — https://fastapi.tiangolo.com
8. Documentation SQLAlchemy 2.0 — https://docs.sqlalchemy.org
