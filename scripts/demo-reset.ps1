<#
.SYNOPSIS
  Deja la base de demostracion en un estado limpio, conocido y reproducible.

.DESCRIPTION
  Pasos:
    1. Copia de seguridad de la base actual (mysqldump) en %TEMP%.
    2. Recrea la base del proyecto (DROP/CREATE) y reinicia el backend para que Flyway la migre.
    3. Siembra datos demo por la API real:
       - administrador de plataforma con las credenciales de .env (ADMIN_CORREO/ADMIN_PASSWORD),
       - propietario validado con 4 sedes y 16 canchas (deportes, caracteristicas, fotos y
         horario de 7 dias por cancha),
       - operador de recepcion vinculado a la sede principal,
       - cliente con dos reservas pagadas (una pendiente y una confirmada),
       - una actividad publica en la sede principal.

  Destruye TODOS los datos actuales de DB_NAME: exige -Force o confirmacion interactiva.
  No toca otras bases ni la configuracion del servidor MySQL.

.PARAMETER Force
  No pide confirmacion.

.PARAMETER SkipBackup
  No genera la copia de seguridad previa (no recomendado).

.PARAMETER NoRestart
  No reinicia el contenedor del backend (usar si el backend se levanta de otra forma).

.PARAMETER BaseUrl
  Backend a sembrar. Por defecto http://localhost:8089.

.PARAMETER DbPort
  Puerto de MySQL. Por defecto DB_PORT de .env.

.PARAMETER DemoPassword
  Clave de las cuentas demo. Por defecto DemoCancha2026.

.EXAMPLE
  ./scripts/demo-reset.ps1 -Force

.EXAMPLE
  # Sin copia de seguridad y con un backend en otro puerto
  ./scripts/demo-reset.ps1 -Force -SkipBackup -BaseUrl http://localhost:8088

.NOTES
  Prerrequisitos: cliente `mysql`/`mysqldump` en el PATH y el backend en marcha.
#>
[CmdletBinding()]
param(
  [switch]$Force,
  [switch]$SkipBackup,
  [switch]$NoRestart,
  [string]$BaseUrl = 'http://localhost:8089',
  [int]$DbPort = 0,
  [string]$DemoPassword = 'DemoCancha2026'
)

$ErrorActionPreference = 'Stop'

# ------------------------------------------------------------------ entorno

function Get-EnvFileValues {
  param([string]$Path)
  $map = @{}
  if (Test-Path $Path) {
    Get-Content $Path | ForEach-Object {
      $line = $_.Trim()
      if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
        $idx = $line.IndexOf('=')
        $k = $line.Substring(0, $idx).Trim()
        $v = $line.Substring($idx + 1).Trim().Trim('"')
        if ($v) { $map[$k] = $v }
      }
    }
  }
  return $map
}

function Resolve-Value {
  param([string]$EnvName, [string]$Default, [hashtable]$Map)
  if ($Map.ContainsKey($EnvName) -and $Map[$EnvName]) { return $Map[$EnvName] }
  $v = [Environment]::GetEnvironmentVariable($EnvName)
  if ($v) { return $v }
  return $Default
}

$root = Split-Path -Parent $PSScriptRoot
$envMap = Get-EnvFileValues -Path (Join-Path $root '.env')

$dbHost = Resolve-Value 'DB_HOST' '127.0.0.1' $envMap
$dbPortFinal = if ($DbPort -gt 0) { $DbPort } else { [int](Resolve-Value 'DB_PORT' '3306' $envMap) }
$dbName = Resolve-Value 'DB_NAME' 'canchadirecta' $envMap
$adminDbUser = Resolve-Value 'DB_ADMIN_USER' 'root' $envMap
$adminDbPwd = Resolve-Value 'DB_ADMIN_PASSWORD' $null $envMap
$appDbUser = Resolve-Value 'DB_USER' 'canchadirecta_app' $envMap
$appDbPwd = Resolve-Value 'DB_PASSWORD' $null $envMap
$adminCorreo = Resolve-Value 'ADMIN_CORREO' $null $envMap
$adminPassword = Resolve-Value 'ADMIN_PASSWORD' $null $envMap
$api = "$BaseUrl/api/v1"

foreach ($par in @(@('DB_NAME', $dbName), @('DB_USER', $appDbUser))) {
  if ($par[1] -notmatch '^[A-Za-z0-9_]+$') {
    throw "Valor invalido para $($par[0]): solo letras, numeros y guion bajo."
  }
}
if (-not $adminDbPwd) { throw "Falta DB_ADMIN_PASSWORD (env o .env)." }
if (-not $appDbPwd) { throw "Falta DB_PASSWORD (env o .env)." }
if (-not $adminCorreo -or -not $adminPassword) {
  throw "Faltan ADMIN_CORREO / ADMIN_PASSWORD en .env: son las credenciales del administrador demo."
}

$mysqlExe = (Get-Command mysql.exe -ErrorAction SilentlyContinue)
if (-not $mysqlExe) { throw "No se encontro el cliente 'mysql' en el PATH." }
$mysqldumpExe = (Get-Command mysqldump.exe -ErrorAction SilentlyContinue)

if (-not $Force) {
  Write-Host "Se eliminaran TODOS los datos de la base '$dbName' en ${dbHost}:${dbPortFinal}." -ForegroundColor Yellow
  Write-Host "Se sembraran despues los datos demo (4 sedes, 16 canchas, operador, cliente y reservas)." -ForegroundColor Yellow
  $respuesta = Read-Host "Escribe SI para continuar"
  if ($respuesta -ne 'SI') { throw "Operacion cancelada." }
}

Write-Host "Base: $dbName @ ${dbHost}:${dbPortFinal}" -ForegroundColor Cyan

# ------------------------------------------------------------------ MySQL

function Invoke-MySql {
  param([string]$Sql, [switch]$Admin, [switch]$Bare)
  $usuario = if ($Admin) { $adminDbUser } else { $appDbUser }
  $clave = if ($Admin) { $adminDbPwd } else { $appDbPwd }
  $argumentos = @('-h', $dbHost, '-P', "$dbPortFinal", '-u', $usuario, '--protocol=TCP',
    '--default-character-set=utf8mb4')
  if ($Bare) { $argumentos += @('-N', '-B') }
  $argumentos += $dbName
  $previo = $env:MYSQL_PWD
  $env:MYSQL_PWD = $clave
  try {
    $Sql | & $mysqlExe.Path @argumentos
    if ($LASTEXITCODE -ne 0) { throw "mysql termino con codigo $LASTEXITCODE" }
  }
  finally { $env:MYSQL_PWD = $previo }
}

function Invoke-MySqlServer {
  param([string]$Sql)
  $argumentos = @('-h', $dbHost, '-P', "$dbPortFinal", '-u', $adminDbUser, '--protocol=TCP',
    '--default-character-set=utf8mb4')
  $previo = $env:MYSQL_PWD
  $env:MYSQL_PWD = $adminDbPwd
  try {
    $Sql | & $mysqlExe.Path @argumentos
    if ($LASTEXITCODE -ne 0) { throw "mysql termino con codigo $LASTEXITCODE" }
  }
  finally { $env:MYSQL_PWD = $previo }
}

# --------------------------------------------------------------- API REST

function Invoke-Api {
  param(
    [string]$Method = 'GET',
    [string]$Path,
    $Body = $null,
    [string]$Token = ''
  )
  $cabeceras = @{ Accept = 'application/json' }
  if ($Token) { $cabeceras['Authorization'] = "Bearer $Token" }
  $params = @{ Method = $Method; Uri = ($api + $Path); Headers = $cabeceras; TimeoutSec = 30 }
  if ($null -ne $Body) {
    $json = $Body | ConvertTo-Json -Depth 8 -Compress
    $params['Body'] = [Text.Encoding]::UTF8.GetBytes($json)
    $params['ContentType'] = 'application/json; charset=utf-8'
  }
  try {
    return Invoke-RestMethod @params
  }
  catch {
    $detalle = ''
    if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $detalle = $_.ErrorDetails.Message }
    throw ("{0} {1} fallo: {2} {3}" -f $Method, $Path, $_.Exception.Message, $detalle)
  }
}

function New-Cuenta {
  param([string]$Nombres, [string]$Apellidos, [string]$Correo, [string]$Telefono, [string]$Clave)
  $registro = Invoke-Api -Method POST -Path '/auth/register' -Body @{
    nombres             = $Nombres
    apellidos           = $Apellidos
    correo              = $Correo
    telefono            = $Telefono
    password            = $Clave
    confirmacionPassword = $Clave
  }
  $login = Invoke-Api -Method POST -Path '/auth/login' -Body @{ correo = $Correo; password = $Clave }
  return [pscustomobject]@{ id = [long]$registro.id; correo = $Correo; token = $login.accessToken }
}

function Wait-Backend {
  param([int]$Segundos = 240)
  $limite = (Get-Date).AddSeconds($Segundos)
  while ((Get-Date) -lt $limite) {
    try {
      $salud = Invoke-RestMethod -Uri "$BaseUrl/q/health" -TimeoutSec 5
      if ($salud.status -eq 'UP') {
        Invoke-Api -Path '/ubicaciones/distritos?limite=1' | Out-Null
        return
      }
    }
    catch { }
    Start-Sleep -Seconds 2
  }
  throw "El backend no respondio con el esquema migrado en $Segundos segundos ($BaseUrl)."
}

# -------------------------------------------------------- 1. copia de seguridad

if ($SkipBackup) {
  Write-Host "[1/4] Copia de seguridad omitida (-SkipBackup)." -ForegroundColor Yellow
}
elseif (-not $mysqldumpExe) {
  throw "No se encontro 'mysqldump' en el PATH. Usa -SkipBackup solo si asumes el riesgo."
}
else {
  $marca = Get-Date -Format 'yyyyMMdd-HHmmss'
  $copia = Join-Path $env:TEMP "canchadirecta-demo-$marca.sql"
  $previo = $env:MYSQL_PWD
  $env:MYSQL_PWD = $adminDbPwd
  try {
    & $mysqldumpExe.Path '--single-transaction' '--no-tablespaces' '--routines' '--events' `
      '-h' $dbHost '-P' "$dbPortFinal" '-u' $adminDbUser "--result-file=$copia" $dbName
    if ($LASTEXITCODE -ne 0) { throw "mysqldump termino con codigo $LASTEXITCODE" }
  }
  finally { $env:MYSQL_PWD = $previo }
  $tamano = [math]::Round((Get-Item $copia).Length / 1MB, 2)
  Write-Host "[1/4] Copia de seguridad: $copia ($tamano MB)" -ForegroundColor Green
}

# ------------------------------------------------------------ 2. base nueva

Write-Host "[2/4] Recreando la base '$dbName' ..." -ForegroundColor Cyan
Invoke-MySqlServer -Sql @"
DROP DATABASE IF EXISTS ``$dbName``;
CREATE DATABASE ``$dbName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
"@

if (-not $NoRestart) {
  $contenedor = docker ps -a --filter 'name=^canchadirecta-backend-1$' --format '{{.Names}}' 2>$null
  if ($contenedor) {
    docker restart $contenedor | Out-Null
    Write-Host "      backend reiniciado ($contenedor); Flyway aplicara las migraciones." -ForegroundColor Gray
  }
  else {
    Write-Host "      No se encontro el contenedor del backend: reinicialo para que Flyway migre." -ForegroundColor Yellow
  }
}
Wait-Backend
$tablas = (Invoke-MySql -Admin -Bare -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$dbName' AND table_type = 'BASE TABLE';").Trim()
if ([int]$tablas -lt 20) { throw "Flyway no creo el esquema (tablas: $tablas). Revisa el arranque del backend." }
Write-Host "      esquema migrado ($tablas tablas)." -ForegroundColor Green

# -------------------------------------------------------------- 3. semilla

Write-Host "[3/4] Sembrando datos demo ..." -ForegroundColor Cyan

# 3.1 Administrador (roles por SQL: aun no existe ningun PLATFORM_ADMIN que pueda asignarlos)
$admin = New-Cuenta -Nombres 'Admin' -Apellidos 'CanchaDirecta' -Correo $adminCorreo -Telefono '900000001' -Clave $adminPassword
Invoke-MySql -Sql @"
INSERT IGNORE INTO usuario_rol (id_usuario, id_rol)
SELECT $($admin.id), id_rol FROM rol WHERE nombre IN ('USER','OWNER','PLATFORM_ADMIN');
"@
$admin = [pscustomobject]@{ id = $admin.id; correo = $admin.correo
  token = (Invoke-Api -Method POST -Path '/auth/login' -Body @{ correo = $adminCorreo; password = $adminPassword }).accessToken }
Write-Host "      admin: $($admin.correo)"

# 3.2 Propietario validado + operador + cliente
$propietario = New-Cuenta -Nombres 'Paola' -Apellidos 'Quispe' -Correo 'propietario@canchadirecta.pe' -Telefono '900000002' -Clave $DemoPassword
$solicitud = Invoke-Api -Method POST -Path '/propietarios/solicitudes' -Token $propietario.token -Body @{
  documentoIdentidad = ("DEMO{0}" -f (Get-Date -Format 'yyMMddHHmm'))
}
Invoke-Api -Method POST -Path ("/admin/propietarios/{0}/validacion" -f [long]$solicitud.id) -Token $admin.token `
  -Body @{ estado = 'VALIDADO' } | Out-Null
Invoke-Api -Method POST -Path ("/admin/usuarios/{0}/roles" -f $propietario.id) -Token $admin.token `
  -Body @{ roles = @('USER', 'OWNER') } | Out-Null
$propietario.token = (Invoke-Api -Method POST -Path '/auth/login' -Body @{
  correo = $propietario.correo; password = $DemoPassword }).accessToken
Write-Host "      propietario: $($propietario.correo)"

$operador = New-Cuenta -Nombres 'Ruben' -Apellidos 'Salas' -Correo 'operador@canchadirecta.pe' -Telefono '900000003' -Clave $DemoPassword
Invoke-Api -Method POST -Path ("/admin/usuarios/{0}/roles" -f $operador.id) -Token $admin.token `
  -Body @{ roles = @('USER', 'OPERADOR') } | Out-Null
$operador.token = (Invoke-Api -Method POST -Path '/auth/login' -Body @{
  correo = $operador.correo; password = $DemoPassword }).accessToken
Write-Host "      operador: $($operador.correo)"

$cliente = New-Cuenta -Nombres 'Lucia' -Apellidos 'Torres' -Correo 'cliente@canchadirecta.pe' -Telefono '900000004' -Clave $DemoPassword
Write-Host "      cliente: $($cliente.correo)"

# 3.3 Catalogos de apoyo (ids por nombre, no hardcodeados)
$distritos = Invoke-Api -Path '/ubicaciones/distritos?limite=100'
$deportes = Invoke-Api -Path '/deportes'
$caracteristicas = Invoke-Api -Path '/caracteristicas'

function Get-IdPorNombre {
  param($Lista, [string]$Nombre)
  $fila = $Lista | Where-Object { $_.nombre -eq $Nombre } | Select-Object -First 1
  if (-not $fila) { throw "No se encontro '$Nombre' en el catalogo." }
  return [long]$fila.id
}

function Get-IdDistrito {
  param([string]$Nombre)
  $fila = $distritos | Where-Object { $_.nombre -eq $Nombre } | Select-Object -First 1
  if (-not $fila) { $fila = $distritos | Select-Object -First 1 }
  return [long]$fila.id
}

$sedes = @(
  @{
    nombre = 'Complejo La Bombonera'; distrito = 'Santiago de Surco'
    direccion = 'Av. Los Próceres 450'; referencia = 'Frente al parque municipal'
    canchas = @(
      @{ nombre = 'Cancha sintética 1'; descripcion = 'Fútbol 7 con césped sintético y luces LED'
         capacidad = 14; precio = 90; deportes = @('Fútbol 7')
         caracteristicas = @('Césped sintético', 'Iluminación nocturna', 'Vestuarios'); inicio = '08:00'; fin = '23:00' }
      @{ nombre = 'Cancha sintética 2'; descripcion = 'Fútbol 7 techada para jugar todo el año'
         capacidad = 14; precio = 85; deportes = @('Fútbol 7')
         caracteristicas = @('Césped sintético', 'Iluminación nocturna'); inicio = '08:00'; fin = '23:00' }
      @{ nombre = 'Cancha de futsal'; descripcion = 'Futsal techado con piso de parquet'
         capacidad = 10; precio = 70; deportes = @('Fútbol Sala / Futsal')
         caracteristicas = @('Techada', 'Vestuarios'); inicio = '09:00'; fin = '22:00' }
      @{ nombre = 'Cancha de vóley'; descripcion = 'Vóley de arena con graderías'
         capacidad = 12; precio = 55; deportes = @('Vóley')
         caracteristicas = @('Césped sintético', 'Graderías'); inicio = '09:00'; fin = '22:00' }
    )
  }
  @{
    nombre = 'Arena San Borja'; distrito = 'San Borja'
    direccion = 'Av. Aviación 2100'; referencia = 'Al costado del centro comercial'
    canchas = @(
      @{ nombre = 'Cancha principal'; descripcion = 'Fútbol 11 con césped natural'
         capacidad = 22; precio = 140; deportes = @('Fútbol')
         caracteristicas = @('Césped natural', 'Iluminación nocturna', 'Estacionamiento'); inicio = '07:00'; fin = '23:00' }
      @{ nombre = 'Cancha 7'; descripcion = 'Fútbol 7 con duchas y vestuarios'
         capacidad = 14; precio = 85; deportes = @('Fútbol 7')
         caracteristicas = @('Césped sintético', 'Duchas'); inicio = '08:00'; fin = '23:00' }
      @{ nombre = 'Cancha de básquet'; descripcion = 'Básquet techado con tablero profesional'
         capacidad = 10; precio = 45; deportes = @('Básquet')
         caracteristicas = @('Techada', 'Graderías'); inicio = '09:00'; fin = '22:00' }
      @{ nombre = 'Cancha de tenis 1'; descripcion = 'Tenis con iluminación para jugar de noche'
         capacidad = 4; precio = 60; deportes = @('Tenis')
         caracteristicas = @('Césped sintético', 'Iluminación nocturna'); inicio = '07:00'; fin = '21:00' }
    )
  }
  @{
    nombre = 'Club Miraflores'; distrito = 'Miraflores'
    direccion = 'Malecón Cisneros 120'; referencia = 'Ingreso por la terraza'
    canchas = @(
      @{ nombre = 'Cancha terraza'; descripcion = 'Fútbol 5 en la terraza con vista al mar'
         capacidad = 10; precio = 75; deportes = @('Fútbol Sala / Futsal')
         caracteristicas = @('Techada', 'Iluminación nocturna', 'Cafetería'); inicio = '09:00'; fin = '23:00' }
      @{ nombre = 'Cancha frente al mar'; descripcion = 'Fútbol 7 con graderías'
         capacidad = 14; precio = 95; deportes = @('Fútbol 7')
         caracteristicas = @('Césped sintético', 'Graderías'); inicio = '08:00'; fin = '22:00' }
      @{ nombre = 'Cancha de pádel 1'; descripcion = 'Pádel con cristal panorámico'
         capacidad = 4; precio = 50; deportes = @('Pádel')
         caracteristicas = @('Iluminación nocturna', 'Vestuarios'); inicio = '08:00'; fin = '22:00' }
      @{ nombre = 'Cancha de pádel 2'; descripcion = 'Pádel con cristal panorámico'
         capacidad = 4; precio = 50; deportes = @('Pádel')
         caracteristicas = @('Iluminación nocturna', 'Vestuarios'); inicio = '08:00'; fin = '22:00' }
    )
  }
  @{
    nombre = 'Complejo La Molina'; distrito = 'La Molina'
    direccion = 'Av. Raúl Ferrero 800'; referencia = 'Portón negro, ingreso peatonal'
    canchas = @(
      @{ nombre = 'Cancha 1'; descripcion = 'Fútbol 7 con estacionamiento propio'
         capacidad = 14; precio = 80; deportes = @('Fútbol 7')
         caracteristicas = @('Césped sintético', 'Estacionamiento', 'Duchas'); inicio = '08:00'; fin = '23:00' }
      @{ nombre = 'Cancha 2'; descripcion = 'Fútbol 7 con estacionamiento propio'
         capacidad = 14; precio = 80; deportes = @('Fútbol 7')
         caracteristicas = @('Césped sintético', 'Estacionamiento'); inicio = '08:00'; fin = '23:00' }
      @{ nombre = 'Cancha techada'; descripcion = 'Fútbol 5 techado'
         capacidad = 10; precio = 65; deportes = @('Fútbol Sala / Futsal')
         caracteristicas = @('Techada', 'Iluminación nocturna'); inicio = '09:00'; fin = '22:00' }
      @{ nombre = 'Cancha de frontón'; descripcion = 'Frontón techado'
         capacidad = 6; precio = 40; deportes = @('Frontón')
         caracteristicas = @('Techada', 'Vestuarios'); inicio = '09:00'; fin = '21:00' }
    )
  }
)

$foto = 0
$canchasCreadas = @()
$idSedePrincipal = $null
$nombreSedePrincipal = ''
foreach ($sede in $sedes) {
  $idDistrito = Get-IdDistrito $sede.distrito
  $establecimiento = Invoke-Api -Method POST -Path '/establecimientos' -Token $propietario.token -Body @{
    documentoIdentidad = ("DEMO{0}" -f (Get-Date -Format 'yyMMddHHmmss'))
    idDistrito         = $idDistrito
    nombre             = $sede.nombre
    direccion          = $sede.direccion
    referencia         = $sede.referencia
  }
  if (-not $idSedePrincipal) {
    $idSedePrincipal = [long]$establecimiento.id
    $nombreSedePrincipal = $sede.nombre
  }
  Start-Sleep -Milliseconds 300

  foreach ($cancha in $sede.canchas) {
    $creada = Invoke-Api -Method POST -Path ("/establecimientos/{0}/canchas" -f [long]$establecimiento.id) `
      -Token $propietario.token -Body @{
        nombre      = $cancha.nombre
        descripcion = $cancha.descripcion
        capacidad   = $cancha.capacidad
        precioBase  = $cancha.precio
      }
    $idCancha = [long]$creada.id

    $idsDeporte = @($cancha.deportes | ForEach-Object { Get-IdPorNombre $deportes $_ })
    Invoke-Api -Method PUT -Path ("/canchas/{0}/deportes" -f $idCancha) -Token $propietario.token `
      -Body @{ ids = $idsDeporte } | Out-Null

    $idsCaracteristica = @($cancha.caracteristicas | ForEach-Object { Get-IdPorNombre $caracteristicas $_ })
    Invoke-Api -Method PUT -Path ("/canchas/{0}/caracteristicas" -f $idCancha) -Token $propietario.token `
      -Body @{ ids = $idsCaracteristica } | Out-Null

    foreach ($orden in 1..2) {
      $foto++
      Invoke-Api -Method POST -Path ("/canchas/{0}/fotos" -f $idCancha) -Token $propietario.token -Body @{
        url       = "https://picsum.photos/seed/canchadirecta-demo-$foto/800/600"
        orden     = $orden
        principal = ($orden -eq 1)
      } | Out-Null
    }

    foreach ($dia in 1..7) {
      Invoke-Api -Method POST -Path ("/canchas/{0}/horarios" -f $idCancha) -Token $propietario.token -Body @{
        diaSemana  = $dia
        horaInicio = $cancha.inicio
        horaFin    = $cancha.fin
        precio     = $cancha.precio
      } | Out-Null
    }

    $canchasCreadas += [pscustomobject]@{
      id = $idCancha; nombre = $cancha.nombre; sede = $sede.nombre; precio = $cancha.precio
    }
    Write-Host ("      cancha #{0} {1} ({2})" -f $idCancha, $cancha.nombre, $sede.nombre) -ForegroundColor Gray
  }
}

# 3.4 Operador vinculado a la sede principal
Invoke-Api -Method POST -Path ("/admin/establecimientos/{0}/operadores" -f $idSedePrincipal) `
  -Token $admin.token -Body @{ idUsuario = $operador.id; cargo = 'Recepcionista' } | Out-Null
Write-Host "      operador vinculado a '$nombreSedePrincipal'"

# 3.5 Actividad publica en la sede principal (manana 18:00, hora de Lima)
$inicioActividad = ([DateTime]::UtcNow.AddHours(-5)).Date.AddDays(1).AddHours(18)
$finActividad = $inicioActividad.AddHours(2)
$iso = { param($fecha) $fecha.ToString("yyyy-MM-dd'T'HH:mm:ss") + '-05:00' }
Invoke-Api -Method POST -Path ("/establecimientos/{0}/actividades" -f $idSedePrincipal) `
  -Token $propietario.token -Body @{
    nombre      = 'Torneo relámpago de fútbol 7'
    descripcion = 'Fecha única: inscripción por equipo de 7 jugadores.'
    inicio      = (& $iso $inicioActividad)
    fin         = (& $iso $finActividad)
    cupos       = 12
    precio      = 25
  } | Out-Null
Write-Host "      actividad publica creada"

# 3.6 Reservas del cliente: una pendiente y una confirmada, ambas pagadas
function Get-FranjasLibres {
  param([long]$IdCancha, [string]$Fecha)
  $res = Invoke-Api -Path ("/canchas/{0}/disponibilidad?fecha={1}&duracionMinutos=60" -f $IdCancha, $Fecha)
  return @($res.franjas)
}

$hoyLima = [DateTime]::UtcNow.AddHours(-5)
$fechas = @(0..6 | ForEach-Object { $hoyLima.Date.AddDays($_).ToString('yyyy-MM-dd') })
$canchaReserva1 = $canchasCreadas[0]
$canchaReserva2 = $canchasCreadas[1]

function Find-FranjaFutura {
  param([long]$IdCancha)
  foreach ($fecha in $fechas) {
    $franjas = Get-FranjasLibres -IdCancha $IdCancha -Fecha $fecha
    foreach ($franja in $franjas) {
      $inicio = [DateTimeOffset]::Parse($franja.inicio)
      if ($inicio -gt [DateTimeOffset]::UtcNow.AddMinutes(90)) { return $franja }
    }
  }
  return $null
}

$franja1 = Find-FranjaFutura -IdCancha $canchaReserva1.id
$franja2 = Find-FranjaFutura -IdCancha $canchaReserva2.id
if (-not $franja1 -or -not $franja2) { throw "No se encontraron franjas futuras para las reservas demo." }

$reservaPendiente = Invoke-Api -Method POST -Path '/reservas' -Token $cliente.token -Body @{
  canchaId = $canchaReserva1.id; inicio = $franja1.inicio; fin = $franja1.fin
}
Invoke-Api -Method POST -Path ("/reservas/{0}/pago" -f [long]$reservaPendiente.id) -Token $cliente.token `
  -Body @{ medioPago = 'TARJETA' } | Out-Null

$reservaConfirmada = Invoke-Api -Method POST -Path '/reservas' -Token $cliente.token -Body @{
  canchaId = $canchaReserva2.id; inicio = $franja2.inicio; fin = $franja2.fin
}
Invoke-Api -Method POST -Path ("/reservas/{0}/pago" -f [long]$reservaConfirmada.id) -Token $cliente.token `
  -Body @{ medioPago = 'YAPE' } | Out-Null
Invoke-Api -Method POST -Path ("/reservas/{0}/confirmar" -f [long]$reservaConfirmada.id) `
  -Token $propietario.token | Out-Null
Write-Host "      reservas: #$($reservaPendiente.id) pendiente pagada y #$($reservaConfirmada.id) confirmada pagada"

# 3.7 Bloqueo de mantenimiento: la ficha y la agenda muestran tambien "Bloqueada" (ADR-0047)
$inicioBloqueo = $hoyLima.Date.AddDays(2).AddHours(10)
$finBloqueo = $inicioBloqueo.AddHours(2)
Invoke-Api -Method POST -Path ("/canchas/{0}/bloqueos" -f $canchaReserva1.id) -Token $propietario.token -Body @{
  inicio = (& $iso $inicioBloqueo)
  fin    = (& $iso $finBloqueo)
  motivo = 'Mantenimiento de la cancha'
} | Out-Null
Write-Host "      bloqueo de mantenimiento en '$($canchaReserva1.nombre)'"

# 3.8 Reclamo del cliente: la mesa de ayuda del administrador tiene un caso para revisar
Invoke-Api -Method POST -Path '/reclamos' -Token $cliente.token -Body @{
  idReserva   = [long]$reservaConfirmada.id
  asunto      = 'No encuentro el comprobante de pago'
  descripcion = 'Pague con Yape y no veo el comprobante en mis reservas. Pueden revisarlo?'
} | Out-Null
Write-Host "      reclamo de soporte creado"

# --------------------------------------------------------------- 4. resumen

Write-Host "[4/4] Verificando ..." -ForegroundColor Cyan
$conteo = Invoke-MySql -Bare -Sql @"
SELECT CONCAT('usuarios=', (SELECT COUNT(*) FROM usuario),
  ' establecimientos=', (SELECT COUNT(*) FROM establecimiento),
  ' canchas=', (SELECT COUNT(*) FROM cancha),
  ' horarios=', (SELECT COUNT(*) FROM horario_cancha),
  ' fotos=', (SELECT COUNT(*) FROM foto_cancha),
  ' reservas=', (SELECT COUNT(*) FROM reserva),
  ' bloqueos=', (SELECT COUNT(*) FROM bloqueo_cancha),
  ' reclamos=', (SELECT COUNT(*) FROM reclamo),
  ' actividades=', (SELECT COUNT(*) FROM actividad));
"@
Write-Host ("      " + $conteo.Trim()) -ForegroundColor Gray

Write-Host ""
Write-Host "[OK] Base de demostracion lista." -ForegroundColor Green
Write-Host "     Frontend: http://localhost:8083" -ForegroundColor Gray
Write-Host "     Cuentas demo (clave '$DemoPassword'):" -ForegroundColor Gray
Write-Host "       propietario@canchadirecta.pe  -> /panel" -ForegroundColor Gray
Write-Host "       operador@canchadirecta.pe     -> /recepcion" -ForegroundColor Gray
Write-Host "       cliente@canchadirecta.pe      -> /mis-reservas" -ForegroundColor Gray
Write-Host "     Administrador: $adminCorreo (clave ADMIN_PASSWORD de .env)" -ForegroundColor Gray
