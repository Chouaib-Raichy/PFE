#Requires -Version 5.1
<#
.SYNOPSIS
    Ouvre les port-forwards locaux vers la pile PFE dans Kubernetes.

.DESCRIPTION
    Lance (en taches d'arriere-plan) trois tunnels kubectl port-forward :
      - frontend-ecom  : localhost:5173 -> 80   (SPA)
      - api-gateway    : localhost:8090 -> 8080 (API, comme Compose)
      - keycloak       : localhost:8181 -> 8080 (OIDC, KC_HOSTNAME attendu)
    Les identifiants et l'emetteur des jetons (iss) correspondent donc a la
    configuration par defaut (http://localhost:8181).

    Ctrl+C arrete ce script ; les taches filles sont alors supprimees.

.EXAMPLE
    pwsh -File k8s/scripts/port-forward.ps1
#>
param(
    [string]$Namespace = "pfe-ecommerce"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw "kubectl introuvable dans le PATH."
}

$forwards = @(
    @{ Name = "frontend-ecom"; Local = "5173:80" },
    @{ Name = "api-gateway";   Local = "8090:8080" },
    @{ Name = "keycloak";      Local = "8181:8080" }
)

$jobs = @()
foreach ($f in $forwards) {
    Write-Host ("[pf] {0}: localhost:{1}" -f $f.Name, $f.Local)
    $jobs += Start-Job -ScriptBlock {
        param($ns, $name, $local)
        & kubectl port-forward -n $ns "svc/$name" $local
    } -ArgumentList $Namespace, $f.Name, $f.Local
}

Write-Host ""
Write-Host "Frontend : http://localhost:5173"
Write-Host "API      : http://localhost:8090"
Write-Host "Keycloak : http://localhost:8181"
Write-Host ""
Write-Host "Ctrl+C pour arreter les port-forwards."

try {
    Wait-Job -Job $jobs | Out-Null
} finally {
    $jobs | Stop-Job -ErrorAction SilentlyContinue
    $jobs | Remove-Job -Force -ErrorAction SilentlyContinue
}
