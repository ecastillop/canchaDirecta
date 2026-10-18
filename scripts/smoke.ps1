<#
.SYNOPSIS
  Prueba de humo de la API de CanchaDirecta (Windows/PowerShell).

.DESCRIPTION
  Recorre el flujo real contra una instancia en ejecucion: health -> registro ->
  login -> /users/me -> refresh (rotacion) -> logout -> recuperacion de clave ->
  establecimiento -> cancha -> listado publico -> disponibilidad -> reserva (409 en
  la misma franja) -> /reservas/mias -> bandeja del propietario y confirmacion/rechazo
  (HU-008-2) -> cancelacion -> horarios del propietario ->
  bloqueo (RN-03) y su efecto en la disponibilidad -> cochera opcional (RN-08: alta,
  cupo publico, reserva con `cocheraId` y liberacion al cancelar) -> pago simulado y
  reembolso (CU05.1/CU05.3) -> liquidacion del propietario (CU04.8/CU05.4) ->
  revocacion (L-14) -> calificacion de una reserva finalizada (F06, CU03.8/CU02.5, RN-04).
  Comprueba los codigos HTTP esperados. Los pasos de calificacion necesitan el cliente `mysql`
  y las credenciales de `.env` (DB_USER/DB_PASSWORD/DB_NAME), porque solo se califica una
  reserva FINALIZADA y el humo debe retrasar la franja para que el job de cierre la finalice;
  si no estan disponibles, esos pasos se omiten con un aviso.

.EXAMPLE
  ./smoke.ps1
  ./smoke.ps1 -BaseUrl http://localhost:8089 -DbPort 3309   # backend + MySQL en contenedor
#>
[CmdletBinding()]
param(
  [string]$BaseUrl = 'http://localhost:8088',
  [int]$IdDistrito = 1,
  [string]$DbHost = '',
  [int]$DbPort = 0,
  [string]$DbUser = '',
  [string]$DbPassword = '',
  [string]$DbName = ''
)

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
$fallos = 0
$pasos  = 0

function Paso([string]$nombre, [scriptblock]$accion) {
  $script:pasos++
  try {
    & $accion
    Write-Host ("[PASS] {0}" -f $nombre) -ForegroundColor Green
  } catch {
    $script:fallos++
    Write-Host ("[FAIL] {0} -> {1}" -f $nombre, $_.Exception.Message) -ForegroundColor Red
  }
}

function Invoke-Api {
  param(
    [string]$Method,
    [string]$Path,
    [object]$Body,
    [string]$Token,
    [int[]]$Esperado = @(200)
  )
  $headers = @{}
  if ($Token) { $headers['Authorization'] = "Bearer $Token" }
  $params = @{
    Method      = $Method
    Uri         = "$BaseUrl$Path"
    Headers     = $headers
    ContentType = 'application/json; charset=utf-8'
  }
  if ($null -ne $Body) {
    # Se envian bytes UTF-8 explicitos: PowerShell 5.1 codifica los cuerpos de tipo String
    # en ISO-8859-1, lo que romperia el JSON con acentos ('Futbol').
    $json = $Body | ConvertTo-Json -Compress -Depth 5
    $params['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
  }

  try {
    $resp = Invoke-WebRequest @params -UseBasicParsing
    $codigo = [int]$resp.StatusCode
    $contenido = $resp.Content
  } catch {
    $r = $_.Exception.Response
    if ($null -eq $r) { throw }
    $codigo = [int]$r.StatusCode
    # En PowerShell 5.1 el stream del error ya llega consumido: el cuerpo de la
    # respuesta de error esta en ErrorDetails.Message.
    $contenido = $_.ErrorDetails.Message
    if ([string]::IsNullOrWhiteSpace($contenido)) {
      try { $contenido = (New-Object System.IO.StreamReader($r.GetResponseStream())).ReadToEnd() }
      catch { $contenido = $null }
    }
  }

  if ($Esperado -notcontains $codigo) {
    throw "esperaba $($Esperado -join '/') y recibio $codigo :: $contenido"
  }
  if ([string]::IsNullOrWhiteSpace($contenido)) { return $null }
  return $contenido | ConvertFrom-Json
}

# --- Acceso a MySQL para los pasos de calificacion ----------------------------
# Las credenciales se toman de los parametros, del entorno o del `.env` del proyecto. El
# cliente `mysql` se usa solo para retrasar la franja de la reserva que se va a calificar;
# el resto del humo sigue siendo HTTP puro.
$envFile = Join-Path (Split-Path -Parent $PSScriptRoot) '.env'
if (Test-Path $envFile) {
  Get-Content $envFile | Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_]*\s*=' -and $_ -notmatch '^\s*#' } |
    ForEach-Object {
      $par = $_ -split '=', 2
      $clave = $par[0].Trim()
      $valor = $par[1].Trim().Trim('"')
      if (-not (Test-Path "Env:$clave")) { Set-Item -Path "Env:$clave" -Value $valor }
    }
}
$script:dbHost = if ($DbHost) { $DbHost } elseif ($env:DB_HOST) { $env:DB_HOST } else { '127.0.0.1' }
$script:dbPort = if ($DbPort -gt 0) { $DbPort } elseif ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }
$script:dbUser = if ($DbUser) { $DbUser } else { $env:DB_USER }
$script:dbPass = if ($DbPassword) { $DbPassword } else { $env:DB_PASSWORD }
$script:dbName = if ($DbName) { $DbName } elseif ($env:DB_NAME) { $env:DB_NAME } else { 'canchadirecta' }
$script:mysql = Get-Command mysql.exe -ErrorAction SilentlyContinue
if (-not $script:mysql) { $script:mysql = Get-Command mysql -ErrorAction SilentlyContinue }
$script:dbDisponible = [bool]($script:mysql -and $script:dbUser -and $script:dbPass)

function Invoke-Sql([string]$sql) {
  $previo = $env:MYSQL_PWD
  $env:MYSQL_PWD = $script:dbPass
  try {
    $salida = & $script:mysql.Source -h $script:dbHost -P $script:dbPort -u $script:dbUser `
      --protocol=TCP -N -B $script:dbName -e $sql 2>&1
    if ($LASTEXITCODE -ne 0) { throw "mysql fallo ($LASTEXITCODE): $salida" }
    return $salida
  } finally {
    $env:MYSQL_PWD = $previo
  }
}

Write-Host "== Smoke test CanchaDirecta :: $BaseUrl ==" -ForegroundColor Cyan

$correo = "smoke.$([DateTime]::UtcNow.Ticks)@canchadirecta.pe"
$password = 'ClaveSegura123'

Paso 'GET /api/v1/system/ping' {
  Invoke-Api -Method GET -Path '/api/v1/system/ping' | Out-Null
}

Paso 'GET /q/health/ready' {
  $h = Invoke-Api -Method GET -Path '/q/health/ready'
  if ($h.status -ne 'UP') { throw "estado de salud: $($h.status)" }
}

Paso 'POST /api/v1/auth/register (201)' {
  Invoke-Api -Method POST -Path '/api/v1/auth/register' -Esperado @(201) -Body @{
    nombres = 'Smoke'; apellidos = 'Test'; correo = $correo; telefono = '900000000'
    password = $password; confirmacionPassword = $password
  } | Out-Null
}

Paso 'POST /api/v1/auth/register duplicado (409)' {
  Invoke-Api -Method POST -Path '/api/v1/auth/register' -Esperado @(409) -Body @{
    nombres = 'Smoke'; apellidos = 'Test'; correo = $correo; telefono = '900000000'
    password = $password; confirmacionPassword = $password
  } | Out-Null
}

$tokens = $null
Paso 'POST /api/v1/auth/login (200)' {
  $script:tokens = Invoke-Api -Method POST -Path '/api/v1/auth/login' -Body @{
    correo = $correo; password = $password
  }
  if (-not $script:tokens.accessToken) { throw 'sin accessToken' }
  if (-not $script:tokens.refreshToken) { throw 'sin refreshToken' }
}

Paso 'GET /api/v1/users/me sin token (401)' {
  Invoke-Api -Method GET -Path '/api/v1/users/me' -Esperado @(401) | Out-Null
}

Paso 'GET /api/v1/users/me con token (200)' {
  $me = Invoke-Api -Method GET -Path '/api/v1/users/me' -Token $tokens.accessToken
  if ($me.correo -ne $correo) { throw "correo inesperado: $($me.correo)" }
}

Paso 'GET /api/v1/ayuda (200, categorias y preguntas)' {
  $ayuda = @(Invoke-Api -Method GET -Path '/api/v1/ayuda')
  # En Windows PowerShell 5.1 un arreglo devuelto por una funcion puede llegar anidado.
  if ($ayuda.Count -eq 1 -and $ayuda[0] -is [array]) { $ayuda = @($ayuda[0]) }
  if ($ayuda.Count -lt 5) { throw "categorias inesperadas: $($ayuda.Count)" }
  if (@($ayuda[0].articulos).Count -lt 1) { throw 'la primera categoria no trae articulos' }
}

Paso 'GET /api/v1/ayuda?texto=reembolso (200, filtra)' {
  $ayuda = @(Invoke-Api -Method GET -Path '/api/v1/ayuda?texto=reembolso')
  if ($ayuda.Count -eq 1 -and $ayuda[0] -is [array]) { $ayuda = @($ayuda[0]) }
  if ($ayuda.Count -lt 1) { throw 'la busqueda no devolvio categorias' }
  $preguntas = @($ayuda | ForEach-Object { $_.articulos } | ForEach-Object { $_.pregunta })
  if (-not ($preguntas | Where-Object { $_ -match 'reembolso' })) {
    throw 'la busqueda no devolvio la pregunta de reembolso'
  }
}

$rotado = $null
Paso 'POST /api/v1/auth/refresh (rota el refresh)' {
  $script:rotado = Invoke-Api -Method POST -Path '/api/v1/auth/refresh' -Body @{
    refreshToken = $tokens.refreshToken
  }
  if ($script:rotado.refreshToken -eq $tokens.refreshToken) { throw 'el refresh no roto' }
}

Paso 'POST /api/v1/auth/refresh reutilizado (401)' {
  Invoke-Api -Method POST -Path '/api/v1/auth/refresh' -Esperado @(401) -Body @{
    refreshToken = $tokens.refreshToken
  } | Out-Null
}

Paso 'POST /api/v1/auth/login (nuevo) y logout (204)' {
  $otra = Invoke-Api -Method POST -Path '/api/v1/auth/login' -Body @{
    correo = $correo; password = $password
  }
  Invoke-Api -Method POST -Path '/api/v1/auth/logout' -Esperado @(204) -Body @{
    refreshToken = $otra.refreshToken
  } | Out-Null
  Invoke-Api -Method POST -Path '/api/v1/auth/refresh' -Esperado @(401) -Body @{
    refreshToken = $otra.refreshToken
  } | Out-Null
}

Paso 'POST /api/v1/auth/password-reset/request (202)' {
  Invoke-Api -Method POST -Path '/api/v1/auth/password-reset/request' -Esperado @(202) -Body @{
    correo = $correo
  } | Out-Null
}

Paso 'POST password-reset correo inexistente (202, sin enumeracion)' {
  Invoke-Api -Method POST -Path '/api/v1/auth/password-reset/request' -Esperado @(202) -Body @{
    correo = "noexiste.$([DateTime]::UtcNow.Ticks)@canchadirecta.pe"
  } | Out-Null
}

# --- Establecimientos, canchas y reservas (RN-01) --------------------------
$establecimientoId = $null
$canchaId = $null

Paso 'POST /api/v1/establecimientos (201)' {
  $script:establecimientoId = (Invoke-Api -Method POST -Path '/api/v1/establecimientos' `
      -Token $tokens.accessToken -Esperado @(201) -Body @{
        documentoIdentidad = "7$([DateTime]::UtcNow.Ticks.ToString().Substring(10))"
        idDistrito         = $IdDistrito
        nombre             = "Complejo Smoke $([DateTime]::UtcNow.Ticks)"
        direccion          = 'Av. Prueba 123'
        referencia         = 'Prueba de humo'
      }).id
  if (-not $script:establecimientoId) { throw 'sin id de establecimiento' }
}

Paso 'POST /api/v1/establecimientos/{id}/canchas (201)' {
  $script:canchaId = (Invoke-Api -Method POST -Path "/api/v1/establecimientos/$script:establecimientoId/canchas" `
      -Token $tokens.accessToken -Esperado @(201) -Body @{
        nombre      = "Cancha Smoke $([DateTime]::UtcNow.Ticks)"
        descripcion = 'Futbol 7 con grass sintetico'
        capacidad   = 14
        precioBase  = 80.00
      }).id
  if (-not $script:canchaId) { throw 'sin id de cancha' }
}

Paso 'GET /api/v1/canchas incluye la cancha creada (200)' {
  $canchas = @(Invoke-Api -Method GET -Path "/api/v1/canchas?idEstablecimiento=$script:establecimientoId&limite=100")
  if (-not ($canchas | Where-Object { $_.id -eq $script:canchaId })) {
    throw "la cancha $script:canchaId no aparece en el listado"
  }
}

# --- Catalogo avanzado (F02/F03, L-10) ---------------------------------------
Paso 'GET /api/v1/ubicaciones/distritos (200)' {
  $d = Invoke-Api -Method GET -Path '/api/v1/ubicaciones/distritos?limite=20'
  if (@($d).Count -lt 1) { throw 'no hay distritos' }
  if ($d[0].provincia -ne 'Lima' -or $d[0].departamento -ne 'Lima') {
    throw "distrito inesperado: $($d[0].provincia)/$($d[0].departamento)"
  }
}

Paso 'GET /api/v1/deportes (200)' {
  $d = Invoke-Api -Method GET -Path '/api/v1/deportes'
  if (@($d).Count -lt 1) { throw 'no hay deportes' }
  $script:deporteId = $d[0].id
}

Paso 'PUT /api/v1/canchas/{id}/deportes (200)' {
  $d = Invoke-Api -Method PUT -Path "/api/v1/canchas/$script:canchaId/deportes" `
    -Token $tokens.accessToken -Esperado @(200) -Body @{ ids = @($script:deporteId) }
  if (@($d.deportes).Count -ne 1) { throw "deportes inesperados: $(@($d.deportes).Count)" }
}

Paso 'PUT /api/v1/canchas/{id}/caracteristicas (200)' {
  $ch = Invoke-Api -Method GET -Path '/api/v1/caracteristicas'
  if (@($ch).Count -lt 1) { throw 'no hay caracteristicas' }
  $script:caracteristicaId = $ch[0].id
  $c = Invoke-Api -Method PUT -Path "/api/v1/canchas/$script:canchaId/caracteristicas" `
    -Token $tokens.accessToken -Esperado @(200) -Body @{ ids = @($script:caracteristicaId) }
  if (@($c.caracteristicas).Count -ne 1) { throw "caracteristicas inesperadas: $(@($c.caracteristicas).Count)" }
}

Paso 'POST /api/v1/canchas/{id}/fotos (201)' {
  $f = Invoke-Api -Method POST -Path "/api/v1/canchas/$script:canchaId/fotos" `
    -Token $tokens.accessToken -Esperado @(201) `
    -Body @{ url = 'https://picsum.photos/seed/canchadirecta-humo/800/600'; orden = 1; principal = $true }
  if (-not $f.principal) { throw 'la foto no quedo como principal' }
}

Paso 'GET /api/v1/canchas/{id} (200, con atributos y fotos)' {
  $c = Invoke-Api -Method GET -Path "/api/v1/canchas/$script:canchaId"
  if ($c.id -ne $script:canchaId) { throw "cancha inesperada: $($c.id)" }
  if (@($c.deportes).Count -lt 1) { throw 'la ficha no trae deportes' }
  if (@($c.fotos).Count -lt 1) { throw 'la ficha no trae fotos' }
  if ($c.distrito.departamento -ne 'Lima') { throw "departamento inesperado: $($c.distrito.departamento)" }
}

Paso 'GET /api/v1/establecimientos/{id}/ficha (200, espacio con canchas y servicios)' {
  $e = Invoke-Api -Method GET -Path "/api/v1/establecimientos/$script:establecimientoId/ficha"
  if ($e.id -ne $script:establecimientoId) { throw "establecimiento inesperado: $($e.id)" }
  $ids = @($e.canchas | ForEach-Object { $_.id })
  if ($ids -notcontains $script:canchaId) { throw "la cancha $script:canchaId no aparece en la ficha" }
  if (@($e.servicios).Count -lt 1) { throw 'la ficha no trae servicios' }
  if (@($e.fotos).Count -lt 1) { throw 'la ficha no trae fotos' }
  if ($null -eq $e.precioDesde) { throw 'la ficha no trae precio desde' }
}

# --- Actividades e inscripciones (F09) ---------------------------------------
Paso 'POST /api/v1/establecimientos/{id}/actividades (201)' {
  $inicio = [DateTime]::UtcNow.AddDays(2)
  $script:actividadId = (Invoke-Api -Method POST -Path "/api/v1/establecimientos/$script:establecimientoId/actividades" `
      -Token $tokens.accessToken -Esperado @(201) -Body @{
        nombre      = "Torneo Smoke $([DateTime]::UtcNow.Ticks)"
        descripcion = 'Actividad de prueba de humo'
        inicio      = $inicio.ToString('yyyy-MM-ddTHH:mm:ssZ')
        fin         = $inicio.AddHours(2).ToString('yyyy-MM-ddTHH:mm:ssZ')
        cupos       = 2
        precio      = 15.00
      }).id
  if (-not $script:actividadId) { throw 'sin id de actividad' }
}

Paso 'GET /api/v1/actividades incluye la actividad (200)' {
  $lista = @(Invoke-Api -Method GET -Path '/api/v1/actividades?limite=100')
  if (-not ($lista | Where-Object { $_.id -eq $script:actividadId })) {
    throw "la actividad $script:actividadId no aparece en el catalogo"
  }
}

Paso 'GET /api/v1/establecimientos/{id}/actividades (200, propietario)' {
  $lista = @(Invoke-Api -Method GET -Path "/api/v1/establecimientos/$script:establecimientoId/actividades" `
      -Token $tokens.accessToken)
  if (-not ($lista | Where-Object { $_.id -eq $script:actividadId })) {
    throw 'la actividad no aparece en la gestion del propietario'
  }
}

Paso 'POST /api/v1/actividades/{id}/inscripciones (201)' {
  $i = Invoke-Api -Method POST -Path "/api/v1/actividades/$script:actividadId/inscripciones" `
    -Token $tokens.accessToken -Esperado @(201)
  if ($i.estado -ne 'CONFIRMADA') { throw "estado inesperado: $($i.estado)" }
}

Paso 'POST /api/v1/actividades/{id}/inscripciones duplicada (409)' {
  Invoke-Api -Method POST -Path "/api/v1/actividades/$script:actividadId/inscripciones" `
    -Token $tokens.accessToken -Esperado @(409) | Out-Null
}

Paso 'GET /api/v1/inscripciones/mias (200)' {
  $mias = @(Invoke-Api -Method GET -Path '/api/v1/inscripciones/mias' -Token $tokens.accessToken)
  if (-not ($mias | Where-Object { $_.idActividad -eq $script:actividadId -and $_.estado -eq 'CONFIRMADA' })) {
    throw 'la inscripcion no aparece en mis inscripciones'
  }
}

Paso 'GET /api/v1/actividades/{id}/inscripciones (200, propietario)' {
  $inscritos = @(Invoke-Api -Method GET -Path "/api/v1/actividades/$script:actividadId/inscripciones" `
      -Token $tokens.accessToken)
  if (@($inscritos).Count -ne 1) { throw "inscritos inesperados: $(@($inscritos).Count)" }
}

Paso 'DELETE /api/v1/actividades/{id}/inscripciones (204)' {
  Invoke-Api -Method DELETE -Path "/api/v1/actividades/$script:actividadId/inscripciones" `
    -Token $tokens.accessToken -Esperado @(204) | Out-Null
}

Paso 'DELETE /api/v1/actividades/{id} (204, cancela)' {
  Invoke-Api -Method DELETE -Path "/api/v1/actividades/$script:actividadId" `
    -Token $tokens.accessToken -Esperado @(204) | Out-Null
}

Paso 'GET /api/v1/establecimientos/{id}/metricas (200, panel propietario)' {
  $m = Invoke-Api -Method GET -Path "/api/v1/establecimientos/$script:establecimientoId/metricas?dias=7" `
    -Token $tokens.accessToken
  if ($m.dias -ne 7) { throw "dias inesperados: $($m.dias)" }
  if (@($m.serie).Count -ne 7) { throw "serie inesperada: $(@($m.serie).Count)" }
  if ($m.resumen.canchasActivas -lt 1) { throw 'sin canchas activas en las metricas' }
  if ($null -eq $m.resumen.ingresos) { throw 'sin ingresos en el resumen' }
}

Paso 'GET /api/v1/canchas con filtros (200)' {
  $lista = Invoke-Api -Method GET `
    -Path "/api/v1/canchas?idDeporte=$script:deporteId&precioMax=80&q=Smoke&limite=100"
  $encontrada = $false
  foreach ($c in $lista) {
    if ($c.id -eq $script:canchaId) { $encontrada = $true }
  }
  if (-not $encontrada) { throw "la cancha $script:canchaId no aparece con los filtros" }
}

# Franja alineada a la ventana por defecto (06:00-23:00 local) del endpoint de
# disponibilidad: 12:00 en America/Lima (UTC-5, sin horario de verano) = 17:00 UTC.
$fechaLima = (Get-Date).ToUniversalTime().AddHours(-5).Date.AddDays(2)
$fechaIso = $fechaLima.ToString('yyyy-MM-dd')
$inicio = $fechaLima.AddHours(12).AddHours(5)
$inicioIso = $inicio.ToString("yyyy-MM-ddTHH:mm:ss'Z'")
$finIso = $inicio.AddHours(1).ToString("yyyy-MM-ddTHH:mm:ss'Z'")
$cuerpoReserva = @{ canchaId = $script:canchaId; inicio = $inicioIso; fin = $finIso }
# Franja contigua (13:00 local = 18:00 UTC): escenario de aceptacion/rechazo del propietario.
$inicio2Iso = $inicio.AddHours(1).ToString("yyyy-MM-ddTHH:mm:ss'Z'")
$fin2Iso = $inicio.AddHours(2).ToString("yyyy-MM-ddTHH:mm:ss'Z'")
$cuerpoReserva2 = @{ canchaId = $script:canchaId; inicio = $inicio2Iso; fin = $fin2Iso }
# Dia ISO (1 = lunes ... 7 = domingo) de la fecha de prueba, en hora de Lima.
$diaSemana = [int]$fechaLima.DayOfWeek
if ($diaSemana -eq 0) { $diaSemana = 7 }

Paso 'GET /api/v1/canchas/{id}/disponibilidad (200, ofrece la franja libre)' {
  $d = Invoke-Api -Method GET -Path "/api/v1/canchas/$script:canchaId/disponibilidad?fecha=$fechaIso&duracionMinutos=60"
  if ($d.zonaHoraria -ne 'America/Lima') { throw "zona inesperada: $($d.zonaHoraria)" }
  if (-not ($d.franjas | Where-Object { $_.inicio -eq $script:inicioIso })) {
    throw "la franja $script:inicioIso no aparece en la disponibilidad"
  }
}

Paso 'POST /api/v1/reservas (201, PENDIENTE)' {
  $r = Invoke-Api -Method POST -Path '/api/v1/reservas' -Token $tokens.accessToken `
    -Esperado @(201) -Body $cuerpoReserva
  if (-not $r.id) { throw 'sin id de reserva' }
  if ($r.estado -ne 'PENDIENTE') { throw "estado inesperado: $($r.estado)" }
  if ($r.montoCancha -ne 80.00) { throw "monto inesperado: $($r.montoCancha)" }
  $script:reservaId = $r.id
}

Paso 'POST /api/v1/reservas misma franja (409 SLOT_UNAVAILABLE)' {
  $r = Invoke-Api -Method POST -Path '/api/v1/reservas' -Token $tokens.accessToken `
    -Esperado @(409) -Body $cuerpoReserva
  if ($r.code -ne 'SLOT_UNAVAILABLE') { throw "codigo inesperado: $($r.code)" }
}

# Promociones del propietario (CU04.6): franja libre 10:00 local = 15:00 UTC.
$inicioPromoIso = $fechaLima.AddHours(10).AddHours(5).ToString("yyyy-MM-ddTHH:mm:ss'Z'")
$finPromoIso = $fechaLima.AddHours(10).AddHours(6).ToString("yyyy-MM-ddTHH:mm:ss'Z'")

Paso 'POST /api/v1/establecimientos/{id}/promociones (201)' {
  $script:codigoPromocion = 'HUMO' + (Get-Date -Format 'MMddHHmmss')
  $p = Invoke-Api -Method POST -Path "/api/v1/establecimientos/$script:establecimientoId/promociones" `
    -Token $tokens.accessToken -Esperado @(201) `
    -Body @{
      codigo = $script:codigoPromocion
      descripcion = 'Promocion de humo 10%'
      tipoDescuento = 'PORCENTAJE'
      valor = 10
      fechaInicio = $fechaIso
      fechaFin = $fechaLima.AddDays(30).ToString('yyyy-MM-dd')
      limiteUsos = 1
      limitePorUsuario = 1
    }
  if ($p.estado -ne 'ACTIVA') { throw "estado inesperado: $($p.estado)" }
  if ($p.limiteUsos -ne 1) { throw "limiteUsos inesperado: $($p.limiteUsos)" }
}

Paso 'POST /api/v1/reservas con codigoPromocion (201, descuento 8.00)' {
  $r = Invoke-Api -Method POST -Path '/api/v1/reservas' -Token $tokens.accessToken `
    -Esperado @(201) `
    -Body @{ canchaId = $script:canchaId; inicio = $inicioPromoIso; fin = $finPromoIso
             codigoPromocion = $script:codigoPromocion }
  if ($r.montoCancha -ne 80.00) { throw "monto inesperado: $($r.montoCancha)" }
  if ($r.montoDescuento -ne 8.00) { throw "descuento inesperado: $($r.montoDescuento)" }
}

Paso 'POST /api/v1/reservas con cupon agotado (409)' {
  $inicioCupon = $fechaLima.AddHours(11).AddHours(5).ToString("yyyy-MM-ddTHH:mm:ss'Z'")
  $finCupon = $fechaLima.AddHours(12).AddHours(5).ToString("yyyy-MM-ddTHH:mm:ss'Z'")
  Invoke-Api -Method POST -Path '/api/v1/reservas' -Token $tokens.accessToken `
    -Esperado @(409) `
    -Body @{ canchaId = $script:canchaId; inicio = $inicioCupon; fin = $finCupon
             codigoPromocion = $script:codigoPromocion } | Out-Null
}

Paso 'GET disponibilidad excluye la franja reservada (200)' {
  $d = Invoke-Api -Method GET -Path "/api/v1/canchas/$script:canchaId/disponibilidad?fecha=$fechaIso&duracionMinutos=60"
  if ($d.franjas | Where-Object { $_.inicio -eq $script:inicioIso }) {
    throw 'la franja reservada sigue apareciendo como libre'
  }
  if (-not ($d.ocupadas | Where-Object { $_.inicio -eq $script:inicioIso -and $_.estado -eq 'RESERVADA' })) {
    throw 'la franja reservada no figura como RESERVADA en ocupadas'
  }
}

Paso 'GET /api/v1/reservas/mias (200, una reserva)' {
  $mias = @(Invoke-Api -Method GET -Path '/api/v1/reservas/mias' -Token $tokens.accessToken)
  if ($mias.Count -ne 1) { throw "se esperaba 1 reserva y hay $($mias.Count)" }
  if ($mias[0].canchaId -ne $script:canchaId) { throw 'cancha inesperada en la reserva' }
  if ($mias[0].estado -ne 'PENDIENTE') { throw "estado inesperado: $($mias[0].estado)" }
}

Paso 'GET /api/v1/reservas/recibidas (200, la solicitud PENDIENTE)' {
  $bandeja = @(Invoke-Api -Method GET -Path '/api/v1/reservas/recibidas?estado=PENDIENTE' -Token $tokens.accessToken)
  $recibida = $bandeja | Where-Object { $_.id -eq $script:reservaId }
  if (-not $recibida) { throw "la reserva $script:reservaId no aparece en la bandeja del propietario" }
  if ($recibida.estado -ne 'PENDIENTE') { throw "estado inesperado: $($recibida.estado)" }
}

Paso 'POST /api/v1/reservas/{id}/confirmar (200, CONFIRMADA)' {
  $c = Invoke-Api -Method POST -Path "/api/v1/reservas/$script:reservaId/confirmar" -Token $tokens.accessToken
  if ($c.estado -ne 'CONFIRMADA') { throw "estado inesperado: $($c.estado)" }
}

Paso 'POST /api/v1/reservas/{id}/confirmar de nuevo (409)' {
  $c = Invoke-Api -Method POST -Path "/api/v1/reservas/$script:reservaId/confirmar" `
    -Token $tokens.accessToken -Esperado @(409)
  if ($c.code -ne 'CONFLICT') { throw "codigo inesperado: $($c.code)" }
}

Paso 'POST /api/v1/reservas franja contigua (201)' {
  $r = Invoke-Api -Method POST -Path '/api/v1/reservas' -Token $tokens.accessToken `
    -Esperado @(201) -Body $cuerpoReserva2
  if ($r.estado -ne 'PENDIENTE') { throw "estado inesperado: $($r.estado)" }
  $script:reserva2Id = $r.id
}

Paso 'POST /api/v1/reservas/{id}/rechazar (200, RECHAZADA)' {
  $r = Invoke-Api -Method POST -Path "/api/v1/reservas/$script:reserva2Id/rechazar" `
    -Token $tokens.accessToken -Body @{ motivo = 'Cancha reservada para mantenimiento' }
  if ($r.estado -ne 'RECHAZADA') { throw "estado inesperado: $($r.estado)" }
}

Paso 'GET disponibilidad vuelve a ofrecer la franja rechazada (200)' {
  $d = Invoke-Api -Method GET -Path "/api/v1/canchas/$script:canchaId/disponibilidad?fecha=$fechaIso&duracionMinutos=60"
  if (-not ($d.franjas | Where-Object { $_.inicio -eq $script:inicio2Iso })) {
    throw 'la franja rechazada no volvio a la disponibilidad'
  }
}

Paso 'POST /api/v1/reservas sin token (401)' {
  Invoke-Api -Method POST -Path '/api/v1/reservas' -Esperado @(401) -Body $cuerpoReserva | Out-Null
}

Paso 'POST /api/v1/reservas/{id}/cancelar (200, CANCELADA)' {
  $c = Invoke-Api -Method POST -Path "/api/v1/reservas/$script:reservaId/cancelar" -Token $tokens.accessToken
  if ($c.estado -ne 'CANCELADA') { throw "estado inesperado: $($c.estado)" }
}

Paso 'POST /api/v1/reservas/{id}/cancelar de nuevo (409)' {
  $c = Invoke-Api -Method POST -Path "/api/v1/reservas/$script:reservaId/cancelar" `
    -Token $tokens.accessToken -Esperado @(409)
  if ($c.code -ne 'CONFLICT') { throw "codigo inesperado: $($c.code)" }
}

Paso 'GET disponibilidad vuelve a ofrecer la franja cancelada (200)' {
  $d = Invoke-Api -Method GET -Path "/api/v1/canchas/$script:canchaId/disponibilidad?fecha=$fechaIso&duracionMinutos=60"
  if (-not ($d.franjas | Where-Object { $_.inicio -eq $script:inicioIso })) {
    throw 'la franja cancelada no volvio a la disponibilidad'
  }
}

# --- Agenda del propietario (HU-005) y bloqueos operativos (RN-03) ------------
$cuerpoHorario = @{ diaSemana = $diaSemana; horaInicio = '06:00'; horaFin = '23:00'; precio = 80.00 }

Paso "POST /api/v1/canchas/{id}/horarios (201, dia $diaSemana)" {
  $h = Invoke-Api -Method POST -Path "/api/v1/canchas/$script:canchaId/horarios" `
    -Token $tokens.accessToken -Esperado @(201) -Body $cuerpoHorario
  if (-not $h.id) { throw 'sin id de horario' }
  $script:horarioId = $h.id
}

Paso 'GET /api/v1/canchas/{id}/horarios (200, publico)' {
  $horarios = @(Invoke-Api -Method GET -Path "/api/v1/canchas/$script:canchaId/horarios")
  if ($horarios.Count -ne 1) { throw "se esperaba 1 horario y hay $($horarios.Count)" }
  if ($horarios[0].horaInicio -ne '06:00:00') { throw "hora inesperada: $($horarios[0].horaInicio)" }
  if ($horarios[0].diaSemana -ne $diaSemana) { throw "dia inesperado: $($horarios[0].diaSemana)" }
}

Paso 'POST /api/v1/canchas/{id}/horarios solapado (409)' {
  $h = Invoke-Api -Method POST -Path "/api/v1/canchas/$script:canchaId/horarios" `
    -Token $tokens.accessToken -Esperado @(409) -Body @{ diaSemana = $diaSemana; horaInicio = '22:00'; horaFin = '23:30' }
  if ($h.code -ne 'CONFLICT') { throw "codigo inesperado: $($h.code)" }
}

Paso 'GET disponibilidad usa el horario configurado (200)' {
  $d = Invoke-Api -Method GET -Path "/api/v1/canchas/$script:canchaId/disponibilidad?fecha=$fechaIso&duracionMinutos=60"
  if (-not ($d.franjas | Where-Object { $_.inicio -eq $script:inicioIso })) {
    throw "la franja $script:inicioIso no aparece con el horario 06:00-23:00"
  }
}

Paso 'POST /api/v1/canchas/{id}/bloqueos (201)' {
  $b = Invoke-Api -Method POST -Path "/api/v1/canchas/$script:canchaId/bloqueos" `
    -Token $tokens.accessToken -Esperado @(201) `
    -Body @{ inicio = $inicioIso; fin = $finIso; motivo = 'Mantenimiento de grass' }
  if (-not $b.id) { throw 'sin id de bloqueo' }
  $script:bloqueoId = $b.id
}

Paso 'GET disponibilidad excluye la franja bloqueada (200)' {
  $d = Invoke-Api -Method GET -Path "/api/v1/canchas/$script:canchaId/disponibilidad?fecha=$fechaIso&duracionMinutos=60"
  if ($d.franjas | Where-Object { $_.inicio -eq $script:inicioIso }) {
    throw 'la franja bloqueada sigue apareciendo como libre'
  }
  if (-not ($d.ocupadas | Where-Object { $_.inicio -eq $script:inicioIso -and $_.estado -eq 'BLOQUEADA' })) {
    throw 'la franja bloqueada no figura como BLOQUEADA en ocupadas'
  }
}

Paso 'POST /api/v1/reservas en franja bloqueada (409)' {
  $r = Invoke-Api -Method POST -Path '/api/v1/reservas' -Token $tokens.accessToken `
    -Esperado @(409) -Body $cuerpoReserva
  if ($r.code -ne 'SLOT_UNAVAILABLE') { throw "codigo inesperado: $($r.code)" }
}

Paso 'DELETE /api/v1/canchas/{id}/bloqueos/{id} (204)' {
  Invoke-Api -Method DELETE -Path "/api/v1/canchas/$script:canchaId/bloqueos/$script:bloqueoId" `
    -Token $tokens.accessToken -Esperado @(204) | Out-Null
}

Paso 'GET disponibilidad liberada tras levantar el bloqueo (200)' {
  $d = Invoke-Api -Method GET -Path "/api/v1/canchas/$script:canchaId/disponibilidad?fecha=$fechaIso&duracionMinutos=60"
  if (-not ($d.franjas | Where-Object { $_.inicio -eq $script:inicioIso })) {
    throw 'la franja bloqueada no volvio a la disponibilidad'
  }
}

# --- Cochera opcional (RN-08) -------------------------------------------------
# Franja libre 15:00 local = 20:00 UTC (dentro del horario 06:00-23:00).
$inicioCochera = $fechaLima.AddHours(15).AddHours(5)
$inicioCocheraIso = $inicioCochera.ToString("yyyy-MM-ddTHH:mm:ss'Z'")
$finCocheraIso = $inicioCochera.AddHours(1).ToString("yyyy-MM-ddTHH:mm:ss'Z'")
$cuerpoReservaCochera = @{
  canchaId = $script:canchaId; inicio = $inicioCocheraIso; fin = $finCocheraIso; cocheraId = 0
}

Paso 'POST /api/v1/establecimientos/{id}/cocheras (201)' {
  $c = Invoke-Api -Method POST -Path "/api/v1/establecimientos/$script:establecimientoId/cocheras" `
    -Token $tokens.accessToken -Esperado @(201) `
    -Body @{ nombre = 'Cochera Smoke'; capacidad = 2; tarifaHora = 5.00 }
  if (-not $c.id) { throw 'sin id de cochera' }
  $script:cocheraId = $c.id
}

Paso 'GET /api/v1/establecimientos/{id}/cocheras (200, una cochera)' {
  $cs = @(Invoke-Api -Method GET -Path "/api/v1/establecimientos/$script:establecimientoId/cocheras" -Token $tokens.accessToken)
  $creada = $cs | Where-Object { $_.id -eq $script:cocheraId }
  if (-not $creada) { throw "la cochera $script:cocheraId no aparece en el listado" }
  if ($creada.capacidad -ne 2) { throw "capacidad inesperada: $($creada.capacidad)" }
}

Paso 'GET /api/v1/canchas/{id}/cocheras (200, cupo 2)' {
  $cupo = @(Invoke-Api -Method GET -Path "/api/v1/canchas/$script:canchaId/cocheras?inicio=$inicioCocheraIso&fin=$finCocheraIso")
  $c = $cupo | Where-Object { $_.id -eq $script:cocheraId }
  if (-not $c) { throw "la cochera $script:cocheraId no aparece en el cupo" }
  if ($c.cupoDisponible -ne 2) { throw "cupo inesperado: $($c.cupoDisponible)" }
}

Paso 'POST /api/v1/reservas con cocheraId (201, montoCochera 5.00)' {
  $cuerpoReservaCochera.cocheraId = $script:cocheraId
  $r = Invoke-Api -Method POST -Path '/api/v1/reservas' -Token $tokens.accessToken `
    -Esperado @(201) -Body $cuerpoReservaCochera
  if ($r.cocheraId -ne $script:cocheraId) { throw "cocheraId inesperado: $($r.cocheraId)" }
  if ($r.montoCochera -ne 5.00) { throw "montoCochera inesperado: $($r.montoCochera)" }
  $script:reservaCocheraId = $r.id
}

Paso 'GET del cupo de cochera baja a 1 (200)' {
  $cupo = @(Invoke-Api -Method GET -Path "/api/v1/canchas/$script:canchaId/cocheras?inicio=$inicioCocheraIso&fin=$finCocheraIso")
  $c = $cupo | Where-Object { $_.id -eq $script:cocheraId }
  if ($c.cupoDisponible -ne 1) { throw "cupo inesperado: $($c.cupoDisponible)" }
}

Paso 'POST /api/v1/reservas/{id}/cancelar libera la cochera (200)' {
  $c = Invoke-Api -Method POST -Path "/api/v1/reservas/$script:reservaCocheraId/cancelar" -Token $tokens.accessToken
  if ($c.estado -ne 'CANCELADA') { throw "estado inesperado: $($c.estado)" }
}

Paso 'GET del cupo de cochera vuelve a 2 (200)' {
  $cupo = @(Invoke-Api -Method GET -Path "/api/v1/canchas/$script:canchaId/cocheras?inicio=$inicioCocheraIso&fin=$finCocheraIso")
  $c = $cupo | Where-Object { $_.id -eq $script:cocheraId }
  if ($c.cupoDisponible -ne 2) { throw "cupo inesperado: $($c.cupoDisponible)" }
}

Paso 'DELETE /api/v1/establecimientos/{id}/cocheras/{id} (204)' {
  Invoke-Api -Method DELETE -Path "/api/v1/establecimientos/$script:establecimientoId/cocheras/$script:cocheraId" `
    -Token $tokens.accessToken -Esperado @(204) | Out-Null
}

# --- Pago simulado y reembolso (CU05.1 / CU05.3, RN-05, L-12) ------------------
# Franja libre 16:00 local = 21:00 UTC (dentro del horario 06:00-23:00).
$inicioPago = $fechaLima.AddHours(16).AddHours(5)
$inicioPagoIso = $inicioPago.ToString("yyyy-MM-ddTHH:mm:ss'Z'")
$finPagoIso = $inicioPago.AddHours(1).ToString("yyyy-MM-ddTHH:mm:ss'Z'")
$cuerpoReservaPago = @{ canchaId = $script:canchaId; inicio = $inicioPagoIso; fin = $finPagoIso }

Paso 'POST /api/v1/reservas para pagar (201)' {
  $r = Invoke-Api -Method POST -Path '/api/v1/reservas' -Token $tokens.accessToken `
    -Esperado @(201) -Body $cuerpoReservaPago
  if ($r.estado -ne 'PENDIENTE') { throw "estado inesperado: $($r.estado)" }
  $script:reservaPagoId = $r.id
}

Paso 'POST /api/v1/reservas/{id}/pago (201, APROBADO)' {
  $p = Invoke-Api -Method POST -Path "/api/v1/reservas/$script:reservaPagoId/pago" `
    -Token $tokens.accessToken -Esperado @(201) -Body @{ medioPago = 'TARJETA' }
  if ($p.estado -ne 'APROBADO') { throw "estado inesperado: $($p.estado)" }
  if ($p.monto -ne 80.00) { throw "monto inesperado: $($p.monto)" }
}

Paso 'GET /api/v1/reservas/{id}/pago (200, APROBADO sin reembolsos)' {
  $p = Invoke-Api -Method GET -Path "/api/v1/reservas/$script:reservaPagoId/pago" -Token $tokens.accessToken
  if ($p.estado -ne 'APROBADO') { throw "estado inesperado: $($p.estado)" }
  if (@($p.reembolsos).Count -ne 0) { throw "no deberia haber reembolsos todavia" }
}

Paso 'POST /api/v1/reservas/{id}/pago de nuevo (409, RN-05)' {
  $p = Invoke-Api -Method POST -Path "/api/v1/reservas/$script:reservaPagoId/pago" `
    -Token $tokens.accessToken -Esperado @(409) -Body @{ medioPago = 'TARJETA' }
  if ($p.code -ne 'CONFLICT') { throw "codigo inesperado: $($p.code)" }
}

Paso 'POST /api/v1/reservas/{id}/cancelar reembolsa el pago (200)' {
  $c = Invoke-Api -Method POST -Path "/api/v1/reservas/$script:reservaPagoId/cancelar" -Token $tokens.accessToken
  if ($c.estado -ne 'CANCELADA') { throw "estado inesperado: $($c.estado)" }
}

Paso 'GET /api/v1/reservas/{id}/pago (200, REEMBOLSADO con 1 reembolso)' {
  $p = Invoke-Api -Method GET -Path "/api/v1/reservas/$script:reservaPagoId/pago" -Token $tokens.accessToken
  if ($p.estado -ne 'REEMBOLSADO') { throw "estado inesperado: $($p.estado)" }
  $reembolsos = @($p.reembolsos)
  if ($reembolsos.Count -ne 1) { throw "se esperaba 1 reembolso y hay $($reembolsos.Count)" }
  if ($reembolsos[0].estado -ne 'PROCESADO') { throw "reembolso inesperado: $($reembolsos[0].estado)" }
}

# --- Liquidaciones / comisiones (CU04.8 / CU05.2 / CU05.4, RN-07) ---------------
# Franja libre 17:00 local = 22:00 UTC (dentro del horario 06:00-23:00).
$inicioLiquidacion = $fechaLima.AddHours(17).AddHours(5)
$inicioLiquidacionIso = $inicioLiquidacion.ToString("yyyy-MM-ddTHH:mm:ss'Z'")
$finLiquidacionIso = $inicioLiquidacion.AddHours(1).ToString("yyyy-MM-ddTHH:mm:ss'Z'")
$cuerpoReservaLiquidacion = @{
  canchaId = $script:canchaId; inicio = $inicioLiquidacionIso; fin = $finLiquidacionIso
}
# Periodo que cubre la fecha de la reserva (comision por defecto 10 %).
$periodoInicio = $fechaLima.ToString('yyyy-MM-dd')
$periodoFin = $fechaLima.AddDays(1).ToString('yyyy-MM-dd')

Paso 'POST /api/v1/reservas para liquidar (201)' {
  $r = Invoke-Api -Method POST -Path '/api/v1/reservas' -Token $tokens.accessToken `
    -Esperado @(201) -Body $cuerpoReservaLiquidacion
  if ($r.estado -ne 'PENDIENTE') { throw "estado inesperado: $($r.estado)" }
  $script:reservaLiquidacionId = $r.id
}

Paso 'POST /api/v1/reservas/{id}/pago para liquidar (201, APROBADO)' {
  $p = Invoke-Api -Method POST -Path "/api/v1/reservas/$script:reservaLiquidacionId/pago" `
    -Token $tokens.accessToken -Esperado @(201) -Body @{ medioPago = 'TARJETA' }
  if ($p.estado -ne 'APROBADO') { throw "estado inesperado: $($p.estado)" }
}

Paso 'POST /api/v1/reservas/{id}/confirmar para liquidar (200, CONFIRMADA)' {
  $c = Invoke-Api -Method POST -Path "/api/v1/reservas/$script:reservaLiquidacionId/confirmar" -Token $tokens.accessToken
  if ($c.estado -ne 'CONFIRMADA') { throw "estado inesperado: $($c.estado)" }
}

Paso 'POST /api/v1/liquidaciones (201, comision 10%)' {
  $l = Invoke-Api -Method POST -Path '/api/v1/liquidaciones' -Token $tokens.accessToken -Esperado @(201) `
    -Body @{ periodoInicio = $periodoInicio; periodoFin = $periodoFin }
  if ($l.montoBruto -ne 80.00) { throw "bruto inesperado: $($l.montoBruto)" }
  if ($l.montoComision -ne 8.00) { throw "comision inesperada: $($l.montoComision)" }
  if ($l.montoNeto -ne 72.00) { throw "neto inesperado: $($l.montoNeto)" }
  if (@($l.detalles).Count -ne 1) { throw "se esperaba 1 detalle y hay $(@($l.detalles).Count)" }
  $script:liquidacionId = $l.id
}

Paso 'POST /api/v1/liquidaciones de nuevo (409, RN-07)' {
  $l = Invoke-Api -Method POST -Path '/api/v1/liquidaciones' -Token $tokens.accessToken -Esperado @(409) `
    -Body @{ periodoInicio = $periodoInicio; periodoFin = $periodoFin }
  if ($l.code -ne 'CONFLICT') { throw "codigo inesperado: $($l.code)" }
}

Paso 'GET /api/v1/liquidaciones/{id} (200, un detalle)' {
  $l = Invoke-Api -Method GET -Path "/api/v1/liquidaciones/$script:liquidacionId" -Token $tokens.accessToken
  if ($l.estado -ne 'PENDIENTE') { throw "estado inesperado: $($l.estado)" }
  $detalles = @($l.detalles)
  if ($detalles.Count -ne 1) { throw "se esperaba 1 detalle y hay $($detalles.Count)" }
  if ($detalles[0].reservaId -ne $script:reservaLiquidacionId) { throw "reserva inesperada: $($detalles[0].reservaId)" }
}

# --- Revocacion de una reserva confirmada (L-14) -------------------------------
# Franja libre 11:00 local = 16:00 UTC (dentro del horario 06:00-23:00).
$inicioRevocacion = $fechaLima.AddHours(11).AddHours(5)
$inicioRevocacionIso = $inicioRevocacion.ToString("yyyy-MM-ddTHH:mm:ss'Z'")
$finRevocacionIso = $inicioRevocacion.AddHours(1).ToString("yyyy-MM-ddTHH:mm:ss'Z'")
$cuerpoReservaRevocacion = @{
  canchaId = $script:canchaId; inicio = $inicioRevocacionIso; fin = $finRevocacionIso
}

Paso 'POST /api/v1/reservas para revocar (201)' {
  $r = Invoke-Api -Method POST -Path '/api/v1/reservas' -Token $tokens.accessToken `
    -Esperado @(201) -Body $cuerpoReservaRevocacion
  if ($r.estado -ne 'PENDIENTE') { throw "estado inesperado: $($r.estado)" }
  $script:reservaRevocacionId = $r.id
}

Paso 'POST /api/v1/reservas/{id}/confirmar para revocar (200, CONFIRMADA)' {
  $c = Invoke-Api -Method POST -Path "/api/v1/reservas/$script:reservaRevocacionId/confirmar" -Token $tokens.accessToken
  if ($c.estado -ne 'CONFIRMADA') { throw "estado inesperado: $($c.estado)" }
}

Paso 'POST /api/v1/reservas/{id}/revocar (200, RECHAZADA)' {
  $v = Invoke-Api -Method POST -Path "/api/v1/reservas/$script:reservaRevocacionId/revocar" `
    -Token $tokens.accessToken -Body @{ motivo = 'Mantenimiento imprevisto' }
  if ($v.estado -ne 'RECHAZADA') { throw "estado inesperado: $($v.estado)" }
}

Paso 'POST /api/v1/reservas/{id}/revocar de nuevo (409)' {
  $v = Invoke-Api -Method POST -Path "/api/v1/reservas/$script:reservaRevocacionId/revocar" `
    -Token $tokens.accessToken -Esperado @(409) -Body @{}
  if ($v.code -ne 'CONFLICT') { throw "codigo inesperado: $($v.code)" }
}

# --- Calificaciones de reservas finalizadas (F06, CU03.8/CU02.5, RN-04) --------
# Franja libre 14:00 local = 19:00 UTC (dentro del horario 06:00-23:00 y sin uso previo).
$inicioCalificacion = $fechaLima.AddHours(14).AddHours(5)
$inicioCalificacionIso = $inicioCalificacion.ToString("yyyy-MM-ddTHH:mm:ss'Z'")
$finCalificacionIso = $inicioCalificacion.AddHours(1).ToString("yyyy-MM-ddTHH:mm:ss'Z'")
$cuerpoReservaCalificacion = @{
  canchaId = $script:canchaId; inicio = $inicioCalificacionIso; fin = $finCalificacionIso
}

if (-not $script:dbDisponible) {
  Write-Host '[SKIP] Calificaciones: falta el cliente mysql o DB_USER/DB_PASSWORD en el entorno/.env' `
    -ForegroundColor Yellow
} else {
  Paso 'POST /api/v1/reservas para calificar (201)' {
    $r = Invoke-Api -Method POST -Path '/api/v1/reservas' -Token $tokens.accessToken `
      -Esperado @(201) -Body $cuerpoReservaCalificacion
    if ($r.estado -ne 'PENDIENTE') { throw "estado inesperado: $($r.estado)" }
    $script:reservaCalificacionId = $r.id
  }

  Paso 'POST /api/v1/reservas/{id}/confirmar para calificar (200, CONFIRMADA)' {
    $c = Invoke-Api -Method POST -Path "/api/v1/reservas/$script:reservaCalificacionId/confirmar" `
      -Token $tokens.accessToken
    if ($c.estado -ne 'CONFIRMADA') { throw "estado inesperado: $($c.estado)" }
  }

  Paso 'Job de cierre deja la reserva FINALIZADA (L-15)' {
    # La API no permite reservar en el pasado: se retrasa la franja por SQL (inicio/fin son
    # literales UTC, ADR-0014) y se espera al job de 60 s con sondeo acotado.
    Invoke-Sql ("UPDATE reserva SET inicio = UTC_TIMESTAMP(6) - INTERVAL 2 HOUR, " +
      "fin = UTC_TIMESTAMP(6) - INTERVAL 1 HOUR WHERE id_reserva = $script:reservaCalificacionId") | Out-Null
    $limite = (Get-Date).AddSeconds(90)
    $estado = $null
    while ((Get-Date) -lt $limite) {
      $estado = (Invoke-Sql "SELECT estado FROM reserva WHERE id_reserva = $script:reservaCalificacionId" |
        Select-Object -First 1)
      if ($estado -eq 'FINALIZADA') { break }
      Start-Sleep -Seconds 5
    }
    if ($estado -ne 'FINALIZADA') { throw "la reserva sigue en '$estado' tras 90 s" }
  }

  Paso 'POST /api/v1/reservas/{id}/calificacion (201, puntaje 5)' {
    $c = Invoke-Api -Method POST -Path "/api/v1/reservas/$script:reservaCalificacionId/calificacion" `
      -Token $tokens.accessToken -Esperado @(201) `
      -Body @{ puntaje = 5; comentario = 'Cancha en buen estado' }
    if ($c.puntaje -ne 5) { throw "puntaje inesperado: $($c.puntaje)" }
    if ($null -eq $c.fechaRegistro) { throw 'falta la fecha de registro' }
  }

  Paso 'POST /api/v1/reservas/{id}/calificacion de nuevo (409, RN-04)' {
    $c = Invoke-Api -Method POST -Path "/api/v1/reservas/$script:reservaCalificacionId/calificacion" `
      -Token $tokens.accessToken -Esperado @(409) -Body @{ puntaje = 4 }
    if ($c.code -ne 'CONFLICT') { throw "codigo inesperado: $($c.code)" }
  }

  Paso 'GET /api/v1/canchas/{id}/calificaciones (200, total 1 y promedio 5)' {
    $c = Invoke-Api -Method GET -Path "/api/v1/canchas/$script:canchaId/calificaciones"
    if ($c.total -ne 1) { throw "total inesperado: $($c.total)" }
    if ($c.promedio -ne 5) { throw "promedio inesperado: $($c.promedio)" }
    $lista = @($c.calificaciones)
    if ($lista.Count -ne 1) { throw "se esperaba 1 calificacion y hay $($lista.Count)" }
    if ($lista[0].reservaId -ne $script:reservaCalificacionId) {
      throw "reserva inesperada: $($lista[0].reservaId)"
    }
  }
}

# --- Soporte del usuario: reclamos y avisos in-app (CU06.6, F06) --------------
Paso 'POST /api/v1/reclamos (201, ABIERTO)' {
  $r = Invoke-Api -Method POST -Path '/api/v1/reclamos' -Token $tokens.accessToken -Esperado @(201) `
    -Body @{ asunto = 'Consulta de humo'; descripcion = 'Reclamo generado por el humo automatico.' }
  if ($r.estado -ne 'ABIERTO') { throw "estado inesperado: $($r.estado)" }
  $script:reclamoId = $r.id
}

Paso 'GET /api/v1/reclamos/mios (200, incluye el reclamo)' {
  $lista = Invoke-Api -Method GET -Path '/api/v1/reclamos/mios' -Token $tokens.accessToken
  $encontrado = $false
  foreach ($item in $lista) {
    if ($item.id -eq $script:reclamoId) { $encontrado = $true }
  }
  if (-not $encontrado) { throw "el reclamo $script:reclamoId no aparece en la bandeja" }
}

Paso 'POST /api/v1/reclamos/{id}/seguimientos (201)' {
  $s = Invoke-Api -Method POST -Path "/api/v1/reclamos/$script:reclamoId/seguimientos" `
    -Token $tokens.accessToken -Esperado @(201) -Body @{ comentario = 'Adjunto el comprobante.' }
  if ($s.comentario -ne 'Adjunto el comprobante.') { throw "comentario inesperado: $($s.comentario)" }
}

Paso 'GET /api/v1/reclamos/{id} (200, con seguimiento)' {
  $d = Invoke-Api -Method GET -Path "/api/v1/reclamos/$script:reclamoId" -Token $tokens.accessToken
  if ($d.id -ne $script:reclamoId) { throw "reclamo inesperado: $($d.id)" }
  if (@($d.seguimientos).Count -ne 1) { throw "seguimientos inesperados: $(@($d.seguimientos).Count)" }
}

Paso 'GET /api/v1/notificaciones (200, con avisos sin leer)' {
  $n = Invoke-Api -Method GET -Path '/api/v1/notificaciones' -Token $tokens.accessToken
  if ($n.noLeidas -lt 1) { throw "sin avisos pendientes: $($n.noLeidas)" }
  $primera = @($n.notificaciones)[0]
  if (-not $primera) { throw 'la bandeja esta vacia' }
  $script:notificacionId = $primera.id
  $script:noLeidas = $n.noLeidas
}

Paso 'POST /api/v1/notificaciones/{id}/leida (204)' {
  Invoke-Api -Method POST -Path "/api/v1/notificaciones/$script:notificacionId/leida" `
    -Token $tokens.accessToken -Esperado @(204) -Body @{} | Out-Null
  $n = Invoke-Api -Method GET -Path '/api/v1/notificaciones' -Token $tokens.accessToken
  if ($n.noLeidas -ne ($script:noLeidas - 1)) {
    throw "noLeidas inesperado: $($n.noLeidas) (antes $script:noLeidas)"
  }
}

# --- Moderacion de reclamos (PLATFORM_ADMIN, F07 sobre CU06.6) -----------------
# El rol viaja en el JWT: se promueve al usuario de humo en la base y se vuelve a iniciar
# sesion para obtener un token con `groups=PLATFORM_ADMIN`.
if (-not $script:dbDisponible) {
  Write-Host '[SKIP] Moderacion: requiere el cliente mysql y credenciales de .env' -ForegroundColor Yellow
} else {
  $sqlRol = "INSERT IGNORE INTO usuario_rol (id_usuario, id_rol) " +
    "SELECT u.id_usuario, r.id_rol FROM usuario u JOIN rol r ON r.nombre = 'PLATFORM_ADMIN' " +
    "WHERE u.correo = '$correo'"
  Invoke-Sql $sqlRol | Out-Null
  $admin = Invoke-Api -Method POST -Path '/api/v1/auth/login' `
    -Body @{ correo = $correo; password = $password }
  $script:adminToken = $admin.accessToken

  Paso 'GET /api/v1/admin/reportes (200, PLATFORM_ADMIN)' {
    $rep = Invoke-Api -Method GET -Path '/api/v1/admin/reportes?dias=7' -Token $script:adminToken
    if ($rep.dias -ne 7) { throw "dias inesperados: $($rep.dias)" }
    if (@($rep.serie).Count -ne 7) { throw "serie inesperada: $(@($rep.serie).Count)" }
    if ($rep.resumen.usuarios -lt 1) { throw 'sin usuarios en el reporte' }
  }

  Paso 'GET /api/v1/admin/configuracion (200)' {
    $script:cfgOriginal = Invoke-Api -Method GET -Path '/api/v1/admin/configuracion' `
      -Token $script:adminToken
    if ($script:cfgOriginal.diasAnticipacionMax -lt 1) { throw 'anticipacion invalida' }
    if ([string]::IsNullOrWhiteSpace($script:cfgOriginal.correoSoporte)) { throw 'sin correo de soporte' }
  }

  Paso 'PUT /api/v1/admin/configuracion (200, actualiza)' {
    $cfg = Invoke-Api -Method PUT -Path '/api/v1/admin/configuracion' `
      -Token $script:adminToken -Esperado @(200) `
      -Body @{ comisionPorcentaje = 12.50; horasCancelacionGratis = 48; horasMinimasCancelacion = 3;
               diasAnticipacionMax = 120; correoSoporte = 'ayuda@canchadirecta.pe' }
    if ($cfg.diasAnticipacionMax -ne 120) { throw "anticipacion inesperada: $($cfg.diasAnticipacionMax)" }
    if ([decimal]$cfg.comisionPorcentaje -ne 12.5) { throw "comision inesperada: $($cfg.comisionPorcentaje)" }
  }

  Paso 'GET /api/v1/admin/configuracion (200, refleja el cambio)' {
    $cfg = Invoke-Api -Method GET -Path '/api/v1/admin/configuracion' -Token $script:adminToken
    if ($cfg.horasCancelacionGratis -ne 48) { throw "horas inesperadas: $($cfg.horasCancelacionGratis)" }
    if ($cfg.correoSoporte -ne 'ayuda@canchadirecta.pe') { throw "correo inesperado: $($cfg.correoSoporte)" }
  }

  Paso 'PUT /api/v1/admin/configuracion (400, comision invalida)' {
    Invoke-Api -Method PUT -Path '/api/v1/admin/configuracion' `
      -Token $script:adminToken -Esperado @(400) `
      -Body @{ comisionPorcentaje = 101; horasCancelacionGratis = 24; horasMinimasCancelacion = 2;
               diasAnticipacionMax = 365; correoSoporte = 'soporte@canchadirecta.pe' } | Out-Null
  }

  Paso 'PUT /api/v1/admin/configuracion (200, restaura)' {
    $cfg = Invoke-Api -Method PUT -Path '/api/v1/admin/configuracion' `
      -Token $script:adminToken -Esperado @(200) `
      -Body @{ comisionPorcentaje = $script:cfgOriginal.comisionPorcentaje;
               horasCancelacionGratis = $script:cfgOriginal.horasCancelacionGratis;
               horasMinimasCancelacion = $script:cfgOriginal.horasMinimasCancelacion;
               diasAnticipacionMax = $script:cfgOriginal.diasAnticipacionMax;
               correoSoporte = $script:cfgOriginal.correoSoporte }
    if ($cfg.diasAnticipacionMax -ne $script:cfgOriginal.diasAnticipacionMax) {
      throw 'no se restauro la anticipacion'
    }
  }

  Paso 'GET /api/v1/admin/actividades (200, incluye actividades)' {
    $acts = @(Invoke-Api -Method GET -Path '/api/v1/admin/actividades?limite=200' -Token $script:adminToken)
    if (@($acts).Count -lt 1) { throw 'sin actividades para administracion' }
  }

  Paso 'POST /api/v1/admin/establecimientos/{id}/estado (200, activa/desactiva)' {
    Invoke-Api -Method POST -Path "/api/v1/admin/establecimientos/$script:establecimientoId/estado" `
      -Token $script:adminToken -Esperado @(200) -Body @{ estado = 'INACTIVO' } | Out-Null
    $act = Invoke-Api -Method POST -Path "/api/v1/admin/establecimientos/$script:establecimientoId/estado" `
      -Token $script:adminToken -Esperado @(200) -Body @{ estado = 'ACTIVO' }
    if ($act.estado -ne 'ACTIVO') { throw "estado inesperado: $($act.estado)" }
  }

  Paso 'GET /api/v1/admin/reclamos (200, PLATFORM_ADMIN)' {
    $lista = Invoke-Api -Method GET -Path '/api/v1/admin/reclamos?estado=ABIERTO&limite=200' `
      -Token $script:adminToken
    $encontrado = $false
    foreach ($item in $lista) {
      if ($item.id -eq $script:reclamoId) { $encontrado = $true }
    }
    if (-not $encontrado) { throw "el reclamo $script:reclamoId no aparece" }
  }

  Paso 'GET /api/v1/admin/reclamos con token USER (403)' {
    Invoke-Api -Method GET -Path '/api/v1/admin/reclamos' `
      -Token $tokens.accessToken -Esperado @(403) | Out-Null
  }

  Paso 'POST /api/v1/admin/reclamos/{id}/estado (200, EN_REVISION)' {
    $r = Invoke-Api -Method POST -Path "/api/v1/admin/reclamos/$script:reclamoId/estado" `
      -Token $script:adminToken -Esperado @(200) `
      -Body @{ estado = 'EN_REVISION'; comentario = 'Estamos revisando el caso.' }
    if ($r.estado -ne 'EN_REVISION') { throw "estado inesperado: $($r.estado)" }
  }

  Paso 'POST /api/v1/admin/reclamos/{id}/seguimientos (201)' {
    $s = Invoke-Api -Method POST -Path "/api/v1/admin/reclamos/$script:reclamoId/seguimientos" `
      -Token $script:adminToken -Esperado @(201) -Body @{ comentario = 'Caso resuelto.' }
    if ($s.comentario -ne 'Caso resuelto.') { throw "comentario inesperado: $($s.comentario)" }
  }

  Paso 'GET /api/v1/admin/reclamos/{id} (200, 3 seguimientos)' {
    $d = Invoke-Api -Method GET -Path "/api/v1/admin/reclamos/$script:reclamoId" `
      -Token $script:adminToken
    if (@($d.seguimientos).Count -ne 3) {
      throw "seguimientos inesperados: $(@($d.seguimientos).Count)"
    }
  }

  Paso 'GET /api/v1/notificaciones tras moderar (aviso al titular)' {
    $n = Invoke-Api -Method GET -Path '/api/v1/notificaciones' -Token $tokens.accessToken
    $titulos = @($n.notificaciones) | ForEach-Object { $_.titulo }
    if ($titulos -notcontains 'Respuesta a tu reclamo') {
      throw "no se encontro el aviso de respuesta: $($titulos -join ', ')"
    }
    if ($titulos -notcontains 'Reclamo actualizado') {
      throw "no se encontro el aviso de cambio de estado: $($titulos -join ', ')"
    }
  }

  Paso 'POST /api/v1/admin/propietarios/{id}/validacion (200)' {
    $props = Invoke-Api -Method GET -Path '/api/v1/admin/propietarios?limite=200' -Token $script:adminToken
    foreach ($p in $props) {
      if ($p.correo -eq $correo) { $script:propietarioId = $p.id }
    }
    if (-not $script:propietarioId) { throw "no se encontro el propietario de $correo" }
    $v = Invoke-Api -Method POST -Path "/api/v1/admin/propietarios/$script:propietarioId/validacion" `
      -Token $script:adminToken -Esperado @(200) -Body @{ estado = 'VALIDADO' }
    if ($v.estadoValidacion -ne 'VALIDADO') { throw "estado inesperado: $($v.estadoValidacion)" }
  }

  Paso 'GET /api/v1/notificaciones (propietario validado)' {
    $n = Invoke-Api -Method GET -Path '/api/v1/notificaciones' -Token $tokens.accessToken
    if (@($n.notificaciones)[0].titulo -ne 'Propietario validado') {
      throw "aviso inesperado: $(@($n.notificaciones)[0].titulo)"
    }
  }

  Paso 'GET /api/v1/admin/establecimientos (200)' {
    $lista = Invoke-Api -Method GET -Path '/api/v1/admin/establecimientos?limite=200' `
      -Token $script:adminToken
    $encontrado = $false
    foreach ($e in $lista) {
      if ($e.id -eq $script:establecimientoId) { $encontrado = $true }
    }
    if (-not $encontrado) { throw "el establecimiento $script:establecimientoId no aparece" }
  }

  Paso 'GET /api/v1/admin/reservas (200)' {
    $lista = Invoke-Api -Method GET -Path '/api/v1/admin/reservas?limite=200' `
      -Token $script:adminToken
    $encontrado = $false
    foreach ($r in $lista) {
      if ($r.id -eq $script:reservaCalificacionId) { $encontrado = $true }
    }
    if (-not $encontrado) { throw "la reserva $script:reservaCalificacionId no aparece" }
  }

  Paso 'GET /api/v1/admin/pagos (200)' {
    $lista = Invoke-Api -Method GET -Path '/api/v1/admin/pagos?limite=200' `
      -Token $script:adminToken
    $encontrado = $false
    foreach ($p in $lista) {
      if ($p.idReserva -eq $script:reservaPagoId) { $encontrado = $true }
    }
    if (-not $encontrado) { throw "el pago de la reserva $script:reservaPagoId no aparece" }
  }

  Paso 'GET /api/v1/admin/auditoria (200, con la validacion)' {
    $lista = Invoke-Api -Method GET -Path '/api/v1/admin/auditoria?limite=20' `
      -Token $script:adminToken
    $encontrada = $false
    foreach ($a in $lista) {
      if ($a.accion -eq 'PROPIETARIO_VALIDADO' -and $a.idEntidad -eq "$script:propietarioId") {
        $encontrada = $true
      }
    }
    if (-not $encontrada) { throw 'no se encontro la auditoria de la validacion' }
  }

  Paso 'GET /api/v1/admin/usuarios (200, roles del usuario)' {
    $lista = Invoke-Api -Method GET -Path "/api/v1/admin/usuarios?correo=$correo" `
      -Token $script:adminToken
    $objetivo = $null
    foreach ($u in $lista) {
      if ($u.correo -eq $correo) { $objetivo = $u }
    }
    if (-not $objetivo) { throw "el usuario $correo no aparece" }
    if ($objetivo.roles -notcontains 'PLATFORM_ADMIN') {
      throw "roles inesperados: $($objetivo.roles -join ', ')"
    }
    $script:usuarioId = $objetivo.id
  }

  Paso 'POST /api/v1/admin/usuarios/{id}/roles (200)' {
    $r = Invoke-Api -Method POST -Path "/api/v1/admin/usuarios/$script:usuarioId/roles" `
      -Token $script:adminToken -Esperado @(200) `
      -Body @{ roles = @('USER', 'OWNER', 'PLATFORM_ADMIN') }
    if ($r.roles -notcontains 'OWNER') { throw "roles inesperados: $($r.roles -join ', ')" }
    # L-20: el cambio de roles revoca el token vigente; se renueva para los pasos siguientes
    $admin = Invoke-Api -Method POST -Path '/api/v1/auth/login' -Esperado @(200) `
      -Body @{ correo = $correo; password = $password }
    $script:adminToken = $admin.accessToken
    if (-not $script:adminToken) { throw 'sin token de administracion tras el cambio de roles' }
    $usuario = Invoke-Api -Method POST -Path '/api/v1/auth/login' -Esperado @(200) `
      -Body @{ correo = $correo; password = $password }
    $tokens.accessToken = $usuario.accessToken
    $tokens.refreshToken = $usuario.refreshToken
    if (-not $tokens.accessToken) { throw 'sin token de usuario tras el cambio de roles' }
  }

  # --- Recepcion (Bloque D, ADR-0038) ------------------------------------------
  # El personal opera solo las sedes que la administracion le asigna; el rol viaja en el JWT.
  $correoRecep = "recepcion.$([DateTime]::UtcNow.Ticks)@canchadirecta.pe"

  Paso 'POST /api/v1/auth/register operador de recepcion (201)' {
    Invoke-Api -Method POST -Path '/api/v1/auth/register' -Esperado @(201) -Body @{
      nombres = 'Operador'; apellidos = 'Recepcion'; correo = $correoRecep; telefono = '955444333'
      password = $password; confirmacionPassword = $password
    } | Out-Null
    $script:idRecep = [int](Invoke-Sql "SELECT id_usuario FROM usuario WHERE correo='$correoRecep'" |
      Select-Object -First 1)
    if (-not $script:idRecep) { throw 'sin id de operador de recepcion' }
  }

  Paso 'GET /api/v1/recepcion/establecimientos sin rol OPERADOR (403)' {
    $tokenRecep = (Invoke-Api -Method POST -Path '/api/v1/auth/login' -Esperado @(200) `
      -Body @{ correo = $correoRecep; password = $password }).accessToken
    Invoke-Api -Method GET -Path '/api/v1/recepcion/establecimientos' `
      -Token $tokenRecep -Esperado @(403) | Out-Null
  }

  Paso 'POST /api/v1/admin/establecimientos/{id}/operadores (201)' {
    $ops = Invoke-Api -Method POST -Path "/api/v1/admin/establecimientos/$script:establecimientoId/operadores" `
      -Token $script:adminToken -Esperado @(201) `
      -Body @{ idUsuario = $script:idRecep; cargo = 'Recepcion' }
    if (@($ops).Count -ne 1) { throw "operadores inesperados: $(@($ops).Count)" }
    if ($ops[0].correo -ne $correoRecep) { throw "correo inesperado: $($ops[0].correo)" }
  }

  Paso 'POST /api/v1/auth/login operador (200, con rol OPERADOR)' {
    $script:recepToken = (Invoke-Api -Method POST -Path '/api/v1/auth/login' -Esperado @(200) `
      -Body @{ correo = $correoRecep; password = $password }).accessToken
    if (-not $script:recepToken) { throw 'sin token de recepcion' }
  }

  Paso 'GET /api/v1/recepcion/establecimientos (200, la sede asignada)' {
    $sedes = @(Invoke-Api -Method GET -Path '/api/v1/recepcion/establecimientos' -Token $script:recepToken)
    $encontrada = $false
    foreach ($s in $sedes) { if ($s.id -eq $script:establecimientoId) { $encontrada = $true } }
    if (-not $encontrada) { throw 'la sede asignada no aparece en recepcion' }
  }

  Paso 'GET /api/v1/recepcion/canchas (200)' {
    $canchas = @(Invoke-Api -Method GET `
      -Path "/api/v1/recepcion/canchas?idEstablecimiento=$script:establecimientoId" -Token $script:recepToken)
    if (@($canchas).Count -lt 1) { throw 'sin canchas de recepcion' }
  }

  Paso 'GET /api/v1/recepcion/resumen (200)' {
    $r = Invoke-Api -Method GET -Path '/api/v1/recepcion/resumen' -Token $script:recepToken
    if ($r.canchasDisponibles -lt 1) { throw "canchas inesperadas: $($r.canchasDisponibles)" }
  }

  Paso 'GET /api/v1/recepcion/clientes (200, busca al cliente registrado)' {
    $clientes = @(Invoke-Api -Method GET -Path '/api/v1/recepcion/clientes?q=Smoke' -Token $script:recepToken)
    $encontrado = $false
    foreach ($c in $clientes) { if ($c.id -eq $script:usuarioId) { $encontrado = $true } }
    if (-not $encontrado) { throw 'el cliente de humo no aparece en la busqueda' }
  }

  # Franja propia del dia de hoy (hora de Lima, UTC-5): libre de las reservas del humo.
  $ahoraLima = (Get-Date).ToUniversalTime().AddHours(-5)
  $inicioRecep = $ahoraLima.AddMinutes(40)
  $fechaRecep = $inicioRecep.ToString('yyyy-MM-dd')
  $horaRecep = $inicioRecep.ToString('HH:mm')

  Paso 'POST /api/v1/recepcion/reservas (201, walk-in con pago aprobado)' {
    $r = Invoke-Api -Method POST -Path '/api/v1/recepcion/reservas' -Token $script:recepToken `
      -Esperado @(201) -Body @{
        idEstablecimiento = $script:establecimientoId; idCancha = $script:canchaId
        idCliente = $script:usuarioId; fecha = $fechaRecep; horaInicio = $horaRecep
        duracionMinutos = 60; pagoRecibido = $true
      }
    if ($r.estado -ne 'PENDIENTE') { throw "estado inesperado: $($r.estado)" }
    if ($r.pagoEstado -ne 'APROBADO') { throw "pago inesperado: $($r.pagoEstado)" }
    $script:reservaRecepId = $r.id
  }

  Paso 'GET /api/v1/recepcion/agenda (200, incluye el walk-in)' {
    $agenda = @(Invoke-Api -Method GET `
      -Path "/api/v1/recepcion/agenda?fecha=$fechaRecep&idEstablecimiento=$script:establecimientoId" `
      -Token $script:recepToken)
    $encontrada = $false
    foreach ($a in $agenda) { if ($a.id -eq $script:reservaRecepId) { $encontrada = $true } }
    if (-not $encontrada) { throw 'el walk-in no aparece en la agenda' }
  }

  Paso 'POST /api/v1/reservas/{id}/confirmar (200, el dueno confirma el walk-in)' {
    Invoke-Api -Method POST -Path "/api/v1/reservas/$script:reservaRecepId/confirmar" `
      -Token $tokens.accessToken -Esperado @(200) | Out-Null
  }

  Paso 'POST /api/v1/recepcion/reservas/{id}/checkin (200, FINALIZADA)' {
    $r = Invoke-Api -Method POST -Path "/api/v1/recepcion/reservas/$script:reservaRecepId/checkin" `
      -Token $script:recepToken -Esperado @(200)
    if ($r.estado -ne 'FINALIZADA') { throw "estado inesperado: $($r.estado)" }
  }

  Paso 'POST /api/v1/recepcion/reservas/{id}/checkin de nuevo (409)' {
    Invoke-Api -Method POST -Path "/api/v1/recepcion/reservas/$script:reservaRecepId/checkin" `
      -Token $script:recepToken -Esperado @(409) | Out-Null
  }

  Paso 'POST /api/v1/recepcion/reservas/{id}/cobro (200, YAPE)' {
    $inicioCobro = $ahoraLima.AddHours(3)
    $pendiente = Invoke-Api -Method POST -Path '/api/v1/recepcion/reservas' -Token $script:recepToken `
      -Esperado @(201) -Body @{
        idEstablecimiento = $script:establecimientoId; idCancha = $script:canchaId
        idCliente = $script:usuarioId; fecha = $inicioCobro.ToString('yyyy-MM-dd')
        horaInicio = $inicioCobro.ToString('HH:mm'); duracionMinutos = 60; pagoRecibido = $false
      }
    $pago = Invoke-Api -Method POST -Path "/api/v1/recepcion/reservas/$($pendiente.id)/cobro" `
      -Token $script:recepToken -Esperado @(200) -Body @{ medioPago = 'YAPE' }
    if ($pago.pagoEstado -ne 'APROBADO') { throw "pago inesperado: $($pago.pagoEstado)" }
    Invoke-Api -Method POST -Path "/api/v1/recepcion/reservas/$($pendiente.id)/cobro" `
      -Token $script:recepToken -Esperado @(409) -Body @{ medioPago = 'EFECTIVO' } | Out-Null
  }

  Paso 'POST /api/v1/recepcion/incidencias (201) y GET (200)' {
    $i = Invoke-Api -Method POST -Path '/api/v1/recepcion/incidencias' -Token $script:recepToken `
      -Esperado @(201) -Body @{ asunto = 'Cancha mojada'; descripcion = 'Se solicito mantenimiento.';
        idReserva = $script:reservaRecepId }
    if ($i.estado -ne 'ABIERTO') { throw "estado inesperado: $($i.estado)" }
    $incidencias = @(Invoke-Api -Method GET -Path '/api/v1/recepcion/incidencias' -Token $script:recepToken)
    if (@($incidencias).Count -lt 1) { throw 'sin incidencias registradas' }
  }

  Paso 'GET /api/v1/recepcion/establecimientos sin token (401)' {
    Invoke-Api -Method GET -Path '/api/v1/recepcion/establecimientos' -Esperado @(401) | Out-Null
  }

  Paso 'DELETE /api/v1/admin/establecimientos/{id}/operadores/{id} (200, sin operadores)' {
    $ops = Invoke-Api -Method DELETE `
      -Path "/api/v1/admin/establecimientos/$script:establecimientoId/operadores/$script:idRecep" `
      -Token $script:adminToken -Esperado @(200)
    if (@($ops).Count -ne 0) { throw "operadores inesperados: $(@($ops).Count)" }
  }

  Paso 'GET /api/v1/recepcion/agenda tras quitar la asignacion (403)' {
    Invoke-Api -Method GET `
      -Path "/api/v1/recepcion/agenda?idEstablecimiento=$script:establecimientoId" `
      -Token $script:recepToken -Esperado @(403) | Out-Null
  }

  Paso 'POST /api/v1/admin/promociones (201)' {
    $script:codigoPromo = 'HUMO' + (Get-Date -Format 'yyyyMMddHHmmss')
    $p = Invoke-Api -Method POST -Path '/api/v1/admin/promociones' `
      -Token $script:adminToken -Esperado @(201) `
      -Body @{
        codigo = $script:codigoPromo
        descripcion = 'Promocion generada por el humo'
        tipoDescuento = 'PORCENTAJE'
        valor = 10
        fechaInicio = (Get-Date -Format 'yyyy-MM-dd')
        fechaFin = (Get-Date).AddDays(30).ToString('yyyy-MM-dd')
        canchas = @($script:canchaId)
      }
    if ($p.estado -ne 'ACTIVA') { throw "estado inesperado: $($p.estado)" }
    $script:promocionId = $p.id
  }

  Paso 'GET /api/v1/admin/promociones (200, incluye la promocion)' {
    $lista = Invoke-Api -Method GET -Path '/api/v1/admin/promociones?estado=ACTIVA&limite=200' `
      -Token $script:adminToken
    $encontrada = $false
    foreach ($p in $lista) {
      if ($p.id -eq $script:promocionId) { $encontrada = $true }
    }
    if (-not $encontrada) { throw "la promocion $script:promocionId no aparece" }
  }

  Paso 'POST /api/v1/admin/promociones/{id}/estado (200, INACTIVA)' {
    $p = Invoke-Api -Method POST -Path "/api/v1/admin/promociones/$script:promocionId/estado" `
      -Token $script:adminToken -Esperado @(200) -Body @{ estado = 'INACTIVA' }
    if ($p.estado -ne 'INACTIVA') { throw "estado inesperado: $($p.estado)" }
  }

  Paso 'GET /api/v1/admin/cocheras (200, incluye la cochera)' {
    # La cochera del flujo de reservas se elimina (queda INACTIVA): se crea una nueva para supervisar.
    $nueva = Invoke-Api -Method POST -Path "/api/v1/establecimientos/$script:establecimientoId/cocheras" `
      -Token $tokens.accessToken -Esperado @(201) `
      -Body @{ nombre = 'Cochera admin'; capacidad = 6; tarifaHora = 4.50 }
    $script:cocheraAdminId = $nueva.id
    $lista = Invoke-Api -Method GET -Path '/api/v1/admin/cocheras?estado=ACTIVA&limite=200' `
      -Token $script:adminToken
    $encontrada = $false
    foreach ($c in $lista) {
      if ($c.id -eq $script:cocheraAdminId) { $encontrada = $true }
    }
    if (-not $encontrada) { throw "la cochera $script:cocheraAdminId no aparece" }
  }

  Paso 'POST /api/v1/admin/cocheras/{id}/estado (200, INACTIVA)' {
    $c = Invoke-Api -Method POST -Path "/api/v1/admin/cocheras/$script:cocheraAdminId/estado" `
      -Token $script:adminToken -Esperado @(200) -Body @{ estado = 'INACTIVA' }
    if ($c.estado -ne 'INACTIVA') { throw "estado inesperado: $($c.estado)" }
  }

  # --- Edicion (CU04.1), baja logica (CU04.2) y comparacion (CU02.2) ------------
  # Se usan recursos dedicados: dar de baja el complejo principal romperia el flujo previo.
  Paso 'POST /api/v1/establecimientos gestion (201)' {
    $script:estGestionId = (Invoke-Api -Method POST -Path '/api/v1/establecimientos' `
      -Token $tokens.accessToken -Esperado @(201) `
      -Body @{
        documentoIdentidad = '89' + (Get-Date -Format 'HHmmssfff')
        idDistrito         = $IdDistrito
        nombre             = 'Complejo Gestion Humo'
        direccion          = 'Av. Gestion 100'
        referencia         = 'Humo'
      }).id
    if (-not $script:estGestionId) { throw 'sin id de establecimiento de gestion' }
  }

  Paso 'POST /api/v1/establecimientos/{id}/canchas gestion (201)' {
    $script:canchaGestionId = (Invoke-Api -Method POST -Path "/api/v1/establecimientos/$script:estGestionId/canchas" `
      -Token $tokens.accessToken -Esperado @(201) `
      -Body @{ nombre = 'Cancha Gestion Humo'; descripcion = 'Humo'; capacidad = 10; precioBase = 60.00 }).id
    if (-not $script:canchaGestionId) { throw 'sin id de cancha de gestion' }
  }

  Paso 'PUT /api/v1/establecimientos/{id} (200, editado)' {
    $e = Invoke-Api -Method PUT -Path "/api/v1/establecimientos/$script:estGestionId" `
      -Token $tokens.accessToken -Esperado @(200) `
      -Body @{ idDistrito = $IdDistrito; nombre = 'Complejo Gestion Editado'; direccion = 'Av. Editada 321'; referencia = 'Humo' }
    if ($e.nombre -ne 'Complejo Gestion Editado') { throw "nombre inesperado: $($e.nombre)" }
  }

  Paso 'PUT /api/v1/canchas/{id} (200, editada)' {
    $c = Invoke-Api -Method PUT -Path "/api/v1/canchas/$script:canchaGestionId" `
      -Token $tokens.accessToken -Esperado @(200) `
      -Body @{ nombre = 'Cancha Gestion Editada'; descripcion = 'Editada'; capacidad = 12; precioBase = 65.00 }
    if ($c.capacidad -ne 12) { throw "capacidad inesperada: $($c.capacidad)" }
  }

  Paso 'GET /api/v1/canchas/comparar (200, dos fichas)' {
    $fichas = Invoke-Api -Method GET -Path "/api/v1/canchas/comparar?ids=$script:canchaGestionId,$script:canchaId"
    if ($null -eq $fichas -or $fichas.Count -ne 2) { throw "esperaba 2 fichas y recibio $($fichas.Count)" }
    if ($fichas[0].id -ne $script:canchaGestionId) { throw 'el orden de las fichas no se respeto' }
  }

  Paso 'DELETE /api/v1/canchas/{id} (204, baja logica)' {
    Invoke-Api -Method DELETE -Path "/api/v1/canchas/$script:canchaGestionId" `
      -Token $tokens.accessToken -Esperado @(204) | Out-Null
  }

  Paso 'GET /api/v1/canchas/{id} tras la baja (200, INACTIVA)' {
    $c = Invoke-Api -Method GET -Path "/api/v1/canchas/$script:canchaGestionId"
    if ($c.estado -ne 'INACTIVA') { throw "estado inesperado: $($c.estado)" }
  }

  Paso 'DELETE /api/v1/establecimientos/{id} (204, baja logica)' {
    Invoke-Api -Method DELETE -Path "/api/v1/establecimientos/$script:estGestionId" `
      -Token $tokens.accessToken -Esperado @(204) | Out-Null
  }

  Paso 'POST /api/v1/admin/usuarios/{id}/estado (200, INACTIVO)' {
    $e = Invoke-Api -Method POST -Path "/api/v1/admin/usuarios/$script:usuarioId/estado" `
      -Token $script:adminToken -Esperado @(200) -Body @{ estado = 'INACTIVO' }
    if ($e.estado -ne 'INACTIVO') { throw "estado inesperado: $($e.estado)" }
  }

  Paso 'POST /api/v1/auth/login con cuenta inactiva (403)' {
    Invoke-Api -Method POST -Path '/api/v1/auth/login' -Esperado @(403) `
      -Body @{ correo = $correo; password = $password } | Out-Null
  }
}

Write-Host ""
if ($fallos -eq 0) {
  Write-Host "RESULTADO: OK ($pasos/$pasos pasos)" -ForegroundColor Green
  exit 0
}
Write-Host "RESULTADO: FALLO ($fallos de $pasos pasos)" -ForegroundColor Red
exit 1
