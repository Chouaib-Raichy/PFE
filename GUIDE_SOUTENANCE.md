# Guide de soutenance PFE

## Architecture
- Backend : Spring Boot 4 (Java 21, Maven, JPA, PostgreSQL) — `/api/projects`, `/api/tasks/*`, `/api/stats`, Actuator (`/actuator/health`, `/actuator/prometheus`)
- Frontend : React 19 + Vite + TypeScript — SPA, proxy Nginx `/api/` → backend
- Observabilité : Prometheus + Grafana (dashboards Spring Boot)
- CI/CD : GitHub Actions (Maven, npm, Docker build + Trivy)

## Démonstration (2–3 min)
1. Lancer `docker compose --profile observability up --build`
2. Ouvrir `http://127.0.0.1:8081` (CRUD projets + tâches + filtres + stats)
3. Montrer `http://127.0.0.1:9090` (cibles backend up, `/actuator/prometheus`)
4. Montrer `http://127.0.0.1:3000` (dashboard PFE Flow)
5. Montrer tests backend (9/9) + build frontend

## Tests & Qualité
- Backend : 9 tests d'intégration (RestClient) — BUILD SUCCESS
- Frontend : typecheck TypeScript sans erreur
- Docker : images multi-stage, healthchecks
