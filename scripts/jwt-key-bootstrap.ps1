<#
.SYNOPSIS
  Genera (si no existe) la clave simetrica HS256 usada para firmar/verificar los
  JWT de acceso de CanchaDirecta, en formato JWK.

.DESCRIPTION
  Escribe `canchadirecta/.secrets/jwt-signing-key.jwk` (gitignored) con una clave
  aleatoria de 32 bytes (HS256) y anade `JWT_SIGN_KEY_LOCATION` al `.env`.
  Idempotente: si la clave ya existe, no la regenera (regenerarla invalida los
  tokens vigentes).

.NOTES
  La clave nunca se imprime ni se versiona.
#>
[CmdletBinding()]
param(
  [switch]$Force
)

$ErrorActionPreference = 'Stop'

$root      = Split-Path -Parent $PSScriptRoot
$secretsDir = Join-Path $root '.secrets'
$keyFile    = Join-Path $secretsDir 'jwt-signing-key.jwk'
$envFile    = Join-Path $root '.env'

if ((Test-Path $keyFile) -and -not $Force) {
  Write-Host "[SKIP] La clave ya existe: $keyFile" -ForegroundColor DarkGray
} else {
  New-Item -ItemType Directory -Force -Path $secretsDir | Out-Null

  $bytes = New-Object byte[] 32
  [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
  $b64url = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')

  $jwk = [ordered]@{
    kty = 'oct'
    kid = 'canchadirecta-access'
    alg = 'HS256'
    k   = $b64url
  } | ConvertTo-Json -Compress

  Set-Content -Path $keyFile -Value $jwk -Encoding ASCII -NoNewline
  Write-Host "[OK] Clave JWT generada en $keyFile (no se imprime la clave)." -ForegroundColor Green
}

# Registrar la ruta en .env
$linea = "JWT_SIGN_KEY_LOCATION=$keyFile"
if (Test-Path $envFile) {
  $contenido = Get-Content $envFile | Where-Object { $_ -notmatch '^JWT_SIGN_KEY_LOCATION=' }
  $contenido += $linea
  Set-Content -Path $envFile -Value $contenido -Encoding UTF8
  Write-Host "[OK] .env actualizado con JWT_SIGN_KEY_LOCATION." -ForegroundColor Green
} else {
  Write-Host "[WARN] No existe .env; anadir manualmente: $linea" -ForegroundColor Yellow
}
