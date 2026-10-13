#Requires -Version 5.1
<#
.SYNOPSIS
    Genere (ou met a jour) le Secret Kubernetes "pfe-secrets" pour le namespace
    pfe-ecommerce. Les valeurs par defaut reproduisent celles de
    docker-compose.ecom.yml (developpement local UNIQUEMENT).

.DESCRIPTION
    Aucune valeur sensible n'est affichee : la sortie est redigee. Les valeurs
    peuvent etre surchargees via parametres. Le Secret est cree avec
    `kubectl create secret ... --dry-run=client | kubectl apply -f -` (idempotent).

.EXAMPLE
    pwsh -File k8s/scripts/generate-secrets.ps1
.EXAMPLE
    pwsh -File k8s/scripts/generate-secrets.ps1 -PostgresPassword "s3cret"
#>
param(
    [string]$Namespace = "pfe-ecommerce",

    [string]$PostgresPassword          = "postgres",
    [string]$ProductDbPassword         = "product",
    [string]$CustomerDbPassword        = "customer",
    [string]$ProvisionerClientSecret   = "pfeecom-provisioner-secret",
    [string]$MinioRootUser             = "pfeecom",
    [string]$MinioRootPassword         = "pfeecom123",
    [string]$MinioAccessKey            = "pfeecom-app",
    [string]$MinioSecretKey            = "pfeecom-app-secret",
    [string]$KeycloakAdmin             = "admin",
    [string]$KeycloakAdminPassword     = "admin"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw "kubectl introuvable dans le PATH."
}

Write-Host "[secrets] Namespace : $Namespace"
Write-Host "[secrets] Creation/mise a jour du Secret 'pfe-secrets' (valeurs redigees)..."

$args = @(
    "create", "secret", "generic", "pfe-secrets",
    "--namespace", $Namespace,
    "--from-literal=POSTGRES_PASSWORD=$PostgresPassword",
    "--from-literal=PRODUCT_DB_PASSWORD=$ProductDbPassword",
    "--from-literal=CUSTOMER_DB_PASSWORD=$CustomerDbPassword",
    "--from-literal=PROVISIONER_CLIENT_SECRET=$ProvisionerClientSecret",
    "--from-literal=MINIO_ROOT_USER=$MinioRootUser",
    "--from-literal=MINIO_ROOT_PASSWORD=$MinioRootPassword",
    "--from-literal=MINIO_ACCESS_KEY=$MinioAccessKey",
    "--from-literal=MINIO_SECRET_KEY=$MinioSecretKey",
    "--from-literal=KEYCLOAK_ADMIN=$KeycloakAdmin",
    "--from-literal=KEYCLOAK_ADMIN_PASSWORD=$KeycloakAdminPassword",
    "--dry-run=client", "-o", "yaml"
)

$yaml = & kubectl @args
if ($LASTEXITCODE -ne 0) { throw "Echec de la generation du Secret." }

$yaml | & kubectl apply -f -
if ($LASTEXITCODE -ne 0) { throw "Echec de l'application du Secret." }

Write-Host "[secrets] OK."
