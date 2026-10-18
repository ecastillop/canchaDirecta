<#
.SYNOPSIS
  Bootstrap SEGURO e idempotente de la base de datos CanchaDirecta en MySQL local.

.DESCRIPTION
  Crea (solo si no existen) la base del proyecto y los usuarios de aplicacion y
  migracion con permisos minimos. NO borra, dropea ni modifica otras bases.
  No altera la configuracion global del servidor MySQL.

  Resuelve credenciales desde variables de entorno o desde el archivo `.env`
  ubicado en la raiz del proyecto. Nunca imprime contrasenas.

.PARAMETER DryRun
  Muestra las sentencias que se ejecutarian sin conectarse ni cambiar nada.

.EXAMPLE
  $env:DB_ADMIN_USER='root'; $env:DB_ADMIN_PASSWORD='***'
  ./scripts/db-bootstrap-safe.ps1

.NOTES
  Prerrequisitos: cliente `mysql` en el PATH.
#>
[CmdletBinding()]
param(
  [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

function Get-EnvFileValues {
  param([string]$Path)
  $map = @{}
  if (Test-Path $Path) {
    Get-Content $Path | ForEach-Object {
      $line = $_.Trim()
      if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
        $idx = $line.IndexOf('=')
        $k = $line.Substring(0, $idx).Trim()
        $v = $line.Substring($idx + 1).Trim()
        if ($v) { $map[$k] = $v }
      }
    }
  }
  return $map
}

$root   = Split-Path -Parent $PSScriptRoot
$envMap = Get-EnvFileValues -Path (Join-Path $root '.env')

function Resolve-Value {
  param([string]$EnvName, [string]$Default, [hashtable]$Map)
  if ($Map.ContainsKey($EnvName) -and $Map[$EnvName]) { return $Map[$EnvName] }
  $v = [Environment]::GetEnvironmentVariable($EnvName)
  if ($v) { return $v }
  return $Default
}

$dbHost    = Resolve-Value 'DB_HOST'          '127.0.0.1'           $envMap
$dbPort    = Resolve-Value 'DB_PORT'          '3306'                $envMap
$dbName    = Resolve-Value 'DB_NAME'          'canchadirecta'       $envMap
$adminUser = Resolve-Value 'DB_ADMIN_USER'    'root'                $envMap
$adminPwd  = Resolve-Value 'DB_ADMIN_PASSWORD' $null                $envMap
$appUser   = Resolve-Value 'DB_USER'          'canchadirecta_app'   $envMap
$appPwd    = Resolve-Value 'DB_APP_PASSWORD'  $null                 $envMap
$migUser   = Resolve-Value 'DB_MIG_USER'      'canchadirecta_mig'   $envMap
$migPwd    = Resolve-Value 'DB_MIG_PASSWORD'  $null                 $envMap

if (-not $appPwd) { $appPwd = Resolve-Value 'DB_PASSWORD' $null $envMap }
if (-not $migPwd) { $migPwd = $appPwd }

# Validaciones de seguridad: nombres simples, sin backticks
foreach ($pair in @(@('DB_NAME',$dbName), @('DB_USER',$appUser), @('DB_MIG_USER',$migUser))) {
  if ($pair[1] -notmatch '^[A-Za-z0-9_]+$') {
    throw "Valor invalido para $($pair[0]): solo se permiten letras, numeros y guion bajo."
  }
}
if (-not $adminPwd) { throw "Falta DB_ADMIN_PASSWORD (env o .env). No se imprime ni se guarda en Git." }
if (-not $appPwd)   { throw "Falta DB_PASSWORD/DB_APP_PASSWORD (env o .env)." }
if (-not $migPwd)   { throw "Falta DB_MIG_PASSWORD (env o .env)." }

$mysql = (Get-Command mysql.exe -ErrorAction SilentlyContinue)
if (-not $mysql) { throw "No se encontro el cliente 'mysql' en el PATH." }

$sql = @"
CREATE DATABASE IF NOT EXISTS ``$dbName``
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Usuario de MIGRACION (Flyway: DDL + DML) sobre la base del proyecto
CREATE USER IF NOT EXISTS '$migUser'@'localhost' IDENTIFIED BY '$migPwd';
CREATE USER IF NOT EXISTS '$migUser'@'127.0.0.1' IDENTIFIED BY '$migPwd';
GRANT ALL PRIVILEGES ON ``$dbName``.* TO '$migUser'@'localhost';
GRANT ALL PRIVILEGES ON ``$dbName``.* TO '$migUser'@'127.0.0.1';

-- Usuario de RUNTIME (permisos minimos DML) sobre la base del proyecto
CREATE USER IF NOT EXISTS '$appUser'@'localhost' IDENTIFIED BY '$appPwd';
CREATE USER IF NOT EXISTS '$appUser'@'127.0.0.1' IDENTIFIED BY '$appPwd';
GRANT SELECT, INSERT, UPDATE, DELETE ON ``$dbName``.* TO '$appUser'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON ``$dbName``.* TO '$appUser'@'127.0.0.1';

FLUSH PRIVILEGES;
"@

if ($DryRun) {
  Write-Host "=== DRY RUN (no se conecta a MySQL) ===" -ForegroundColor Yellow
  Write-Host $sql
  return
}

$tmp = New-TemporaryFile
try {
  [System.IO.File]::WriteAllText($tmp.FullName, $sql, (New-Object System.Text.UTF8Encoding($false)))
  $mysqlArgs = @('-h', $dbHost, '-P', $dbPort, '-u', $adminUser, '--protocol=TCP')
  Write-Host "Aplicando bootstrap en ${dbHost}:${dbPort} (base: $dbName) ..." -ForegroundColor Cyan
  # Se pasa la contrasena por variable de entorno (MYSQL_PWD) para no exponerla
  # como argumento ni disparar el aviso "Using a password on the command line".
  $prevMysqlPwd = $env:MYSQL_PWD
  $env:MYSQL_PWD = $adminPwd
  try {
    Get-Content $tmp.FullName | & $mysql.Path @mysqlArgs
    if ($LASTEXITCODE -ne 0) { throw "mysql termino con codigo $LASTEXITCODE" }
  }
  finally {
    $env:MYSQL_PWD = $prevMysqlPwd
  }
  Write-Host "[OK] Base '$dbName' y usuarios '$migUser' / '$appUser' listos (si no existian)." -ForegroundColor Green
}
finally {
  Remove-Item $tmp.FullName -Force -ErrorAction SilentlyContinue
}
