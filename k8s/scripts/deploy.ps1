#Requires -Version 5.1
<#
.SYNOPSIS
    Deploie la pile e-commerce PFE dans le cluster Kubernetes local
    (Docker Desktop). Idempotent : peut etre rejoue.

.DESCRIPTION
    1. Verifie kubectl et la joignabilite du cluster.
    2. Construit l'image Keycloak derivee (theme + royaume) si absente.
    3. Cree le namespace.
    4. Cree le ConfigMap "config-repo" depuis le dossier config-repo/.
    5. Genere le Secret "pfe-secrets" (voir generate-secrets.ps1).
    6. Applique les manifestes (kustomize) puis attend les deploiements.

.PREREQUIS
    - Docker Desktop avec Kubernetes ACTIVE.
    - Images applicatives locales deja construites (via docker compose build) :
      pfe-{config-server,discovery,product-service,customer-service,
      cart-service,order-service,api-gateway,frontend-ecom}:latest

.EXAMPLE
    pwsh -File k8s/scripts/deploy.ps1
#>
param(
    [string]$Namespace = "pfe-ecommerce",
    [switch]$SkipWait
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot  = (Resolve-Path (Join-Path $scriptDir "..\..")).Path
$k8sDir    = Join-Path $repoRoot "k8s"

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw "kubectl introuvable dans le PATH."
}

Write-Host "[deploy] Verification du cluster..."
& kubectl cluster-info | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Cluster Kubernetes injoignable. Activez Kubernetes dans Docker Desktop (Settings > Kubernetes)."
}

# --- Image Keycloak derivee (theme + royaume) -------------------------------
$kcImage = "pfe-keycloak-ecom:latest"
$hasImage = (& docker images -q $kcImage) 2>$null
if (-not $hasImage) {
    Write-Host "[deploy] Construction de l'image Keycloak derivee ($kcImage)..."
    & docker build -f (Join-Path $k8sDir "keycloak\Dockerfile") -t $kcImage $repoRoot
    if ($LASTEXITCODE -ne 0) { throw "Echec de la construction de l'image Keycloak." }
} else {
    Write-Host "[deploy] Image $kcImage deja presente."
}

# --- Namespace --------------------------------------------------------------
Write-Host "[deploy] Namespace..."
& kubectl apply -f (Join-Path $k8sDir "namespace.yaml")
if ($LASTEXITCODE -ne 0) { throw "Echec de creation du namespace." }

# --- ConfigMap config-repo (source unique de verite) ------------------------
Write-Host "[deploy] ConfigMap config-repo..."
$configRepoPath = Join-Path $repoRoot "config-repo"
$cmArgs = @(
    "create", "configmap", "config-repo",
    "--namespace", $Namespace,
    "--from-file=$configRepoPath",
    "--dry-run=client", "-o", "yaml"
)
$cmYaml = & kubectl @cmArgs
if ($LASTEXITCODE -ne 0) { throw "Echec de la generation du ConfigMap config-repo." }
$cmYaml | & kubectl apply -f -
if ($LASTEXITCODE -ne 0) { throw "Echec de l'application du ConfigMap config-repo." }

# --- Secret -----------------------------------------------------------------
& (Join-Path $scriptDir "generate-secrets.ps1") -Namespace $Namespace

# --- Manifestes -------------------------------------------------------------
Write-Host "[deploy] Application des manifestes (kustomize)..."
& kubectl apply -k $k8sDir
if ($LASTEXITCODE -ne 0) { throw "Echec de kubectl apply -k." }

if ($SkipWait) {
    Write-Host "[deploy] Termine (attente ignoree)."
    exit 0
}

# --- Attente des deploiements ----------------------------------------------
$deployments = @(
    "postgres", "mongo", "redis", "elasticsearch", "minio", "keycloak",
    "config-server", "discovery", "product-service", "customer-service",
    "cart-service", "order-service", "api-gateway", "frontend-ecom"
)
Write-Host "[deploy] Attente des deploiements (peut prendre plusieurs minutes)..."
foreach ($d in $deployments) {
    Write-Host "  - $d"
    & kubectl rollout status "deployment/$d" -n $Namespace --timeout=300s
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Le deploiement '$d' n'est pas pret. Inspectez : kubectl -n $Namespace describe deploy/$d ; kubectl -n $Namespace logs deploy/$d"
    }
}

Write-Host "[deploy] Termine. Acces local : pwsh -File k8s/scripts/port-forward.ps1"
