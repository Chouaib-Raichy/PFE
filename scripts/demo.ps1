# Script de démonstration - PFE Flow (Spring Boot + React)
# Usage: powershell -ExecutionPolicy Bypass -File scripts\demo.ps1

$ErrorActionPreference = "Stop"
$api = "http://127.0.0.1:8888/api"
$fe  = "http://127.0.0.1:8081"
$grafana = "http://127.0.0.1:3000"
$prometheus = "http://127.0.0.1:9090"
$backend = "http://127.0.0.1:8888"

Write-Host "[1/5] Démarrage de la stack (si non lancée)..." -ForegroundColor Cyan
docker compose --profile observability up -d 2>$null | Out-Null

Write-Host "[2/5] Attente de la santé backend..." -ForegroundColor Cyan
$ok = $false
foreach ($i in 1..60) {
    try {
        $h = Invoke-RestMethod "$backend/actuator/health" -TimeoutSec 5
        if ($h.status -eq "UP") { $ok = $true; break }
    } catch {
        Start-Sleep -Seconds 2
    }
}
if (-not $ok) { throw "Backend non sain. Vérifier : docker compose ps / logs pfe-backend-1" }
Write-Host "  backend: UP" -ForegroundColor Green

Write-Host "[3/5] Données de démonstration..." -ForegroundColor Cyan
$existing = Invoke-RestMethod "$api/projects"
if ($existing.Count -eq 0) {
    $p1 = Invoke-RestMethod -Method Post -Uri "$api/projects" -ContentType 'application/json' -Body '{"title":"Pipeline CI/CD pour microservice e-commerce","student":"Amine Berrada","supervisor":"Pr. Alami","status":"VALIDE","description":"CI/CD complet","defenseDate":"2026-09-15"}'
    $p2 = Invoke-RestMethod -Method Post -Uri "$api/projects" -ContentType 'application/json' -Body '{"title":"Conteneurisation et orchestration Kubernetes","student":"Sara Idrissi","supervisor":"Pr. Bennani","status":"SOUTENU","description":"K8s","defenseDate":"2026-10-02"}'
    $p3 = Invoke-RestMethod -Method Post -Uri "$api/projects" -ContentType 'application/json' -Body '{"title":"Observabilité d\'une plateforme de streaming","student":"Youssef El Fassi","supervisor":"Pr. Alami","status":"EN_COURS","description":"Prometheus/Grafana","defenseDate":"2026-10-12"}'
    $t = Invoke-RestMethod -Method Post -Uri "$api/projects/$($p3.id)/tasks" -ContentType 'application/json' -Body '{"label":"Finaliser le rapport"}'
    $t2 = Invoke-RestMethod -Method Post -Uri "$api/projects/$($p3.id)/tasks" -ContentType 'application/json' -Body '{"label":"Configurer le monitoring"}'
    Invoke-RestMethod -Method Post -Uri "$api/tasks/$($t2.id)/toggle" | Out-Null
    Write-Host "  3 projets et 2 tâches créés (1 cochée)" -ForegroundColor Green
} else {
    Write-Host "  données déjà présentes ($($existing.Count) projets)" -ForegroundColor Green
}

Write-Host "[4/5] Génération de trafic pour graphiques..." -ForegroundColor Cyan
foreach ($i in 1..40) {
    try {
        Invoke-WebRequest "$fe/" -UseBasicParsing -TimeoutSec 2 | Out-Null
        Invoke-WebRequest "$api/projects" -UseBasicParsing -TimeoutSec 2 | Out-Null
        Invoke-WebRequest "$api/stats" -UseBasicParsing -TimeoutSec 2 | Out-Null
    } catch {}
    Start-Sleep -Milliseconds 250
}
Write-Host "  trafic généré" -ForegroundColor Green

Write-Host "[5/5] Endpoints prêts" -ForegroundColor Cyan
Write-Host "  API: $api" -ForegroundColor White
Write-Host "  Frontend: $fe" -ForegroundColor White
Write-Host "  Prometheus: $prometheus" -ForegroundColor White
Write-Host "  Grafana: $grafana (admin/admin)" -ForegroundColor White
Write-Host "  Backend Actuator: $backend/actuator/health, $backend/actuator/prometheus" -ForegroundColor White
Write-Host "Stack prête pour soutenance." -ForegroundColor Green