# PFE Flow

Application de gestion des projets de fin d'études (PFE) — FSAC Casablanca.

**Stack :** Spring Boot 4.1.1 (REST + JPA + PostgreSQL/H2) + React 19 + Vite + TypeScript + Docker + Prometheus + Grafana + CI/CD GitHub Actions.

## Architecture
- `backend/` — API REST (Java 21, Maven), expose `/api/*` + Actuator (`/actuator/health`, `/actuator/prometheus`).
- `frontend/` — SPA React+Vite (build statique), servi par Nginx, proxy `/api/` vers le backend.

## Démarrage rapide (Docker)
```bash
docker compose --profile observability up --build
```
- Frontend : http://127.0.0.1:8081
- Backend API : http://127.0.0.1:8888/api
- Prometheus : http://127.0.0.1:9090
- Grafana : http://127.0.0.1:3000 (admin/admin)

## Tests
```bash
cd backend && mvn test  # 9 tests, H2
cd frontend && npm ci && npm run typecheck && npm run build
```