<#
.SYNOPSIS
  Cree (de facon idempotente) le client confidentiel de provisionnement dans le
  realm Keycloak et lui accorde le role minimal "manage-users".

.DESCRIPTION
  Le realm d'import (keycloak/realm-pfeecom.json) n'est applique qu'a la creation
  du realm. Ce script applique la meme configuration a un realm deja en service.
  Le client s'authentifie en client_credentials pour renseigner l'attribut
  utilisateur customer_id lors du provisionnement d'un nouveau client.

  Aucun secret n'est ecrit dans un fichier : la valeur par defaut correspond a
  celle du compose et doit etre surchargee en production (PROVISIONER_CLIENT_SECRET).

.EXAMPLE
  ./setup-provisioner-client.ps1
  ./setup-provisioner-client.ps1 -AdminPassword $env:KC_ADMIN_PASSWORD
#>
param(
    [string]$KeycloakUrl = "http://localhost:8181",
    [string]$Realm = "pfeecom",
    [string]$AdminUser = "admin",
    [string]$AdminPassword = "admin",
    [string]$ClientId = "pfeecom-provisioner",
    [string]$ClientSecret = "pfeecom-provisioner-secret"
)

$ErrorActionPreference = "Stop"

function Get-AdminToken {
    $body = @{
        grant_type = "password"
        client_id  = "admin-cli"
        username   = $AdminUser
        password   = $AdminPassword
    }
    $resp = Invoke-RestMethod -Method Post `
        -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" `
        -ContentType "application/x-www-form-urlencoded" -Body $body
    return $resp.access_token
}

$token = Get-AdminToken
$headers = @{ Authorization = "Bearer $token" }

function Get-ProvisionerClient {
    $found = Invoke-RestMethod -Method Get -Headers $headers `
        -Uri "$KeycloakUrl/admin/realms/$Realm/clients?clientId=$ClientId"
    return @($found) | Where-Object { $_ -ne $null } | Select-Object -First 1
}

$client = Get-ProvisionerClient
if (-not $client) {
    Write-Host "Creation du client $ClientId..."
    $clientRep = @{
        clientId               = $ClientId
        name                   = "PFE E-commerce - Provisionnement client (compte de service)"
        enabled                = $true
        protocol               = "openid-connect"
        publicClient           = $false
        bearerOnly             = $false
        standardFlowEnabled    = $false
        implicitFlowEnabled    = $false
        directAccessGrantsEnabled = $false
        serviceAccountsEnabled = $true
        secret                 = $ClientSecret
    } | ConvertTo-Json
    Invoke-RestMethod -Method Post -Headers $headers -ContentType "application/json" `
        -Uri "$KeycloakUrl/admin/realms/$Realm/clients" -Body $clientRep | Out-Null
    $client = Get-ProvisionerClient
} else {
    Write-Host "Client $ClientId deja present (secret inchange)."
}

$clientUuid = $client.id
$serviceAccount = Invoke-RestMethod -Method Get -Headers $headers `
    -Uri "$KeycloakUrl/admin/realms/$Realm/clients/$clientUuid/service-account-user"

$realmMgmt = @(Invoke-RestMethod -Method Get -Headers $headers `
    -Uri "$KeycloakUrl/admin/realms/$Realm/clients?clientId=realm-management")[0]
$role = Invoke-RestMethod -Method Get -Headers $headers `
    -Uri "$KeycloakUrl/admin/realms/$Realm/clients/$($realmMgmt.id)/roles/manage-users"

$mapping = ConvertTo-Json -InputObject @(@{ id = $role.id; name = $role.name }) -Depth 5
Invoke-RestMethod -Method Post -Headers $headers -ContentType "application/json" `
    -Uri "$KeycloakUrl/admin/realms/$Realm/users/$($serviceAccount.id)/role-mappings/clients/$($realmMgmt.id)" `
    -Body $mapping | Out-Null

# Le profil declaratif (Keycloak 26) restreint les attributs libres. On autorise
# l'edition administrateur afin que le compte de service puisse poser l'attribut
# customer_id (sinon la mise a jour est silencieusement ignoree).
$profile = Invoke-RestMethod -Method Get -Headers $headers `
    -Uri "$KeycloakUrl/admin/realms/$Realm/users/profile"
$profile | Add-Member -NotePropertyName unmanagedAttributePolicy -NotePropertyValue "ADMIN_EDIT" -Force
$profileJson = $profile | ConvertTo-Json -Depth 10
Invoke-RestMethod -Method Put -Headers $headers -ContentType "application/json" `
    -Uri "$KeycloakUrl/admin/realms/$Realm/users/profile" -Body $profileJson | Out-Null

Write-Host "OK : client $ClientId pret (role manage-users + unmanagedAttributePolicy=ADMIN_EDIT)."
