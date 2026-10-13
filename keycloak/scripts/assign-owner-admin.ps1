<#
.SYNOPSIS
  Attribue (de facon idempotente) le role realm ADMIN au compte proprietaire,
  identifie par son email exact.

.DESCRIPTION
  Le realm d'import (keycloak/realm-pfeecom.json) n'est applique qu'a la creation
  du realm ; ce script applique le role a un realm deja en service. Aucun mot de
  passe ni attribut de l'utilisateur n'est modifie : seul le role realm est pose.

  Securite : l'email doit correspondre EXACTEMENT a un unique utilisateur. En cas
  d'absence ou d'ambiguite, le script s'arrete sans rien modifier (jamais de
  creation d'utilisateur, jamais de promotion d'un autre compte).

.EXAMPLE
  ./assign-owner-admin.ps1
  ./assign-owner-admin.ps1 -AdminPassword $env:KC_ADMIN_PASSWORD
#>
param(
    [string]$KeycloakUrl = "http://localhost:8181",
    [string]$Realm = "pfeecom",
    [string]$AdminUser = "admin",
    [string]$AdminPassword = "admin",
    [string]$Email = "raichychouaib@gmail.com",
    [string]$RoleName = "ADMIN"
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

$headers = @{ Authorization = "Bearer $(Get-AdminToken)" }

# 1) Localiser l'utilisateur par email EXACT et verifier l'unicite.
$users = @(Invoke-RestMethod -Method Get -Headers $headers `
    -Uri "$KeycloakUrl/admin/realms/$Realm/users?email=$([uri]::EscapeDataString($Email))&exact=true") |
    Where-Object { $_ -ne $null }
if ($users.Count -ne 1) {
    throw "Attendu 1 utilisateur pour '$Email', trouve $($users.Count). Arret sans modification."
}
$user = $users[0]
Write-Host "Utilisateur trouve : $($user.username) (enabled=$($user.enabled))."

# 2) Representer le role realm cible.
$role = Invoke-RestMethod -Method Get -Headers $headers `
    -Uri "$KeycloakUrl/admin/realms/$Realm/roles/$RoleName"

# 3) Idempotence : ne poser le role que s'il est absent.
$current = @(Invoke-RestMethod -Method Get -Headers $headers `
    -Uri "$KeycloakUrl/admin/realms/$Realm/users/$($user.id)/role-mappings/realm") |
    ForEach-Object { $_.name }
if ($current -contains $RoleName) {
    Write-Host "Role $RoleName deja attribue. Rien a faire."
    return
}

$mapping = ConvertTo-Json -InputObject @(@{ id = $role.id; name = $role.name }) -Depth 5
Invoke-RestMethod -Method Post -Headers $headers -ContentType "application/json" `
    -Uri "$KeycloakUrl/admin/realms/$Realm/users/$($user.id)/role-mappings/realm" `
    -Body $mapping | Out-Null

Write-Host "OK : role $RoleName attribue a $Email."
