<#
.SYNOPSIS
  Crea o habilita un usuario de desarrollo con una clave conocida y uno o mas roles.

.DESCRIPTION
  Pensado para entornos locales: registra la cuenta por la API (la clave se hashea
  con el mismo algoritmo que usa el backend) y asigna el rol por SQL reutilizando
  las credenciales de administrador de base de datos de `.env`.
  No imprime secretos y es idempotente: si la cuenta o el rol ya existen, no falla.

.EXAMPLE
  .\scripts\usuario-dev.ps1
  Crea ELVIS / 12345678 con los roles USER, OWNER y PLATFORM_ADMIN.

.EXAMPLE
  .\scripts\usuario-dev.ps1 -Correo operador2@canchadirecta.pe -Nombres Operador -Rol OPERADOR
  Crea una cuenta operativa (rol USER); el establecimiento se asigna desde /admin o demo-reset.
#>
param(
  [string]$Correo = 'elvis@canchadirecta.pe',
  [string]$Nombres = 'ELVIS',
  [string]$Apellidos = 'Dev',
  [string]$Clave = '12345678',
  [string]$Telefono = '999000111',
  [ValidateSet('USER', 'OWNER', 'PLATFORM_ADMIN', 'OPERADOR')]
  [string]$Rol = 'PLATFORM_ADMIN',
  [string]$BaseUrl = 'http://localhost:8089'
)

$ErrorActionPreference = 'Stop'

if ($Correo -notmatch '^[^@\s]+@[^@\s]+\.[^@\s]+$') { throw "Correo invalido: $Correo" }
if ($Clave.Length -lt 8) { throw 'La clave debe tener al menos 8 caracteres.' }

$root = Split-Path -Parent $PSScriptRoot
$archivoEnv = Join-Path $root '.env'
$envMap = @{}
if (Test-Path $archivoEnv) {
  Get-Content $archivoEnv | ForEach-Object {
    $linea = $_.Trim()
    if ($linea -and -not $linea.StartsWith('#') -and $linea.Contains('=')) {
      $corte = $linea.IndexOf('=')
      $claveEnv = $linea.Substring(0, $corte).Trim()
      $valor = $linea.Substring($corte + 1).Trim().Trim('"')
      if ($valor) { $envMap[$claveEnv] = $valor }
    }
  }
}

function Resolver([string]$Nombre, [string]$Default) {
  if ($envMap.ContainsKey($Nombre) -and $envMap[$Nombre]) { return $envMap[$Nombre] }
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
if ($dbName -notmatch '^[A-Za-z0-9_]+$') { throw 'DB_NAME invalido.' }

$mysqlExe = Get-Command mysql.exe -ErrorAction SilentlyContinue
if (-not $mysqlExe) { throw "No se encontro 'mysql' en el PATH." }

function Invoke-Api {
  param([string]$Method, [string]$Path, $Body = $null)
  $params = @{
    Method      = $Method
    Uri         = "$BaseUrl/api/v1$Path"
    Headers     = @{ Accept = 'application/json' }
    TimeoutSec  = 30
  }
  if ($null -ne $Body) {
    $params['Body'] = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Compress))
    $params['ContentType'] = 'application/json; charset=utf-8'
  }
  return Invoke-RestMethod @params
}

function Invoke-Sql {
  param([string]$Sql)
  $argumentos = @('-h', $dbHost, '-P', "$dbPort", '-u', $dbAdmin, '--protocol=TCP',
    '--default-character-set=utf8mb4', '-N', '-B', $dbName)
  $previo = $env:MYSQL_PWD
  $env:MYSQL_PWD = $dbAdminPwd
  try {
    $salida = $Sql | & $mysqlExe.Path @argumentos
    if ($LASTEXITCODE -ne 0) { throw "mysql termino con codigo $LASTEXITCODE" }
    return $salida
  }
  finally { $env:MYSQL_PWD = $previo }
}

# 1. Registro por API si la cuenta no existe (el backend hashea la clave).
$id = $null
try {
  $creado = Invoke-Api -Method POST -Path '/auth/register' -Body @{
    nombres              = $Nombres
    apellidos            = $Apellidos
    correo               = $Correo
    telefono             = $Telefono
    password             = $Clave
    confirmacionPassword = $Clave
  }
  $id = [long]$creado.id
  Write-Host "Cuenta creada: $Correo (#$id)" -ForegroundColor Green
}
catch {
  $detalle = if ($_.ErrorDetails) { $_.ErrorDetails.Message } else { '' }
  if ($detalle -notmatch '409|CONFLICT|ya existe|registrad') { throw }
  $id = [long](Invoke-Sql "SELECT id_usuario FROM usuario WHERE correo = '$Correo'").Trim()
  if (-not $id) { throw "La cuenta $Correo existe pero no se pudo resolver su id." }
  Write-Host "Cuenta existente: $Correo (#$id)" -ForegroundColor Yellow
}

# 2. Verifica la clave (si no coincide, se detiene sin tocar la cuenta).
try {
  Invoke-Api -Method POST -Path '/auth/login' -Body @{ correo = $Correo; password = $Clave } | Out-Null
  Write-Host 'Login verificado con la clave indicada.' -ForegroundColor Green
}
catch {
  throw "La cuenta $Correo existe pero su clave no es la indicada. Cambiala desde /auth/recuperar o usa otro correo."
}

# 3. Rol por SQL (no hay PLATFORM_ADMIN previo que pueda asignarlo por API).
$roles = switch ($Rol) {
  'PLATFORM_ADMIN' { @('USER', 'OWNER', 'PLATFORM_ADMIN') }
  'OWNER' { @('USER', 'OWNER') }
  'OPERADOR' { @('USER', 'OPERADOR') }
  default { @('USER') }
}
foreach ($r in $roles) {
  Invoke-Sql "INSERT IGNORE INTO usuario_rol (id_usuario, id_rol) SELECT $id, id_rol FROM rol WHERE nombre = '$r';" | Out-Null
}

# 4. Perfil funcional: propietario validado u operador vinculado a una sede.
if ($Rol -eq 'OWNER' -or $Rol -eq 'PLATFORM_ADMIN') {
  Invoke-Sql @"
INSERT INTO propietario (id_usuario, documento_identidad, estado_validacion)
SELECT $id, CONCAT('DEV', $id), 'VALIDADO'
WHERE NOT EXISTS (SELECT 1 FROM propietario WHERE id_usuario = $id);
UPDATE propietario SET estado_validacion = 'VALIDADO' WHERE id_usuario = $id;
"@ | Out-Null
  Write-Host 'Perfil de propietario validado.' -ForegroundColor Green
}
if ($Rol -eq 'OPERADOR') {
  $vinculo = Invoke-Sql @"
INSERT IGNORE INTO operador_establecimiento (id_usuario, id_establecimiento, cargo)
SELECT $id, MIN(id_establecimiento), 'Operador dev' FROM establecimiento;
SELECT COUNT(*) FROM operador_establecimiento WHERE id_usuario = $id;
"@
  if ("$vinculo".Trim() -eq '0') {
    Write-Host 'Aviso: no hay establecimientos para vincular al operador.' -ForegroundColor Yellow
  } else {
    Write-Host 'Operador vinculado a la primera sede.' -ForegroundColor Green
  }
}

$asignados = (Invoke-Sql "SELECT GROUP_CONCAT(r.nombre ORDER BY r.nombre) FROM usuario_rol ur JOIN rol r ON r.id_rol = ur.id_rol WHERE ur.id_usuario = $id;").Trim()
Write-Host "Roles de ${Correo}: $asignados" -ForegroundColor Cyan
Write-Host "Listo. Inicia sesion en /auth/login con $Correo y la clave indicada." -ForegroundColor Green
