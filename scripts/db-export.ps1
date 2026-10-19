<#
.SYNOPSIS
  Exporta la base de datos completa (esquema + datos) a un archivo .sql reproducible.

.DESCRIPTION
  Usa las credenciales de administrador de MySQL del `.env` y `mysqldump`. El archivo
  resultante no incluye `CREATE DATABASE` ni `USE`, de modo que se puede restaurar en
  cualquier base con `scripts/db-import.ps1`.

.PARAMETER Salida
  Ruta del archivo. Por defecto `db/canchadirecta.sql` (relativa a la raiz del proyecto).

.PARAMETER SoloEsquema
  Exporta solo la estructura, sin filas.

.EXAMPLE
  .\scripts\db-export.ps1
  .\scripts\db-export.ps1 -Salida db\respaldo-2026-10-19.sql
  .\scripts\db-export.ps1 -SoloEsquema
#>
param(
  [string]$Salida = '',
  [switch]$SoloEsquema
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

# ---------------------------------------------------------------- entorno .env
$map = @{}
$archivoEnv = Join-Path $root '.env'
if (Test-Path $archivoEnv) {
  Get-Content $archivoEnv | ForEach-Object {
    $linea = $_.Trim()
    if ($linea -and -not $linea.StartsWith('#') -and $linea.Contains('=')) {
      $corte = $linea.IndexOf('=')
      $clave = $linea.Substring(0, $corte).Trim()
      $valor = $linea.Substring($corte + 1).Trim().Trim('"')
      if ($valor) { $map[$clave] = $valor }
    }
  }
}
function Resolver([string]$Nombre, [string]$Default) {
  if ($map.ContainsKey($Nombre) -and $map[$Nombre]) { return $map[$Nombre] }
  $v = [Environment]::GetEnvironmentVariable($Nombre)
  if ($v) { return $v }
  return $Default
}

$dbHost = Resolver 'DB_HOST' '127.0.0.1'
$dbPort = Resolver 'DB_PORT' '3306'
$dbName = Resolver 'DB_NAME' 'canchadirecta'
$dbAdmin = Resolver 'DB_ADMIN_USER' 'root'
$dbAdminPwd = Resolver 'DB_ADMIN_PASSWORD' $null
if (-not $dbAdminPwd) { throw 'Falta DB_ADMIN_PASSWORD en .env o en el entorno.' }

$mysqldump = Get-Command mysqldump.exe -ErrorAction SilentlyContinue
if (-not $mysqldump) { throw "No se encontro 'mysqldump' en el PATH." }

if (-not $Salida) { $Salida = Join-Path $root 'db\canchadirecta.sql' }
elseif (-not [System.IO.Path]::IsPathRooted($Salida)) { $Salida = Join-Path $root $Salida }

$carpeta = Split-Path -Parent $Salida
if ($carpeta -and -not (Test-Path $carpeta)) { New-Item -ItemType Directory -Force -Path $carpeta | Out-Null }

$argumentos = @(
  '-h', $dbHost, '-P', "$dbPort", '-u', $dbAdmin, '--protocol=TCP',
  '--single-transaction', '--quick', '--routines', '--triggers', '--events',
  '--hex-blob', '--default-character-set=utf8mb4', '--set-gtid-purged=OFF',
  '--no-tablespaces'
)
if ($SoloEsquema) { $argumentos += '--no-data' }
$argumentos += $dbName

$previo = $env:MYSQL_PWD
$env:MYSQL_PWD = $dbAdminPwd
try {
  Write-Host "Exportando $dbName (${dbHost}:${dbPort}) ..." -ForegroundColor Cyan
  $contenido = & $mysqldump.Path @argumentos
  if ($LASTEXITCODE -ne 0) { throw "mysqldump termino con codigo $LASTEXITCODE" }
  $cabecera = @(
    "-- CanchaDirecta - volcado de base de datos",
    "-- Generado: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
    "-- Origen: $dbName en ${dbHost}:${dbPort}",
    "-- Restaurar con: .\scripts\db-import.ps1 -Archivo `"$([System.IO.Path]::GetFileName($Salida))`"",
    ''
  )
  $utf8SinBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllLines($Salida, ($cabecera + $contenido), $utf8SinBom)
}
finally { $env:MYSQL_PWD = $previo }

$tamano = [math]::Round((Get-Item $Salida).Length / 1KB, 1)
Write-Host "Listo: $Salida ($tamano KB)" -ForegroundColor Green
