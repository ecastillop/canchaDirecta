<#
.SYNOPSIS
  Restaura un volcado .sql de CanchaDirecta creado con `db-export.ps1`.

.DESCRIPTION
  Crea la base si no existe (utf8mb4), aplica el archivo y comprueba cuantas tablas quedaron.
  Usa las credenciales de administrador de MySQL del `.env`. Es destructivo sobre las tablas
  que existan en la base destino: pedira confirmacion salvo que se use `-Force`.

.PARAMETER Archivo
  Volcado a importar. Por defecto `db/canchadirecta.sql`.

.PARAMETER Base
  Base destino. Por defecto `DB_NAME` del `.env`.

.PARAMETER Force
  No pedir confirmacion.

.EXAMPLE
  .\scripts\db-import.ps1 -Force
  .\scripts\db-import.ps1 -Archivo db\respaldo-2026-10-19.sql -Base canchadirecta_demo -Force
#>
param(
  [string]$Archivo = '',
  [string]$Base = '',
  [switch]$Force
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

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
if (-not $Base) { $Base = Resolver 'DB_NAME' 'canchadirecta' }
$dbAdmin = Resolver 'DB_ADMIN_USER' 'root'
$dbAdminPwd = Resolver 'DB_ADMIN_PASSWORD' $null
if (-not $dbAdminPwd) { throw 'Falta DB_ADMIN_PASSWORD en .env o en el entorno.' }
if ($Base -notmatch '^[A-Za-z0-9_]+$') { throw 'Nombre de base invalido: solo letras, numeros y guion bajo.' }

if (-not $Archivo) { $Archivo = Join-Path $root 'db\canchadirecta.sql' }
elseif (-not [System.IO.Path]::IsPathRooted($Archivo)) { $Archivo = Join-Path $root $Archivo }
if (-not (Test-Path $Archivo)) { throw "No existe el archivo: $Archivo" }

$mysql = Get-Command mysql.exe -ErrorAction SilentlyContinue
if (-not $mysql) { throw "No se encontro 'mysql' en el PATH." }

Write-Host "Archivo: $Archivo" -ForegroundColor Cyan
Write-Host "Destino: $Base en ${dbHost}:${dbPort}" -ForegroundColor Cyan
if (-not $Force) {
  Write-Host 'Se reemplazaran las tablas de esa base con el contenido del volcado.' -ForegroundColor Yellow
  if ((Read-Host 'Escribe SI para continuar') -ne 'SI') { throw 'Operacion cancelada.' }
}

$argumentos = @('-h', $dbHost, '-P', "$dbPort", '-u', $dbAdmin, '--protocol=TCP',
  '--default-character-set=utf8mb4')
$previo = $env:MYSQL_PWD
$env:MYSQL_PWD = $dbAdminPwd
try {
  & $mysql.Path @argumentos -e "CREATE DATABASE IF NOT EXISTS $Base CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
  if ($LASTEXITCODE -ne 0) { throw "No se pudo crear la base $Base" }

  $proceso = Start-Process -FilePath $mysql.Path `
    -ArgumentList @('-h', $dbHost, '-P', "$dbPort", '-u', $dbAdmin, '--protocol=TCP',
      '--default-character-set=utf8mb4', $Base) `
    -RedirectStandardInput $Archivo -NoNewWindow -Wait -PassThru
  if ($proceso.ExitCode -ne 0) { throw "La importacion termino con codigo $($proceso.ExitCode)" }

  $tablas = (& $mysql.Path @argumentos -N -B -e `
      "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Base';").Trim()
  $usuarios = (& $mysql.Path @argumentos -N -B -e `
      "SELECT COUNT(*) FROM $Base.usuario;").Trim()
}
finally { $env:MYSQL_PWD = $previo }

Write-Host "Listo: $tablas tablas y $usuarios usuarios en $Base." -ForegroundColor Green
Write-Host 'Si la base es nueva, ejecuta scripts/db-bootstrap-safe.ps1 para crear los usuarios de la aplicacion.' -ForegroundColor Gray
