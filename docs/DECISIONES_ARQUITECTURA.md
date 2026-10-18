# Decisiones de arquitectura (ADR)

Formato abreviado: contexto -> decision -> consecuencias. Estado: `ACEPTADA` salvo
indicacion.

## ADR-0001 - Monolito modular + arquitectura hexagonal
**Contexto:** un solo producto, un backend, una BD; el prompt exige Clean/Hexagonal
y prohibe microservicios por moda.
**Decision:** monolito modular con modulos `identity, geography, catalog, venues,
bookings, payments, settlements, engagement, platform`. Cada modulo:
`domain` (solo Java) -> `application` (puertos in/out + servicios) -> `adapter.in.rest`
/ `adapter.out.*`. Dependencias de fuera hacia dentro.
**Consecuencias:** `domain` sin JPA/REST/Quarkus; un modulo no importa entidades JPA
de otro (usa IDs y puertos); verificable con test de arquitectura.

## ADR-0002 - Versiones fijadas (verificadas)
**Contexto:** el prompt exige versiones estables reales, no inventadas.
**Decision:** Quarkus **3.40.1** (ultima estable; `4.0.0.Beta1` descartada), Java **21**
(`B:\cursos\java\jdk-21.0.2`), Angular **22.2.3**, Node 22.13.0, MySQL 8.0.41.
Quarkus verificado contra Maven Central (`quarkus-bom`); Angular contra npm.
**Consecuencias:** fijado en `backend/pom.xml`; actualizar solo con verificacion.

## ADR-0003 - Jerarquia geografica y ubigeo
**Decision:** anadir `departamento` y `provincia`; `distrito.id_provincia` (FK) y
`distrito.codigo_ubigeo` (6 dig. INEI, unico). Se elimina `UNIQUE(distrito.nombre)`
global y se reemplaza por `UNIQUE(id_provincia, nombre)`.
**Consecuencias:** semillas oficiales opcionales; el nombre de distrito ya no es
identificador nacional.

## ADR-0004 - `tipo_espacio` y `estado_reserva`
**Decision:** `tipo_espacio` (S1/S2) es una dimension distinta de `deporte`; se modela
como atributo/taxonomia de cancha (no se fuerza equivalencia con `deporte`).
`estado_reserva` se modela como **maquina de estados tipada + restriccion CHECK**
(valores) en lugar de tabla fisica, preservando sus reglas de transicion.
**Consecuencias:** ambos quedan documentados en la trazabilidad; no se borra material.

## ADR-0005 - Concurrencia de reservas sin triggers
**Contexto:** los triggers T-SQL `TR_reserva_sin_solapamiento` / `..._respeta_bloqueos`
no son trasladables y no garantizan serializacion.
**Decision:** transaccion InnoDB + `SELECT ... FOR UPDATE` sobre la fila de `cancha`
para serializar; validacion de solape `nuevoInicio < existenteFin AND nuevoFin >
existenteInicio` sobre reservas que retienen cupo; locking de cochera en **orden
consistente**; snapshot inmutable de importe; resultado de pago **fuera** del lock
largo; clave de idempotencia; mapeo de deadlock/timeout a 409 con reintento acotado.
**Consecuencias:** test obligatorio de 20-50 solicitudes concurrentes al mismo slot.
**Estado:** IMPLEMENTADO y VERIFICADO - ver ADR-0013 y la evidencia E-08 (30 peticiones
simultaneas -> exactamente 1 reserva).

## ADR-0006 - Unicidad parcial (SQL Server -> MySQL)
**Contexto:** SQL Server usa `CREATE UNIQUE INDEX ... WHERE principal=1` y
`WHERE estado='APROBADO'`; MySQL no lo soporta.
**Decision:** columnas generadas `STORED` con `CASE WHEN ... THEN ... ELSE NULL END`
(`foto_cancha.id_cancha_principal`, `pago.id_reserva_aprobada`) + `UNIQUE`.
**Consecuencias:** la unicidad se garantiza a nivel de motor; ver V3 y sus pruebas.

## ADR-0007 - Extensiones minimas de esquema
**Decision:** `auth_token`, `sesion_refresh`, `idempotencia`,
`solicitud_cambio_reserva`, `config_establecimiento`, `reclamo_seguimiento`,
`pago_evento`. Se descartan tecnologias no justificadas (event bus, Redis, Kafka).
**Consecuencias:** cada extension tiene caso de uso, constraint e indice.

## ADR-0008 - Tiempo, moneda y zona horaria
**Decision:** instantes en `DATETIME(6)` con convencion UTC; conversion a
`America/Lima` en el borde; `LocalTime` para horarios semanales; `BigDecimal` para
montos (nunca `double`); PEN; redondeo a 2 decimales; `Clock` inyectable. La
propiedad `quarkus.jackson.time-zone` no es reconocida en este stack -> configurar el
`ObjectMapper` en codigo.
**Consecuencias:** test de medianoche pendiente.

## ADR-0009 - Credenciales de BD separadas (migracion vs runtime)
**Decision:** `canchadirecta_mig` (DDL, solo para Flyway via
`quarkus.flyway.username/password`) y `canchadirecta_app` (DML minimo). Contrasenas
solo en `.env` (gitignored). Base `canchadirecta` propia; no se tocan otras bases
(`ikcore`, `acme`, `clinica`, ...).
**Consecuencias:** `quarkus.flyway.clean-disabled=true`; prohibido `FLYWAY CLEAN`/`DROP`.

## ADR-0010 - Puertos no conflictivos
**Contexto:** el equipo tiene ocupados 80, 3306, 3307, 4200, 5432, 8080, 8081, 8090,
11434, etc. (otros proyectos y Ollama).
**Decision:** el backend nativo usa **8088**; Compose publica en puertos **libres**
verificados en el momento de la puesta en marcha: MySQL aislado -> **3309**,
backend contenedor -> **8089**, frontend (Nginx) -> **8083**. Todos parametrizables por
`.env` (`MYSQL_HOST_PORT`, `BACKEND_PORT`, `FRONTEND_PORT`).
**Consecuencias:** el backend contenedor se publica en el host solo para
verificacion/diagnostico; en el despliegue real basta la red interna de Compose. Si
un puerto se ocupa despues, se cambia solo en `.env`.

## ADR-0011 - JWT HS256 con clave local fuera del repositorio
**Contexto:** se necesitan tokens de acceso sin versionar secretos ni depender de un
proveedor de identidad externo (Keycloak descartado por el prompt).
**Decision:** `quarkus-smallrye-jwt` (verificacion) + `quarkus-smallrye-jwt-build`
(emision), algoritmo **HS256** con clave simetrica en formato **JWK** almacenada en
`canchadirecta/.secrets/jwt-signing-key.jwk` (gitignored), generada por
`scripts/jwt-key-bootstrap.ps1`. La ruta llega por `JWT_SIGN_KEY_LOCATION` y se usa en
`smallrye.jwt.sign.key.location` y `mp.jwt.verify.publickey.location`
(`mp.jwt.verify.publickey.algorithm=HS256`). El **refresh token es opaco** (48 bytes
aleatorios, base64url) y solo se persiste su **SHA-256** en `sesion_refresh`.
**Consecuencias:** rotacion de clave = invalidar tokens vigentes (documentado en el
script); en produccion, mover la clave a un gestor de secretos o usar RS256 con par de
claves; la autorizacion fina (ownership) va en los servicios, no en el token.

## ADR-0012 - Persistencia selectiva por SQL nativo
**Contexto:** la validacion de esquema de Hibernate rechaza tipos sin mapeo JPA directo
(p. ej. `CHAR(64)` en `auth_token.token_hash` / `sesion_refresh.token_hash`) y los ids
`INT` frente a `Long`.
**Decision:** mapear con entidades JPA solo las tablas cuyo tipo calza exactamente
(caso `usuario`, con id `Integer`); para el resto de escrituras puntuales (p. ej.
sesiones) usar **SQL nativo** via `EntityManager`. `quarkus.hibernate-orm.schema-management.strategy=validate`
se mantiene activo como red de seguridad.
**Consecuencias:** menos mapeos fragiles; en tablas con `CHAR` de hash se evaluara
migrar a `VARCHAR(64)` si en el futuro se requiere entidad JPA.

## ADR-0013 - Implementacion del bloqueo de reservas (despliega ADR-0005)
**Contexto:** habia que garantizar en codigo que N peticiones simultaneas al mismo slot
produzcan como maximo una reserva.
**Decision:** `CrearReservaService.crear` es el unico metodo `@Transactional` y ejecuta, en
este orden: (1) `SELECT id_cancha, precio_base, estado FROM cancha WHERE id_cancha = ? FOR
UPDATE` - bloqueo exclusivo de la fila de la cancha que serializa todas las reservas de esa
cancha y se mantiene hasta el commit; (2) comprobacion de solape contra `reserva` (estados
`PENDIENTE`/`CONFIRMADA`) y `bloqueo_cancha`, ambas lecturas **tambien** con `FOR UPDATE`
para leer la ultima version confirmada y no la instantanea de la transaccion; (3) `INSERT`
en `reserva` + `historial_reserva`; (4) commit. El titular sale del `sub` del JWT (nunca del
cuerpo). El solape se evalua con intervalos semiabiertos (`inicio < fin AND fin > inicio`),
asi que las franjas contiguas no chocan. La violacion se mapea a **409 `SLOT_UNAVAILABLE`**.
**Consecuencias:** la serializacion es **por cancha** (canchas distintas no se bloquean entre
si) y dura lo que dura la transaccion, por lo que el caso de uso no debe contener llamadas
externas (el pago simulado ira fuera, ADR-0005). Evidencia: E-08 (30 hilos -> 1 reserva).

## ADR-0014 - `DATETIME` del esquema tratado como UTC explicito
**Contexto:** el esquema usa `DATETIME(6)` sin zona. Al escribir con `Instant` desde
Hibernate y leer con el driver, las horas se desplazaban -5 h (America/Lima frente a
`serverTimezone=UTC`): la reserva guardada y la devuelta por la API no coincidian (`L-04`).
La propiedad `quarkus.hibernate-orm.jdbc.time-zone` **no existe** en este stack (clave
ignorada).
**Decision:** en `ReservaPersistenciaAdapter` los instantes se envian y se leen como
literales `yyyy-MM-dd HH:mm:ss.SSSSSS` calculados explicitamente en UTC
(`LocalDateTime.ofInstant(instante, ZoneOffset.UTC)` al escribir y `CAST(columna AS CHAR)`
+ `Instant.parse` al leer). Ni Hibernate ni el driver reinterpretan la hora.
**Consecuencias:** `bookings` es determinista frente a la zona de la JVM y devuelve el
instante exacto solicitado. Queda pendiente unificar el criterio en `identity` (hoy
autoconsistente, pero guardando horas locales) y fijar la serializacion JSON con un
`ObjectMapper` en codigo (ADR-0008).

## ADR-0015 - Disponibilidad por fecha a partir de la ventana de atencion
**Contexto:** hay que ofrecer franjas reservables por cancha y dia, sin exponer la
complejidad de `horario_cancha` y sin que el catalogo de bloques cambie al reservar.
**Decision:** `GET /api/v1/canchas/{id}/disponibilidad?fecha=&duracionMinutos=` (publico)
interpreta la fecha en `cd.zona-horaria` (`America/Lima`) y toma la ventana del dia desde
`horario_cancha`. Mientras la cancha **no tenga ningun horario activo** (agenda sin
configurar) se usa `cd.reservas.ventana-defecto-inicio/fin` (`06:00-23:00`); en cuanto el
propietario define agenda, los **dias sin horario quedan cerrados** (actualizado por
ADR-0017). La ventana se parte en bloques de
`duracionMinutos` (30-180) **alineados a su inicio** con
`FranjaHoraria.bloquesLibres(...)`, descartando los que se solapan con reservas vigentes o
con `bloqueo_cancha` (una sola consulta `UNION ALL`), y los que ya empezaron. El precio del
bloque es el de `horario_cancha` o, si es nulo, el `precio_base` de la cancha.
**Consecuencias:** el catalogo es estable respecto a las reservas existentes; la ventana
puede durar mas de 180 minutos porque el limite de duracion aplica a los bloques, no a la
ventana. La lectura no toma bloqueos (es una consulta, no una escritura). La ventana por
defecto deja de ser el comportamiento normal en cuanto el propietario configura su agenda.

## ADR-0016 - Cancelacion de reserva: ventana minima y liberacion por estado
**Contexto:** al cancelar debe liberarse la franja sin permitir cancelaciones abusivas ni
dobles cancelaciones.
**Decision:** `POST /api/v1/reservas/{id}/cancelar` (autenticado) lee la reserva con
`SELECT ... FOR UPDATE` sobre su fila y, dentro de la misma transaccion, valida en este
orden: titular (`sub` del JWT, `403`), estado cancelable (`409`), ya comenzada o dentro de
`cd.reservas.horas-minimas-cancelacion` (2 h por defecto, `409`); luego actualiza el estado
a `CANCELADA` y escribe `historial_reserva` (`PENDIENTE -> CANCELADA`).
**Consecuencias:** liberar la franja no requiere borrar nada: las comprobaciones de solape
solo consideran `PENDIENTE`/`CONFIRMADA`, asi que la franja vuelve a ofrecerse de inmediato.
Dos cancelaciones simultaneas no pueden registrarse dos veces (la segunda ve el estado ya
cancelado). La politica economica queda fuera (`L-12`).

## ADR-0017 - Agenda del propietario (horarios y bloqueos) y cierre de dias sin horario
**Contexto:** hasta ahora nadie creaba filas en `horario_cancha` desde la API, de modo que la
disponibilidad vivia de la ventana por defecto (`L-11`); ademas RN-03 (no reservar en
bloqueos) no tenia forma de crear bloqueos ni prueba de su cruce con las reservas.
**Decision:**
1. La agenda de una cancha se gestiona en el modulo `venues` con casos de uso propios
   (`GestionAgendaUseCase`): `GET/POST /api/v1/canchas/{id}/horarios`,
   `DELETE .../horarios/{id}`, `GET/POST /api/v1/canchas/{id}/bloqueos` y
   `DELETE .../bloqueos/{id}`. La lectura de horarios es publica (igual que el catalogo); el
   resto exige ser el **propietario** del establecimiento de la cancha
   (`cancha -> establecimiento -> propietario`, `403` si no lo es, `404` si no existe).
2. Los horarios son franjas semanales (dia ISO 1-7 + hora local, precio opcional): se rechazan
   solapes con otro horario **activo** del mismo dia (`409`), rangos invertidos y dias fuera de
   rango (`400`). La baja es **logica** (`activo = 0`), que es para lo que existe la columna.
3. Los bloqueos usan el **mismo orden de bloqueo que RN-01**: `SELECT ... FOR UPDATE` sobre la
   fila de `cancha` antes de comprobar solapes, de modo que una reserva no pueda colarse entre
   la comprobacion y el `INSERT`. Se rechaza (`409`) si choca con una reserva vigente o con
   otro bloqueo; el rango y el motivo se validan en el agregado (`400`).
4. **Una cancha con agenda ya no usa la ventana por defecto:** si tiene algun horario activo,
   cada dia se abre segun su agenda y un dia sin horarios queda cerrado. La ventana por defecto
   solo rige mientras la cancha no tenga **ningun** horario (compatibilidad con `venues` recien
   creados y con `L-11` resuelta).
**Consecuencias:** la disponibilidad refleja la agenda real y los bloqueos operativos; el
listado de horarios es publico para que el futuro frontend pinte el calendario. `venues` lee
`reserva` para validar el cruce con reservas (`bloqueo_cancha` + `reserva` compartidos por el
mismo esquema del monolito modular); el endpoint de alta de bloqueos queda fuera de `bookings`
para no mover la propiedad del recurso (la cancha es de `venues`). Las excepciones por fecha
(feriados) siguen sin soporte (`L-13`).
**Evidencia:** `AgendaTest` (5 tests) + humo 33/33 (`docs/RESULTADOS_PRUEBAS.md` E-12/E-13).

## ADR-0018 - Atencion de reservas por el propietario (estado `RECHAZADA`)

**Contexto:** HU-008-2 (reservas de recursos del propietario) seguia sin cubrir: el propietario no
podia ver las solicitudes recibidas en sus canchas ni aceptarlas o rechazarlas. El catalogo del
esquema fuente (`ck_reserva_estado`, V1) solo admitia `PENDIENTE/CONFIRMADA/CANCELADA/FINALIZADA`,
asi que un rechazo del propietario no tenia estado propio.

**Decision:**
1. **Extension minima del CHECK (ADR-0007):** `V5__estado_reserva_rechazada.sql` anade
   `'RECHAZADA'` al catalogo. No es un alias de `CANCELADA`: cambia el responsable (propietario
   frente a titular) y, cuando existan pagos, el tratamiento economico (`L-12`).
2. Casos de uso en `bookings` (`AtenderReservaUseCase`): `GET /api/v1/reservas/recibidas`
   (bandeja del propietario, filtro opcional `estado`), `POST /api/v1/reservas/{id}/confirmar` y
   `POST /api/v1/reservas/{id}/rechazar` (motivo opcional de hasta 200 caracteres, para que el
   prefijo fijo no desborde el `VARCHAR(250)` de `historial_reserva.observacion`).
3. **Autorizacion por recurso, no por rol:** la propiedad se comprueba con
   `reserva -> cancha -> establecimiento -> propietario -> usuario` (`403` si no lo es, `404` si
   la reserva no existe). El titular de la reserva tampoco decide sobre ella.
4. Ambas decisiones siguen el patron de la cancelacion (ADR-0016): `FOR UPDATE` de la fila de la
   reserva, cambio de estado y traza en `historial_reserva` en la misma transaccion. Solo se
   atienden reservas `PENDIENTE` que **no hayan comenzado** (`409` en cualquier otro caso).
5. Al pasar a `RECHAZADA` la franja deja de retener cupo (la ocupacion cuenta solo `PENDIENTE` y
   `CONFIRMADA`), por lo que reaparece en la disponibilidad sin borrar ninguna fila.

**Consecuencias:** el propietario cierra el ciclo de vida de las solicitudes y el rechazo libera
la franja igual que una cancelacion. Una reserva `PENDIENTE` ya ocupa la franja (no hay
expiracion automatica) y revocar una reserva **ya confirmada** sigue sin soporte (`L-14`).

**Evidencia:** `ReservasTest` (6 tests nuevos; 45/45) + humo 39/39 (`docs/RESULTADOS_PRUEBAS.md`
E-14/E-15).

## ADR-0019 - Cochera opcional con capacidad (RN-08) y orden de locking cancha -> cochera

**Contexto:** RN-08 exige que la cochera sea **opcional**, coherente con el establecimiento de la
cancha y con su **capacidad** (la tabla `cochera` tiene `capacidad` > 1, ver analisis R2). Habia
que evitar carreras entre canchas distintas compitiendo por la misma cochera y no permitir
cocheras de otro establecimiento.

**Decision:**
1. **Gestion (modulo `venues`):** la cochera pertenece al **establecimiento**
   (`POST`/`GET /api/v1/establecimientos/{id}/cocheras`,
   `DELETE /api/v1/establecimientos/{id}/cocheras/{id}`), siempre con ownership
   (`establecimiento -> propietario -> usuario`, `403` si no lo es). La baja es **logica**
   (`estado = 'INACTIVA'`, como el resto del esquema).
2. **Reserva con cochera opcional (modulo `bookings`):** `POST /api/v1/reservas` acepta un
   `cocheraId` opcional. La cochera debe ser del **mismo establecimiento** que la cancha (`400`
   en caso contrario), estar `ACTIVA` y tener **cupo** en la franja (`409 SLOT_UNAVAILABLE` si
   no). El `monto` de `reserva_cochera` se congela al crear la reserva
   (`tarifa_hora x horas`, redondeo `HALF_UP` a 2 decimales).
3. **Capacidad por solape:** el cupo libre es `capacidad - reservas_cochera_vigentes` que se
   **solapan** con `[inicio, fin)`; solo cuentan las cocheras cuya reserva padre esta
   `PENDIENTE`/`CONFIRMADA`, de modo que cancelar o rechazar **libera el cupo** sin borrar filas.
4. **Orden de locking consistente (despliega ADR-0005/0013):** dentro de la transaccion de
   creacion se bloquea primero la fila de la **cancha** (`FOR UPDATE`) y, acto seguido, la fila
   de la **cochera** (`FOR UPDATE`), **antes** de las lecturas bloqueantes de `reserva` /
   `bloqueo_cancha`. Esto serializa a todas las peticiones que compiten por la misma cochera
   (aunque sean de canchas distintas) y evita un **deadlock** real detectado durante el
   desarrollo: si la cochera se bloqueaba despues de las lecturas de rango sobre `reserva`,
   cada peticion retenia bloqueos de rango de su propia franja y el ciclo con el lock de la
   cochera producia `Deadlock found when trying to get lock` (comprobado y corregido reordenando
   los bloqueos).
5. **Dato de cupo publico:** `GET /api/v1/canchas/{idCancha}/cocheras?inicio=&fin=` (publico,
   igual que la disponibilidad) devuelve cada cochera activa del establecimiento con su
   `cupoDisponible`, para que el usuario elija antes de reservar.
6. **Respuesta controlada ante locking:** un `PessimisticLockException` (deadlock/timeout de
   bloqueo) se mapea a **409 `RESOURCE_BUSY`** en lugar de un `500` opaco, para que el cliente
   pueda reintentar (prompt maestro 05.3.10).

**Consecuencias:** la reserva sin cochera se mantiene igual que antes (`cocheraId` nulo); la
serializacion adicional solo aplica cuando hay cochera. `bookings` lee y escribe `cochera` y
`reserva_cochera` (esquema compartido del monolito modular). El reintento acotado automatico
ante `RESOURCE_BUSY` queda como mejora pendiente.

**Evidencia:** `CocheraTest` (12 tests: capacidad 1/2, intervalos parciales, coherencia de
establecimiento, cochera inactiva, liberacion al cancelar y 4 canchas concurrentes -> exactamente
2 reservas) + `mvnw test` 57/57 (`docs/RESULTADOS_PRUEBAS.md` E-16).

## ADR-0020 - Pago simulado, reembolso y frontera del modulo `payments`

**Contexto:** el alcance excluye pagos reales (REQUISITOS §2) pero si exige pago, reembolso y
liquidacion simulados (F05/CU05). La tabla `pago` no puede tener dos `APROBADO` por reserva (RN-05)
y la cancelacion/rechazo no tenian efecto economico (L-12). Habia que decidir donde vive la logica
de pago y como se comunica con `bookings` sin ciclos de dependencia.

**Decision:**
1. **Modulo `payments`:** dominio propio (`Pago`, `Reembolso`), puertos `PagoRepositorio` y
   `PaymentGatewayPort`, servicios `PagoSimuladoService` y `PoliticaReembolsoService`, con
   adaptadores de persistencia (SQL nativo), pasarela y REST. La pasarela externa esta
   **deshabilitada**: se usa un **simulador local determinista** (`SimuladorPagoAdapter`).
2. **Simulador determinista:** el cobro se aprueba salvo que `codigoOperacion` empiece por
   `RECHAZA`; el codigo devuelto es el recibido o uno reproducible derivado del importe
   (`SIM-<importe>`). Mismo cuerpo -> mismo resultado, sin estado ni aleatoriedad.
3. **Importe:** total congelado de la reserva = `monto_cancha - monto_descuento + monto_cochera`.
   Cada intento escribe un evento `pago_evento` (`SOLICITADO` + `APROBADO`/`RECHAZADO`).
4. **RN-05:** como maximo un pago `APROBADO` por reserva. Se comprueba antes de cobrar (`409`) y
   la base de datos lo garantiza con la columna generada `id_reserva_aprobada` + `UNIQUE`
   (ADR-0006). Los intentos `RECHAZADO` no ocupan ese cupo, por lo que el titular puede reintentar.
5. **Frontera sin ciclos:** la politica se invoca desde `bookings` a traves del puerto declarado en
   `shared` (`PoliticaReembolso`), que implementa `payments`. Asi `bookings` no depende de
   `payments`; la dependencia va solo en un sentido (`payments` -> `bookings` para leer la reserva).
   El reembolso se ejecuta **dentro de la misma transaccion** que cambia el estado de la reserva
   (atomicidad).
6. **Politica economica (L-12):** el pago **no** cambia el estado de la reserva (la confirmacion
   sigue siendo del propietario, HU-008-2). Al liberar la reserva: **rechazo del propietario** ->
   reembolso total del pago aprobado; **cancelacion del titular** -> reembolso total solo si se
   cancela dentro de la ventana gratuita (`cd.pagos.horas-cancelacion-gratis` = 24 h), en caso
   contrario no hay reembolso. El reembolso queda `PROCESADO` y el pago pasa a `REEMBOLSADO`.
   Como solo busca pagos en `APROBADO`, una segunda liberacion no vuelve a reembolsar (idempotente).

**Consecuencias:** `payments` es la primera pieza de F05; las liquidaciones (`settlements`, ADR-0021)
y la expiracion por `hold_pago_min` (ADR-0022) se anadieron despues. El simulador se
sustituye por una pasarela real implementando `PaymentGatewayPort` sin tocar el resto.

**Evidencia:** `PagoTest` (11 tests: cobro aprobado/rechazado/reintento, RN-05 con `409`, monto con
cochera, autorizacion, reembolso dentro/fuera de ventana y por rechazo) + `mvnw test` 68/68 + humo
53/53 (`docs/RESULTADOS_PRUEBAS.md` E-18/E-19).

## ADR-0021 - Liquidaciones del propietario y frontera del modulo `settlements`

**Contexto:** CU04.8/CU05.2/CU05.4 (ingresos, comision y liquidacion) seguian pendientes (L-07). Las
tablas `liquidacion` y `detalle_liquidacion` ya existian en V1 con `UNIQUE(id_reserva)` (RN-07) y el
`CHECK` de `porcentaje_comision` 0..100. Faltaba generar la liquidacion a partir de las reservas
pagadas del propietario, aplicar la comision y exponerla (owner/settlements).

**Decision:**
1. **Modulo `settlements`:** dominio propio (`Liquidacion` cabecera, `DetalleLiquidacion` linea),
   puerto `LiquidacionRepositorio`, caso de uso `GestionarLiquidacionesUseCase` (servicio
   `LiquidacionService`), adaptador de persistencia (SQL nativo) y `LiquidacionResource`
   (`POST /api/v1/liquidaciones`, `GET /api/v1/liquidaciones`, `GET .../liquidaciones/{id}`).
2. **Que se liquida:** reservas del propietario con **pago `APROBADO`**, estado **`CONFIRMADA`/
   `FINALIZADA`** (ni canceladas ni rechazadas) y cuya fecha de inicio cae en
   `[periodoInicio, periodoFin]`. El monto por reserva es `pago.monto` (incluye la cochera opcional).
3. **Comision:** `cd.liquidaciones.porcentaje-comision` (default funcional 10 %). Por linea:
   `monto_comision = round(monto_reserva x pct / 100, 2)` y `monto_neto = monto_reserva -
   monto_comision`; la cabecera suma las lineas (`bruto = Σ reserva`, `comision = Σ comision`,
   `neto = Σ neto`).
4. **RN-07 (una reserva, una liquidacion):** la generacion bloquea la fila del `propietario`
   (`SELECT ... FOR UPDATE`) antes de leer las reservas liquidables, serializando generaciones
   concurrentes del mismo propietario; el `UNIQUE(id_reserva)` de `detalle_liquidacion` es la
   garantia final. La consulta excluye las reservas ya presentes en `detalle_liquidacion`.
5. **Frontera sin ciclos:** `settlements` no importa clases de `bookings` ni de `payments`. La unica
   lectura cruzada es una **proyeccion SQL** (read model) que une `reserva` + `pago` + `cancha` +
   `establecimiento`; la escritura queda confinada a `liquidacion`/`detalle_liquidacion`. No hay
   dependencias entre modulos.
6. **Autorizacion:** el propietario se deriva del `sub` del JWT; solo ve y genera sus liquidaciones
   (`403` si es ajena, `404` si no existe). Periodo invalido -> `400`; sin reservas liquidables o sin
   perfil de propietario -> `409`.

**Consecuencias:** F05 queda cubierto en modo simulado (pago, reembolso, comision y liquidacion). Los
estados `PROCESADA`/`PAGADA` de la liquidacion (desembolso real) siguen pendientes (L-07); la
expiracion de solicitudes `PENDIENTE` por `hold_pago_min` se resolvio en ADR-0022.

**Evidencia:** `LiquidacionTest` (10 tests: calculo con comision, exclusion de reservas sin pago/sin
confirmar/canceladas, RN-07 sin doble liquidacion, autorizacion y validaciones de periodo) +
`mvnw test` 78/78 + humo 59/59 (`docs/RESULTADOS_PRUEBAS.md` E-20/E-21).

## ADR-0022 - Expiracion del hold de pago y revocacion de reservas confirmadas

**Contexto:** quedaban dos piezas del ciclo de reserva (L-07, L-14). Por un lado, una solicitud
`PENDIENTE` que nadie paga ni atiende **retiene la franja indefinidamente**: `config_establecimiento
.hold_pago_min` (default 10 min en V3) existia pero no se usaba. Por otro, un propietario **no podia
revocar una reserva ya `CONFIRMADA`**: `/rechazar` solo acepta `PENDIENTE`.

**Decision:**
1. **Estado `EXPIRADA`** (migracion V6, amplia `ck_reserva_estado`): una solicitud `PENDIENTE` sin pago
   `APROBADO` que supera su hold de pago pasa a `EXPIRADA`. Es una extension minima justificada
   (ADR-0007): no equivale a cancelacion del titular (ADR-0016) ni a rechazo del propietario
   (ADR-0018), y el listado debe poder distinguirla. Libera la franja igual que los otros terminales.
2. **Job programado transaccional:** `ExpirarReservasService` (`@Scheduled(every = "60s")` sobre la
   extension `quarkus-scheduler`) delega en el caso de uso `ExpirarReservasUseCase.expirarVencidas(max)`;
   el lote se acota con `cd.reservas.expiracion-lote-max` (200). En el perfil `test` el temporizador esta
   desactivado (`%test.quarkus.scheduler.enabled=false`) y las pruebas invocan el caso de uso de forma
   directa y determinista.
3. **Que expira:** reservas `PENDIENTE` **sin pago `APROBADO`** y con `fecha_creacion` anterior a
   `NOW() - hold`. El hold sale de `config_establecimiento.hold_pago_min` de la cancha; si no hay fila de
   configuracion se aplica `cd.reservas.hold-pago-min` (10). Una reserva **pagada** no expira (el titular
   ya pago) ni una **dentro** del hold.
4. **Reloj de la base (`NOW(6)`):** la antiguedad se mide contra el reloj del servidor, que es el mismo
   que llena `fecha_creacion` por defecto (`CURRENT_TIMESTAMP`). Medir contra una marca de la app
   mezclaria zonas horarias (L-04). Las filas se leen con `FOR UPDATE OF r` (solo la reserva) para no
   bloquear la cancha (que RN-01 bloquea al reservar) y para que ninguna otra decision cambie el estado
   a la vez.
5. **Revocacion (L-14):** `POST /api/v1/reservas/{id}/revocar` (motivo opcional, mismo DTO que el
   rechazo). El propietario revoca una reserva `CONFIRMADA` que **no haya comenzado**; pasa a
   `RECHAZADA` (se reutiliza el estado ya existente, sin anadir uno nuevo: la responsabilidad del
   propietario ya esta separada de la cancelacion del titular) y **reembolsa el total** reutilizando
   `PoliticaReembolso` con `porPropietario = true`.
6. Reutiliza el patron de locking existente (`buscarParaActualizar` + `cambiarEstado`), por lo que dos
   revocaciones simultaneas no pueden registrarse dos veces.

**Consecuencias:** F05 queda cerrado en modo simulado (pago, reembolso, comision, liquidacion y
expiracion); L-14 resuelto. El paso automatico a `FINALIZADA` se decidio por separado (ADR-0023).
L-04 (unificar la zona horaria) sigue pendiente, pero el job es inmune por medir contra el reloj de la
base.

**Evidencia:** `ExpiracionReservasTest` (4 tests: expira vencida sin pago, no expira dentro del hold, no
expira con pago aprobado, respeta el hold del establecimiento) + `ReservasTest` (3 nuevas de revocacion:
LIBERA la franja y traza, `409` si no esta confirmada, `403/404/401`) + `mvnw test` 85/85 + humo 63/63
(`docs/RESULTADOS_PRUEBAS.md` E-22/E-23).

## ADR-0023 - Cierre automatico de reservas concluidas (estado `FINALIZADA`)

**Contexto:** `FINALIZADA` ya existia en el dominio y en `ck_reserva_estado` (ADR-0004/V5) y las
liquidaciones ya lo contaban (RN-07, ADR-0021), pero **nada lo asignaba**: una reserva `CONFIRMADA`
cuya franja ya habia terminado se quedaba en `CONFIRMADA` para siempre. Ese estado colgante impedia
distinguir lo consumido de lo vigente y dejaba sin frontera temporal el cierre contable del propietario
(L-15).

**Decision:**
1. **Sin estado ni migracion nuevas:** se reutiliza `FINALIZADA`. El job mueve `CONFIRMADA` ->
   `FINALIZADA`; como `ocupaFranja` solo cuenta `PENDIENTE`/`CONFIRMADA`, la franja deja de retener cupo
   (irrelevante en la practica porque ya paso, pero consistente).
2. **Job programado transaccional:** `FinalizarReservasService` (`@Scheduled(every = "60s")`) delega en
   `FinalizarReservasUseCase.finalizarConcluidas(max)`; el lote se acota con
   `cd.reservas.finalizacion-lote-max` (200). Mismo patron que la expiracion (ADR-0022): en el perfil
   `test` el temporizador esta desactivado y las pruebas invocan el caso de uso directamente.
3. **Que finaliza:** reservas en estado `CONFIRMADA` cuyo `fin` ya quedo en el pasado. No toca el importe
   ni genera reembolso: el titular dispuso de su franja. Una `PENDIENTE` **no** se finaliza aqui (su
   cierre lo hace la expiracion del hold, ADR-0022).
4. **Reloj en UTC (`UTC_TIMESTAMP(6)`), no `NOW(6)`:** `inicio`/`fin` se guardan como literales UTC
   (SqlUtc, ADR-0014), mientras que `NOW(6)` es la hora **local** del servidor. El "ya concluyo" debe
   compararse contra el instante actual en UTC; de otro modo el desfase de zona (L-04) adelantaria o
   retrasaria el cierre. `UTC_TIMESTAMP(6)` es independiente de la zona del servidor, asi que el job
   queda inmune a ella. Las filas se leen con `FOR UPDATE` (sin join: solo bloquea `reserva`) para no
   cerrar una reserva mientras el propietario la revoca (L-14).
5. Reutiliza `cambiarEstado` (traza en `historial_reserva`, misma proteccion ante decisiones
   simultaneas).

**Consecuencias:** el ciclo de vida de la reserva queda completo
(`PENDIENTE` -> `CONFIRMADA` -> `FINALIZADA`, con salidas `CANCELADA`/`RECHAZADA`/`EXPIRADA`); L-15
resuelto. `FINALIZADA` y `CONFIRMADA` cuentan igual en las liquidaciones (ADR-0021), asi que el cierre no
cambia el calculo de comisiones.

**Evidencia:** `FinalizacionReservasTest` (4 tests: finaliza confirmada vencida y traza, no finaliza
vigente, no finaliza pendiente, no arrastra reservas vigentes del lote) + `mvnw test` 89/89
(`docs/RESULTADOS_PRUEBAS.md` E-24). El job no tiene superficie HTTP, por lo que no se cubre en el humo.

## ADR-0024 - Calificaciones de reservas finalizadas (modulo `engagement`)

**Contexto:** F06 (CU03.8 calificar, CU02.5 ver calificaciones) exige que el titular valore la cancha
que uso y que el catalogo muestre su reputacion. La tabla `calificacion` existe desde V1
(`UNIQUE(id_reserva)`, `ck_calificacion_puntaje BETWEEN 1 AND 5`), pero **nada la usaba**. RN-04 fija
una sola calificacion por reserva.

**Decision:**
1. **Sin migracion:** se usa la tabla `calificacion` de V1; sus restricciones ya cubren el puntaje
   (1..5) y la unicidad por reserva.
2. **Modulo `engagement` hexagonal** (dominio `Calificacion` + puertos + servicio + adaptador de
   persistencia con SQL nativo, ADR-0012), igual que el resto de slices.
3. **Solo el titular y solo una reserva ya usada:** el `sub` del JWT identifica al usuario (nunca el
   cuerpo); se exige `FINALIZADA` (la cierra el job de ADR-0023), `403` si la reserva es ajena y `409`
   si aun no finaliza o si ya tiene calificacion. El comentario es opcional (<= 500).
4. **RN-04:** comprobacion previa (`existeParaReserva`) para responder `409` limpio y
   `UNIQUE(id_reserva)` como garantia final ante carreras. *Limitacion conocida:* no hay mapper de
   violacion de unicidad, por lo que dos peticiones simultaneas del mismo titular podrian devolver
   `500` a la perdedora; queda anotado como pendiente.
5. **Lectura publica por cancha:** `GET /api/v1/canchas/{id}/calificaciones?limite=` devuelve total,
   promedio (`AVG`) y las mas recientes; como `calificacion` cuelga de `reserva`, se resuelve con
   `JOIN reserva` por `id_cancha`. `limite` se acota a 1..100 (por defecto 20) y una cancha sin
   calificaciones devuelve `total=0` y `promedio=0`; una cancha inexistente, `404`.
6. **`fecha_registro` como literal UTC explicito** (ADR-0014): el `DEFAULT CURRENT_TIMESTAMP` de la
   columna seria la hora local del servidor (L-04) y no se releeria como el mismo instante.

**Consecuencias:** RN-04 y las dos CU quedan cubiertas; el catalogo ya puede mostrar reputacion.
Reclamos y notificaciones de F06 siguen pendientes.

**Evidencia:** `CalificacionTest` (7 tests: alta + listado publico con promedio, reserva no finalizada
`409`, doble calificacion `409`, reserva ajena `403`, puntaje fuera de rango `400`, reserva/cancha
inexistente `404`, promedio de varias) + `mvnw test` 96/96 y humo 69/69 en ambos scripts
(`docs/RESULTADOS_PRUEBAS.md` E-25).

## ADR-0025 - Reclamos de soporte y avisos in-app (modulo `engagement`)

**Contexto:** F06 pide soporte y avisos; las tablas `reclamo`, `reclamo_seguimiento` y
`notificacion` existen desde V1/V3 pero **nada las usaba**. Un reclamo puede referirse a una
reserva concreta o ser una consulta general, y el usuario necesita ver el estado y conversar;
los cambios de estado y la respuesta de la administracion pertenecen a F07.

**Decision:**
1. **Sin migracion:** se usan las tablas existentes; `reclamo.estado` ya tiene el CHECK
   `ABIERTO/EN_REVISION/RESUELTO/CERRADO` y `reclamo_seguimiento` ya modela la conversacion.
2. **Reclamos del usuario** (`ReclamoService`, `ReclamosUseCase`): `POST /api/v1/reclamos`
   (asunto <= 150, descripcion <= 800; `idReserva` opcional y, si viene, debe ser del titular),
   `GET /api/v1/reclamos/mios`, `GET /api/v1/reclamos/{id}` (con seguimientos) y
   `POST /api/v1/reclamos/{id}/seguimientos`. El `sub` del JWT manda: un reclamo ajeno da `403`
   y un estado `CERRADO` ya no admite comentarios (`409`). El alta queda en `ABIERTO`.
3. **Avisos in-app** (`NotificacionService`): `GET /api/v1/notificaciones` devuelve
   `{noLeidas, total, notificaciones}` y `POST /api/v1/notificaciones/{id}/leida` marca el aviso
   propio (`204`; `404` si es ajeno). El listado va de mas reciente a mas antiguo y se acota a
   1..100 (por defecto 20).
4. **Puerto compartido `AvisosUsuario`** (`shared/application/port/out`): reservas y pagos emiten
   el aviso sin depender de `engagement` (evita la dependencia circular). El adaptador de
   `engagement` lo implementa escribiendo en `notificacion` con la fecha como literal UTC
   (ADR-0014, L-04).
5. **Eventos que avisan:** reserva confirmada, rechazada y revocada por el propietario, reserva
   finalizada por el job (ADR-0023) y pago aprobado. No se avisa al titular de su propia
   cancelacion (accion propia) ni se crea un aviso por cada calificacion o reclamo.
6. **Sin servicio de correo:** los avisos son in-app; el envio real sigue fuera de alcance
   (el unico correo simulado es el de recuperacion de clave, `NotificadorCuenta`).

**Consecuencias:** F06 queda cubierto en la parte de usuario. La **moderacion de reclamos**
(cambiar estado, responder como administracion, auditar) queda para F07; mientras tanto un
reclamo permanece `ABIERTO` salvo cambio manual. Ver `L-16`.

**Evidencia:** `ReclamoNotificacionTest` (8 tests) + `mvnw test` 104/104 y humo 75/75 en ambos
scripts (`docs/RESULTADOS_PRUEBAS.md` E-26).

## ADR-0026 - Moderacion de reclamos por `PLATFORM_ADMIN`

**Contexto:** el slice de reclamos (ADR-0025) solo cubria la cara del usuario: todo reclamo
quedaba `ABIERTO` y nadie podia responder oficialmente (`L-16`), pese a que el rol
`PLATFORM_ADMIN` existe desde V4 y el JWT ya emite `groups` con los roles del usuario
(`SmallryeJwtEmisorTokens`).

**Decision:**
1. **Autorizacion por rol del token:** los endpoints viven en
   `engagement/adapter/in/rest/AdminReclamoResource` bajo `/api/v1/admin/reclamos` y se
   protegen con `@RolesAllowed("PLATFORM_ADMIN")`; un token `USER` recibe `403`. El rol se
   resuelve del claim `groups`, por lo que promover a un usuario requiere volver a iniciar
   sesion (el token vigente conserva sus roles).
2. **Sin migracion:** se reutilizan `reclamo` y `reclamo_seguimiento`; el estado ya tiene el
   CHECK de V1.
3. **Transiciones explicitas:** `ABIERTO -> EN_REVISION|RESUELTO|CERRADO`,
   `EN_REVISION -> RESUELTO|CERRADO`, `RESUELTO -> EN_REVISION|CERRADO`, `CERRADO` terminal.
   Repetir el estado actual o saltar desde `CERRADO` es `409`; un estado desconocido, `400`.
4. **Cambio condicional al estado leido:** `UPDATE ... WHERE id_reclamo = :id AND estado = :esperado`;
   si otra moderacion gano la carrera, `409` en lugar de pisar la decision.
5. **Avisos al titular:** cada cambio de estado emite "Reclamo actualizado" y cada respuesta
   oficial "Respuesta a tu reclamo", por el puerto compartido `AvisosUsuario` (ADR-0025).
6. **Comentario opcional al cambiar estado:** si viene, se guarda como seguimiento con el
   `id_usuario` del administrador, de modo que la conversacion muestre quien respondio.
7. **Listado y detalle:** `GET /api/v1/admin/reclamos?estado=&limite=` (50 por defecto, maximo
   200) devuelve todos los reclamos con `idUsuario`; `GET /api/v1/admin/reclamos/{id}` incluye
   la conversacion completa. `POST .../seguimientos` responde `409` si el reclamo esta cerrado.

**Consecuencias:** `L-16` queda resuelta y F06 completo en su parte funcional. La administracion
de usuarios/establecimientos y la auditoria global siguen siendo F07.

**Evidencia:** `ModeracionReclamoTest` (5 tests: `403` para `USER`, listado y filtros, cambio de
estado con aviso, transiciones invalidas, respuesta oficial) + `mvnw test` 109/109 y humo 81/81
en ambos scripts (`docs/RESULTADOS_PRUEBAS.md` E-27).

## ADR-0027 - Administracion de plataforma: propietarios, supervision y auditoria

**Contexto:** con la moderacion de reclamos (ADR-0026) existia ya el rol `PLATFORM_ADMIN`, pero
la administracion seguia sin poder validar propietarios, supervisar la operacion ni dejar traza
de sus acciones, aunque el esquema ya tenia `propietario.estado_validacion` y `auditoria` (V1).

**Decision:**
1. **Modulo `platform`** (ya previsto en ARQUITECTURA) con su propio dominio, puertos y
   adaptadores; todos sus endpoints viven bajo `/api/v1/admin/*` y exigen
   `@RolesAllowed("PLATFORM_ADMIN")`.
2. **Validacion de propietarios (CU06.2):**
   `GET /api/v1/admin/propietarios?estado=&limite=` y
   `POST /api/v1/admin/propietarios/{id}/validacion`. Transiciones:
   `PENDIENTE -> VALIDADO|RECHAZADO`, `RECHAZADO -> VALIDADO`, `VALIDADO -> RECHAZADO`;
   repetir el estado o saltar desde otro es `409`. Rechazar exige `motivo` (`400` si falta).
   El cambio es condicional al estado leido (`409` si otra accion gano) y avisa al propietario
   ("Propietario validado" / "Validacion rechazada" con el motivo).
3. **Supervision de solo lectura (CU06.3/CU06.4):**
   `GET /api/v1/admin/establecimientos` (propietario, distrito y numero de canchas),
   `GET /api/v1/admin/reservas` (cancha, establecimiento, titular, montos y estado) y
   `GET /api/v1/admin/pagos` (con el total reembolsado por subconsulta). Filtro opcional por
   estado y limite 50 por defecto (maximo 200). No se anaden acciones de escritura sobre estos
   recursos en este slice.
4. **Auditoria (CU06.7):** puerto compartido `RegistroAuditoria` en
   `shared/application/port/out`, implementado por el adaptador de `platform` sobre la tabla
   `auditoria` con `fecha_evento` como literal UTC (ADR-0014, L-04). Se auditan las acciones
   administrativas: validacion de propietarios (`PROPIETARIO_VALIDADO`/`PROPIETARIO_RECHAZADO`)
   y moderacion de reclamos (`RECLAMO_<ESTADO>`/`RECLAMO_RESPUESTA`, retrofit en ADR-0026).
   `GET /api/v1/admin/auditoria?limite=` devuelve las mas recientes.
5. **Sin migracion:** se reutilizan `propietario` y `auditoria` de V1.
6. **Fuera de alcance:** gestion de usuarios/roles (CU06.1) y administracion de cocheras/
   promociones (CU06.5); ver `L-18`.

**Consecuencias:** F07 queda **PARCIAL**: validacion, supervision, moderacion de reclamos y
auditoria operativas; la gestion de cuentas y roles sigue pendiente. La auditoria es de las
acciones administrativas, no un traza de todas las peticiones.

**Evidencia:** `AdminPlataformaTest` (4 tests: `403` para `USER`, validacion con aviso y
auditoria, rechazo con motivo y revalidacion, supervision de establecimientos/reservas/pagos) +
`mvnw test` 113/113 y humo 87/87 en ambos scripts (`docs/RESULTADOS_PRUEBAS.md` E-28).

## ADR-0028 - Gestion de usuarios, roles y estado de cuenta (CU06.1)

**Contexto:** la administracion ya validaba propietarios y moderaba reclamos (ADR-0027), pero no
podia consultar cuentas, asignar roles ni desactivarlas sin SQL manual (`L-18`). El rol
`PLATFORM_ADMIN` viaja en el claim `groups` del JWT y el login ya rechaza cuentas no activas
(`AutenticarService`), asi que desactivar una cuenta por API tiene efecto real.

**Decision:**
1. **Endpoints** bajo `/api/v1/admin/usuarios` con `@RolesAllowed("PLATFORM_ADMIN")`:
   `GET /?correo=&estado=&rol=&limite=` (correo por coincidencia parcial, `GROUP_CONCAT` de
   roles), `GET /{id}`, `POST /{id}/roles` y `POST /{id}/estado`.
2. **Roles:** se reemplaza el conjunto completo (`USER`, `OWNER`, `PLATFORM_ADMIN`); vacio o
   desconocido es `400`. Un usuario con `PLATFORM_ADMIN` no puede quedarse sin ese rol si es el
   **ultimo administrador activo** (`409`); mismo criterio al desactivar/bloquear.
3. **Estado:** `ACTIVO`, `INACTIVO` o `BLOQUEADO`; repetir el estado actual es `409` y el cambio
   es condicional al estado leido. El login (`AutenticarService`) ya exige `estaActivo()`.
4. **Auditoria y aviso:** `USUARIO_ROLES_CAMBIADOS` / `USUARIO_ESTADO_CAMBIADO` en `auditoria`
   (via `RegistroAuditoria`) y aviso in-app al afectado ("Tus roles cambiaron", "Cuenta
   activada/bloqueada"). El aviso de bloqueo se conserva aunque no pueda iniciar sesion.
5. **Vigencia del token:** los cambios de rol o estado **no revocan** los tokens ya emitidos; el
   usuario debe volver a iniciar sesion y un token vigente conserva sus roles hasta expirar
   (1800 s). Ver `L-20`.
6. **Sin migracion:** se usan `usuario`, `rol` y `usuario_rol` de V1.

**Consecuencias:** F07 queda solo a falta de la administracion de cocheras/promociones (CU06.5) y
del catalogo avanzado. La gestion de cuentas ya es auditable y no puede dejar la plataforma sin
administradores activos.

**Evidencia:** `AdminUsuariosTest` (4 tests: `403` para `USER`, listado/filtros/detalle, cambio de
roles con aviso + auditoria y re-login, desactivacion con login `403` y reactivacion) +
`mvnw test` 117/117 y humo 91/91 en ambos scripts (`docs/RESULTADOS_PRUEBAS.md` E-29).

## ADR-0029 - Administracion de cocheras y promociones (CU06.5)

**Contexto:** F07 pedia cerrar la administracion de cocheras y promociones. Las tablas
`cochera`, `promocion` y `cancha_promocion` existen desde V1 (con CHECKs de estado, tipo, valor y
fechas), pero solo se usaban las cocheras del propietario; no habia catalogo de promociones.

**Decision:**
1. **Cocheras (supervision y estado):** `GET /api/v1/admin/cocheras?estado=&limite=` (con su
   establecimiento) y `POST /{id}/estado` (`ACTIVA`/`INACTIVA`, condicional, `409` si ya esta en
   ese estado o si otra accion gano). El propietario sigue creando/eliminando sus cocheras; el
   admin supervisa y puede desactivarlas.
2. **Promociones (catalogo de plataforma):** `GET /api/v1/admin/promociones?estado=&limite=` y
   `POST /api/v1/admin/promociones` con `codigo` unico (1..30, `[A-Z0-9_-]`), `descripcion`,
   `tipoDescuento` (`PORCENTAJE`/`MONTO`), `valor > 0` (porcentaje <= 100), `fechaInicio <=
   fechaFin` y `canchas` opcionales (deben existir). Una cancha puede colgar de varias promociones
   (`cancha_promocion`).
3. **Activacion:** `POST /{id}/estado` alterna `ACTIVA`/`INACTIVA` con el mismo criterio condicional.
4. **Auditoria:** `PROMOCION_CREADA`, `PROMOCION_<ESTADO>` y `COCHERA_<ESTADO>` via
   `RegistroAuditoria`; no se emiten avisos a usuarios (es mantenimiento de catalogo).
5. **Sin migracion:** se reutilizan las tablas de V1.
6. **Alcance:** las promociones son **catalogo**; la reserva aun no las aplica al precio
   (`monto_descuento` existe pero nadie lo calcula) y el propietario no tiene aun su propio alta de
   promociones (CU04.6). Ver `L-21`.

**Consecuencias:** F07 queda completo (CU06.1-CU06.7). Quedan como siguiente bloque el catalogo
avanzado, el alta de promociones del propietario con aplicacion al precio y el frontend.

**Evidencia:** `AdminCocherasPromocionesTest` (4 tests: `403` para `USER`, alta/listado/baja de
promocion con auditoria, validaciones del alta y supervision/baja de cochera) + `mvnw test`
121/121 y humo 96/96 en ambos scripts (`docs/RESULTADOS_PRUEBAS.md` E-30).

## ADR-0030 - Promociones del propietario aplicadas a la reserva (CU04.6)

**Contexto:** el catalogo de promociones ya existia (ADR-0029) pero solo lo gestionaba la
plataforma y **no afectaba al precio** (`L-21`): `reserva.monto_descuento` siempre era 0 y el cobro
ignoraba la promocion.

**Decision:**
1. **Alta por propietario:** `POST/GET /api/v1/establecimientos/{id}/promociones` y
   `POST /{id}/estado`, con `@Authenticated` y pertenencia resuelta contra
   `establecimiento.id_propietario -> propietario.id_usuario` (`403` si no es suyo). El alta asocia
   la promocion a **todas las canchas del establecimiento** (`cancha_promocion`); sin canchas, `409`.
   Se reutiliza la validacion y el codigo unico del catalogo (ADR-0029).
2. **Reserva con codigo:** `POST /api/v1/reservas` acepta `codigoPromocion` opcional. El puerto
   compartido `PromocionesCancha` (implementado en `platform`) resuelve la promocion activa asociada
   a la cancha. Si no existe, esta inactiva, fuera de fechas o no aplica, la reserva falla con `409`
   y el usuario puede reservar sin codigo.
3. **Fecha de evaluacion:** la vigencia se comprueba contra la **fecha de la reserva** en
   `America/Lima`, no contra el dia actual; asi una promocion que empieza manana ya puede usarse en
   una reserva de la semana siguiente. Es el criterio documentado en E-31.
4. **Calculo:** `PORCENTAJE` -> `precio * valor / 100` (HALF_UP a 2 decimales); `MONTO` -> valor
   fijo. El descuento se limita al precio de la cancha y se guarda en `reserva.monto_descuento`.
   No afecta a la cochera.
5. **Cobro y liquidacion:** sin cambios de codigo: `PagoSimuladoService` ya cobraba
   `monto_cancha - monto_descuento + cochera` y la liquidacion toma el `monto` del pago, por lo que
   ambos quedan alineados automaticamente.
6. **Sin migracion:** se usan `promocion`, `cancha_promocion` y `reserva.monto_descuento` de V1.

**Consecuencias:** `L-21` queda resuelta y CU04.6 completo. El propietario puede crear y apagar sus
promociones; la plataforma conserva la supervision (ADR-0029). No hay validacion de solapamiento
entre promociones: si dos codigos aplican, el usuario elige uno.

**Evidencia:** `PromocionReservaTest` (4 tests: alta/listado y `403` ajeno, 10% sobre 80 con pago de
72, tope del descuento fijo al precio, y `409` para codigo inexistente/inactivo/de otra cancha) +
`mvnw test` 125/125 y humo 98/98 en ambos scripts (`docs/RESULTADOS_PRUEBAS.md` E-31).

## ADR-0031 - Catalogo avanzado: ubicaciones, filtros y ficha de cancha

**Contexto:** F02/F03 pedian buscar canchas por algo mas que el distrito, exponer el catalogo
geografico (`L-10`) y una ficha con fotos y atributos. Las tablas `departamento`, `provincia`,
`distrito`, `deporte`, `caracteristica`, `cancha_deporte`, `cancha_caracteristica` y `foto_cancha`
existen desde V1/V2 y los catalogos estan sembrados en V4, pero no habia endpoints.

**Decision:**
1. **Catalogos publicos:** `GET /api/v1/ubicaciones/distritos?q=&limite=` (distrito con provincia y
   departamento, resuelve `L-10`), `GET /api/v1/deportes` y `GET /api/v1/caracteristicas`.
2. **Filtros del listado:** `GET /api/v1/canchas` acepta `idDistrito`, `idDeporte`, `precioMax` y
   `q` (nombre de cancha o de establecimiento, `LIKE`); se mantiene el `limite` 1..100.
3. **Ficha publica:** `GET /api/v1/canchas/{id}` devuelve cancha, establecimiento, direccion,
   distrito con provincia/departamento, deportes, caracteristicas (con `valor`), fotos ordenadas y
   reputacion (`COUNT` + `AVG` de calificaciones). Una cancha sin datos no falla: listas vacias y
   `reputacion.total = 0`.
4. **Gestion por el propietario:** `PUT /canchas/{id}/deportes`, `PUT /canchas/{id}/caracteristicas`
   (reemplazo completo; lista vacia limpia; ids inexistentes `400`) y `POST/DELETE
   /canchas/{id}/fotos` (orden unico 1..99, `409` si se repite; `principal` desmarca las demas).
   La pertenencia se valida con `VenuesRepositorio.esPropietarioDeCancha` (`403` si es ajena).
5. **Ruta:** la ficha y sus atributos viven en `CanchaDetalleResource`
   (`@Path("/api/v1/canchas/{idCancha}")`), no en el recurso raiz `/api/v1`, porque RESTEasy sombrea
   ese prefijo con el recurso mas especifico de agenda (misma limitacion que obligo a separar
   calificaciones, ADR-0024).
6. **Sin migracion:** todo el modelo ya existia; las fotos son URLs externas (no hay subida de
   archivos).

**Consecuencias:** F02 queda a falta de "comparar canchas"; F03 a falta de edicion/baja de
establecimiento/cancha. El frontend ya puede poblar filtros y pintar la ficha.

**Evidencia:** `CatalogoCanchaTest` (4 tests: catalogos publicos, ficha con deportes/
caracteristicas/foto y `403` ajeno, filtros por deporte/precio/texto, validaciones de atributos y
fotos) + `mvnw test` 129/129 y humo 104/104 en ambos scripts (`docs/RESULTADOS_PRUEBAS.md` E-32).

## ADR-0032 - Edicion y baja logica de establecimientos y canchas, y comparacion de canchas

**Contexto:** CU04.1/CU04.2 solo tenian el alta; no habia forma de corregir datos ni de retirar
un establecimiento o una cancha del catalogo, y CU02.2 (comparar canchas) no tenia endpoint.

**Decision:**
1. `PUT /api/v1/establecimientos/{id}` (idDistrito, nombre, direccion, referencia) y
   `PUT /api/v1/canchas/{id}` (nombre, descripcion, capacidad, precioBase) con ownership por
   `propietario.id_usuario`: `404` si no existe, `403` si es ajeno, `409` si el nombre de
   cancha se repite en el establecimiento (`UNIQUE`) y `400` en validaciones. El estado no se
   edita por esta via.
2. **Baja logica:** `DELETE /api/v1/establecimientos/{id}` (estado `INACTIVO` y todas sus
   canchas `INACTIVA`) y `DELETE /api/v1/canchas/{id}` (`INACTIVA`). `204` al aplicarla y
   `404` si ya estaba de baja. Se rechaza con `409` si hay reservas `PENDIENTE`/`CONFIRMADA`
   cuya franja aun no termino (`fin > UTC_TIMESTAMP(6)`), para no dejar a un cliente con una
   reserva de una cancha retirada.
3. El listado publico filtra `cancha.estado = 'ACTIVA' AND establecimiento.estado = 'ACTIVO'`; la
   ficha sigue accesible (devuelve `estado`) para que el propietario vea lo dado de baja. Las
   reservas nuevas ya rechazaban canchas no `ACTIVA` (RN-01) y la disponibilidad devuelve cero
   franjas.
4. **Comparar canchas (CU02.2):** `GET /api/v1/canchas/comparar?ids=1,2` devuelve la ficha de 2 a
   4 canchas en el orden pedido; `400` si la seleccion no es valida (cantidad, ids no numericos o
   repetidos) y `404` si alguna no existe. Se publica como recurso propio porque la ruta literal
   gana a `/api/v1/canchas/{idCancha}` en el matching de JAX-RS.
5. **Sin migracion:** `establecimiento.estado` y `cancha.estado` ya existian en V1.

**Consecuencias:** F02/F03 quedan completos (comparar, editar y dar de baja). La baja solo se
revierte con SQL: no hay endpoint de reactivacion (no esta en el alcance). Las reservas historicas y
sus importes se conservan.

**Evidencia:** `EdicionYBajaVenuesTest` (6 tests) + `mvnw test` 135/135 y humo 112/112 en ambos
scripts (`docs/RESULTADOS_PRUEBAS.md` E-33).

## ADR-0033 - Cierre de identidad: verificacion de correo, perfil, propietario y revocacion

**Contexto:** F01 quedaba incompleta (CU01.2 verificacion, CU01.5 perfil, CU01.6 solicitud de
propietario) y L-20/S-4/S-5 seguian abiertas.

**Decision:**
1. **Verificacion de correo (CU01.2):** columna usuario.correo_verificado; el registro emite un
   token `EMAIL_VERIFY` (24 h, solo hash) y lo envia por el notificador simulado.
   `POST /api/v1/auth/email/verify` lo consume y `POST /api/v1/auth/email/resend` lo reemite sin
   revelar si la cuenta existe. El login exige verificacion solo con
   `cd.auth.requerir-correo-verificado=true` (off por defecto porque el envio es simulado y el
   demo/smoke se registra y opera de inmediato).
2. **Perfil (CU01.5):** `PUT /api/v1/users/me` actualiza nombres, apellidos y telefono validando
   el agregado; `GET /api/v1/users/me` devuelve ademas esos campos y `correoVerificado`.
3. **Solicitud de propietario (CU01.6):** `POST /api/v1/propietarios/solicitudes` crea
   `propietario` en `PENDIENTE` (idempotente por usuario y documento; `409` si otro usuario
   ya usa el documento) y `GET /api/v1/propietarios/me` consulta su estado. La validacion sigue
   en CU06.2.
4. **S-4:** limite de 5 intentos fallidos por correo con bloqueo de 5 minutos, en memoria y por
   instancia (`LimitadorIntentosLogin`), `429 TOO_MANY_ATTEMPTS`.
5. **L-20:** `usuario.token_version` viaja como claim `ver` del JWT; un `ContainerRequestFilter`
   posterior a la autenticacion compara el claim con la base y rechaza con `401`. Incrementar la
   version al cambiar roles, estado o contrasena invalida los access tokens ya emitidos sin depender
   de la precision de `iat`.

**Consecuencias:** F01 queda cerrada. El contador de intentos y el envio de correo son adecuados
para un unico nodo y el alcance del curso; en produccion se sustituyen por un almacen compartido y
un proveedor SMTP. La revocacion anade una lectura por request autenticado.

**Evidencia:** `PerfilVerificacionPropietarioTest` (4), `EndurecimientoAccesoTest` (3),
`AdminUsuariosTest` y `RecuperacionPasswordTest`; suite 144/144 y humo 112/112.

## ADR-0034 - Idempotencia y reprogramacion de reservas

**Contexto:** CU03.2 estaba validada pero sin clave de idempotencia; CU03.7 (modificacion) no
existia.

**Decision:**
1. `POST /api/v1/reservas` acepta `Idempotency-Key`. Se persiste en
   `reserva.clave_idempotencia` con `UNIQUE (id_usuario, clave_idempotencia)`; repetir la clave
   devuelve la reserva ya creada (mismo `id`) y una carrera entre dos peticiones se resuelve
   releyendo la reserva ganadora. Sin cabecera el comportamiento es el anterior.
2. `POST /api/v1/reservas/{id}/modificar` reprograma una reserva propia `PENDIENTE` o
   `CONFIRMADA` a una franja futura **de la misma duracion**, conservando importes congelados.
   Revalida anticipacion, cancha activa, solapamientos, bloqueos y cupo de cochera con el mismo
   orden de locking que la creacion (cancha -> cochera -> reserva). Cambiar la duracion devuelve
   `400` y otro titular `403`.
3. `historial_reserva` registra la reprogramacion.

**Consecuencias:** CU03.2/CU03.7 cerradas. Cambiar la duracion requeriria recalcular importes y
descuentos (extension futura). La reprogramacion no mueve pagos: el pago simulado sigue asociado a
la reserva.

**Evidencia:** `IdempotenciaModificacionTest` (2 tests); suite 144/144 y humo 112/112.

## ADR-0035 - Arquitectura del frontend y sistema de diseno

**Contexto:** Fase 4 debe construir una UI profesional, responsive y accesible sobre los contratos
reales del backend, con identidad visual propia y posibilidad de cambiar de paleta sin reescribir
componentes.

**Decision:**
1. **Angular 22.2.3 standalone**, TypeScript strict, SCSS, lazy loading por area, signals para
   estado de UI y RxJS para HTTP. Sin NgRx: stores signal por feature mas `AuthStore` y
   `ThemeStore` globales.
2. **Componentes propios sobre Angular CDK** (overlay, dialog, listbox, a11y). No se adopta
   Angular Material como capa visual; CDK aporta solo comportamiento y accesibilidad.
3. **Design tokens en tres capas** (primitivos -> semanticos -> componente) y paletas
   intercambiables via `[data-palette]` + variables CSS. Paleta inicial navy/turquesa; el
   `ThemeService` persiste la eleccion. Los componentes no conocen colores literales.
4. **Sesion:** access token en memoria y refresh token en `sessionStorage` con rotacion; al
   recargar se restaura con `POST /auth/refresh`. Un `401` limpia la sesion; nunca se guarda el
   access token en `localStorage`.
5. **HTTP tipado:** un servicio por modulo con DTOs espejo de OpenAPI; interceptors de auth
   (refresh unico), errores (mapeo `code` -> mensaje ES) y loading. Sin logica de negocio en el
   cliente: montos y estados se muestran, no se recalculan como fuente de verdad.
6. **UX:** mobile-first (360/390/768/1024/1440), estados loading/empty/error/success en todas las
   areas, aviso permanente de pago de demostracion, PEN y `America/Lima` en el borde UI,
   WCAG 2.2 AA. Imagenes locales/placeholder, sin fotos remotas ni marcas de terceros.
7. **Entrega Docker:** proyecto CLI `canchadirecta-frontend`; Nginx sirve
   `dist/canchadirecta-frontend/browser` y hace proxy `/api` -> `backend:8080`.

**Consecuencias:** mas trabajo inicial en componentes que usar Material, pero control visual total y
paletas conmutables sin tocar features. La sesion en `sessionStorage` sobrevive a recarga pero no a
cerrar la pestana; el access token nunca queda persistido. Detalle de pantallas, componentes y flujos
en `docs/UX_FLOWS.md`.

**Evidencia:** implementado y verificado en `frontend/` (Angular 22.2.3, CDK, tokens/paletas,
auth con refresh unico, guards, catalogo, reservas/pago demo, panel del propietario y admin).
`npm run build` OK, `npm test` 15/15 y E2E Playwright OK en dev y en el contenedor Nginx
(`localhost:8083`): flujo de usuario y flujo propietario/admin (validacion + rol OWNER,
establecimiento, cancha, fotos, cochera, agenda, promociones, supervision y auditoria).
Capturas en `docs/evidencias/ui/`. Se agrego `LOCALE_ID='es-PE'`, textos con tildes/ñ y
paginacion numerica del catalogo (12 por pagina) con filtro
`GET /canchas?idEstablecimiento=&desplazamiento=` y total en `X-Total-Count`.
## ADR-0036 - Calendario semanal y ventana de reserva de 365 dias

**Contexto:** El detalle de cancha solo permitia elegir una fecha individual y el backend limitaba
la consulta de disponibilidad a 7 dias. Se requiere ver la disponibilidad de la semana, navegar
entre semanas y consultar meses futuros, como en el mockup de horarios.

**Decision:**
1. El detalle muestra un **calendario semanal** (lunes a domingo) con navegacion anterior/siguiente,
   salto directo a un mes y una grilla **hora x dia** con precio por franja; al elegir una celda se
   actualiza el resumen y el boton de reserva.
2. La semana se carga con una consulta de disponibilidad por dia (7 en paralelo). Los dias pasados
   no se consultan y quedan deshabilitados.
3. La ventana de anticipacion sube de **7 a 365 dias** (`cd.reservas.dias-anticipacion-max`), tanto
   para consultar disponibilidad como para crear reservas. El frontend refleja el limite en el
   selector de mes y deshabilita la navegacion mas alla de la ultima semana.
4. El calendario usa la zona `America/Lima` y etiquetas en espanol; los montos conservan `es-PE`.

**Consecuencias:** se puede reservar con hasta un ano de anticipacion y explorar meses completos sin
cambiar el contrato REST (solo cambia el limite de negocio). Cada cambio de semana genera 7
peticiones pequenas; aceptable para el volumen actual. Si el negocio requiere otra ventana, basta
ajustar la propiedad y `APP_CONFIG.diasAnticipacionMax`.

**Evidencia:** E2E de usuario valida calendario de 7 dias, navegacion de semana y seleccion de
franja; backend 144/144 y smoke 112/112 (2026-10-17). El detalle se rediseno con galeria
(fallback sin fotos), bloques de informacion, reserva sticky a la derecha y calendario en la
columna principal; en movil la reserva aparece antes del contenido. Capturas en
`docs/evidencias/ui/detalle-calendario-*.png`.

## ADR-0037 - Configuracion persistente de la plataforma

**Contexto:** La comision, las ventanas de cancelacion y la anticipacion maxima vivian en
`application.properties` (`@ConfigProperty`) y solo cambiaban reiniciando el backend. El delta
del standalone pide una pantalla de configuracion administrable (Bloque C).

**Decision:**
1. Migracion `V17` crea `configuracion_plataforma` (clave/valor + descripcion + fecha de
   actualizacion) y siembra cinco claves: `comision_porcentaje`, `horas_cancelacion_gratis`,
   `horas_minimas_cancelacion`, `dias_anticipacion_max` y `correo_soporte`.
2. El puerto compartido `ConfiguracionPlataforma` expone los valores tipados y lo implementa
   `ConfiguracionPlataformaPersistenciaAdapter` (SQL nativo, ADR-0012). Los modulos de reservas,
   pagos y liquidaciones lo leen **en cada operacion**, por lo que un cambio rige sin reiniciar.
   Si una fila faltara, se usa el valor por defecto historico.
3. `GET/PUT /api/v1/admin/configuracion` (solo `PLATFORM_ADMIN`) consulta y reemplaza los cinco
   valores. El servicio valida rangos (comision 0..100, horas 0..720, dias 1..365, correo con
   formato valido) y registra `CONFIGURACION_ACTUALIZADA` en auditoria.
4. El frontend agrega `/admin/configuracion` con formulario reactivo, estado de carga/error y
   confirmacion por toast.

**Consecuencias:** las reglas de negocio dejan de requerir despliegue. `APP_CONFIG` del frontend
conserva 365 dias como valor inicial; si la administracion reduce la anticipacion, el backend
rechaza fechas fuera del nuevo rango aunque el calendario siga permitiendo navegar hasta 365
(mejora futura: endpoint publico de configuracion para el frontend). El correo de soporte queda
persistido para futuras superficies publicas.

**Evidencia:** `ConfiguracionPlataformaTest` (consulta, actualizacion, validaciones, `403/401` y
lectura por el puerto compartido), humo con GET/PUT/restauracion, E2E de administracion y
accesibilidad de la nueva ruta.

## ADR-0038 - Rol OPERADOR y portal de recepcion

**Contexto:** El delta del standalone incorpora el trabajo de mostrador: el personal de una sede
necesita consultar su turno, registrar reservas presenciales, cobrar en efectivo o billetera y
reportar incidencias sin recibir los permisos del propietario ni de la plataforma (Bloque D). Hasta
ahora solo existian `USER`, `OWNER` y `PLATFORM_ADMIN`.

**Decision:**
1. Migracion `V18` agrega el rol `OPERADOR` y la tabla `operador_establecimiento`
   (`id_usuario` + `id_establecimiento` como clave, `cargo`, `activo`, `fecha_alta`). El vinculo
   —no el rol suelto— define que sedes opera cada persona.
2. El alcance se resuelve en SQL y no en el cliente: `ADMIN` ve todas las sedes, `OPERADOR` solo
   las asignadas con `activo = 1` y `OWNER` las propias. Toda consulta o accion de recepcion
   vuelve a comprobar el acceso, por lo que quitar una asignacion surte efecto de inmediato. Un
   usuario autenticado sin rol de recepcion recibe `403`.
3. `RecepcionRepositorio`/`RecepcionUseCase` (paquete `reception`, SQL nativo, ADR-0012) exponen
   `GET /api/v1/recepcion/{establecimientos,canchas,resumen,agenda,reservas,clientes,incidencias}`,
   `POST /api/v1/recepcion/reservas` (walk-in), `POST .../reservas/{id}/checkin`,
   `POST .../reservas/{id}/cobro` y `POST /api/v1/recepcion/incidencias`.
4. La reserva presencial reutiliza `ReservasUseCase.crear` (RN-01/RN-03, anticipacion maxima y
   solapamiento) y exige un cliente ya registrado (`idCliente`) mas una cancha activa de una sede
   operada. Descartamos crear cuentas invitado en este bloque.
5. El check-in mueve `CONFIRMADA -> FINALIZADA` y solo se habilita desde 2 horas antes del inicio
   hasta el fin de la franja; la reserva presencial nace `PENDIENTE`, como cualquier reserva, y la
   confirma el propietario. El cobro presencial (EFECTIVO/YAPE/PLIN/TARJETA/POS) registra un pago
   `APROBADO` reutilizando `PagoRepositorio`, con un unico pago aprobado por reserva (RN-05).
6. Las incidencias reutilizan la tabla `reclamo` con `estado = 'ABIERTO'`, `id_usuario` = operador
   que reporta y `id_reserva` opcional, de modo que la mesa de ayuda de `PLATFORM_ADMIN` las
   atienda con el flujo existente.
7. La administracion gestiona los vinculos con
   `GET/POST/DELETE /api/v1/admin/establecimientos/{id}/operadores[/{idUsuario}]` (solo
   `PLATFORM_ADMIN`, con auditoria). `AdminPlataformaService.ROLES` incluye `OPERADOR`.
8. El frontend agrega `/admin/operadores` (buscar usuario por correo y vincular a una sede) y el
   portal `/recepcion` con Turno actual, Agenda del dia, Reservas, Reserva presencial, Cobros e
   Incidencias, protegido por `roleGuard(['OPERADOR','OWNER','PLATFORM_ADMIN'])`.

**Consecuencias:** el mostrador opera con privilegios minimos y acotados por sede, sin acceder a
paneles de propietario ni de plataforma. La reserva presencial exige que el cliente ya tenga
cuenta (una reserva telefonica sin usuario registrado no es posible todavia) y la confirmacion
del walk-in sigue dependiendo del propietario porque la creacion reutiliza el flujo estandar de
`ReservasUseCase.crear`. Queda fuera del bloque la gestion de bloqueos desde recepcion.

**Evidencia:** `RecepcionTest` (permiso por sede, agenda, walk-in, check-in idempotente, cobro y
conflictos), humo con vinculacion/desvinculacion y turno completo (153/153 PowerShell, 134/134
Bash), E2E `owner-admin-flows.mjs` (alta de operador desde la UI y recorrido del portal),
accesibilidad de las siete rutas nuevas y capturas `docs/evidencias/ui/recepcion-*.png` y
`admin-operadores-1440.png` (2026-10-18).

## ADR-0039 - Badge de notificaciones sin leer compartido

**Contexto:** `GET /api/v1/notificaciones` ya devolvia `noLeidas`, y `/notificaciones` lo mostraba,
pero la campana de la topbar publica y la de la cabecera de cuenta no indicaban nada: habia que
entrar a la pagina para saber si habia avisos. Cada cabecera vivia en un componente distinto
(`shell` y `cuenta-layout`), asi que un `fetch` por componente habria duplicado peticiones y
quedado desincronizado al marcar leidas.

**Decision:**
1. `NotificacionesStore` (`core/api/notificaciones.store.ts`, `providedIn: 'root'`) expone una senal
   `noLeidas` con `cargar()`, `fijar()`, `quitar()` y `limpiar()`. Es la unica fuente del conteo.
2. El shell sincroniza el badge con la sesion mediante un `effect`: al autenticarse llama a `cargar()`
   (una sola peticion, `limite = 1` porque solo interesa el contador) y al cerrar sesion llama a
   `limpiar()`.
3. Ambas campanas (`shell` y `cuenta-layout`) pintan el mismo badge cuando `noLeidas > 0`, con tope
   visual `9+` y `aria-label` que anuncia el numero ("Notificaciones, N sin leer").
4. `/notificaciones` ya no maneja su propio contador: escribe en el store (`fijar` al cargar,
   `quitar` al marcar una) para que el badge y la pagina nunca diverjan.

**Consecuencias:** una sola peticion alimenta ambos badges y la pagina; el estado sobrevive a la
navegacion dentro de la sesion. El conteo no se refresca solo (ni por polling ni por websocket): se
actualiza al entrar/salir de sesion y al usar la pagina; un aviso generado en otra pestana no se
refleja hasta la proxima carga. Si la peticion falla, el badge conserva el ultimo valor conocido
en vez de romper la UI.

**Evidencia:** E2E manual con dos avisos sembrados para el admin (badge 2 en topbar y en la cabecera
de cuenta; al marcar una pasa a 1 en la misma pagina; al marcar todas desaparece) y suites
`user-flows.mjs`/`owner-admin-flows.mjs` + `a11y-check.mjs` sin regresiones (2026-10-18).

## ADR-0040 - Modo claro/oscuro explicito y persistente

**Contexto:** existian tres paletas (`cancha`, `grafito`, `arena`) y el dark del prototipo
("Nocturno") era solo un acercamiento: no habia un toggle claro/oscuro explicito, no se respetaba la
preferencia del sistema y la eleccion se confundia con la paleta. Ademas, aunque los tokens
existian, buena parte de los componentes de pagina usaba hex fijos, por lo que cualquier tema oscuro
quedaba con tarjetas claras y texto claro.

**Decision:**
1. `ThemeService` incorpora `modo` (`claro | oscuro`) con clave propia `cd.modo`, independiente de
   `cd.palette`. Al arrancar usa la preferencia guardada y, si no hay, `prefers-color-scheme`.
2. Un unico `effect` aplica ambos atributos en `<html>`: `data-palette` y `data-theme='dark'`
   (ausente en claro) mas `color-scheme`, de modo que controles nativos y barras de scroll
   acompanan al tema.
3. `styles/_tokens.scss` declara el juego oscuro al final del archivo (`:root[data-theme='dark']`)
   para ganar a las paletas de igual especificidad: el modo oscuro se aplica **sobre** cualquier
   paleta elegida y tambien corrige la paleta "Nocturno".
4. La interfaz expone el toggle en la topbar publica (boton con `aria-pressed`) y selectores "Modo"
   junto a "Tema" en el menu lateral y en el pie, sin retirar el cambio de paleta.
5. Se migraron a tokens las superficies, bordes y textos con hex fijo de los componentes de pagina
   y del chrome de los cuatro portales (98 reemplazos). Se conservan a proposito los literales
   oscuros del sidebar, el acento lima, los degradados decorativos y el SVG de "Foto no disponible",
   que se leen igual en ambos temas.

**Consecuencias:** el tema es explicito, persistente y estable al recargar, y el diseno queda
realmente parametrizado por tokens (los componentes ya no codifican colores de superficie). Por
diseno, el sidebar de los portales es siempre oscuro, así que el modo claro no es un "todo blanco".
El toggle vive en la topbar publica y el pie: los portales no exponen todavia su propio control.

**Evidencia:** `e2e/dark-mode-capturas.mjs` (aplica cada modo, recorre cinco rutas, comprueba
`data-theme` y la persistencia tras recargar; guarda `docs/evidencias/ui/{claro,oscuro}-*-1440.png`),
build OK, 22/22 unitarios, E2E usuario y propietario/admin OK y a11y 34 rutas OK (2026-10-18).

## ADR-0041 - El calendario publicado se deriva del horario configurado

**Contexto:** la ficha de la cancha mostraba la disponibilidad de dos formas redundantes: la rejilla
"Disponibilidad semanal" (hora x dia, con las franjas libres) y, debajo, un listado plano "Horario
semanal" que repetia dia, rango y precio. Ademas, la rejilla construia sus filas con la union de las
franjas libres de la semana: como el backend omite de la disponibilidad lo ocupado y lo bloqueado
(RN-03), una hora bloqueada no se marcaba como no disponible, **desaparecia de la tabla**, y el
propietario no veia reflejado en el calendario el bloqueo que acababa de registrar.

**Decision:**
1. Se elimina el listado "Horario semanal" de la ficha: el horario configurado se refleja en el
   calendario, que es la unica vista del horario y de la disponibilidad.
2. Las filas de la rejilla se derivan del **horario configurado** (`GET /canchas/{id}/horarios`)
   partido por la duracion elegida (60/90/120). Si la cancha no tiene agenda se mantiene el
   comportamiento anterior (filas a partir de las franjas libres que devuelve el backend, que ya
   aplica la ventana por defecto).
3. Con las filas fijas por horario, una hora ocupada o bloqueada se pinta como "Sin cupo" en lugar
   de desaparecer, asi que un bloqueo del propietario queda visible en el calendario.
4. Se distingue el cierre del dia: si la cancha tiene agenda y ese dia de la semana no esta en ella,
   la columna se rotula "Cerrado" y sus celdas usan un estilo propio. La leyenda pasa a
   Libre / Sin cupo / Cerrado.
5. Las horas de la ficha se formatean en `America/Lima` (helper `hora()`) tambien en el panel de
   reserva y en el resumen de la franja elegida, que antes usaban la zona del navegador.

**Consecuencias:** el calendario es la fuente unica y refleja lo que el propietario configuro y
bloqueo, sin listados duplicados; el dia cerrado se entiende como tal. El backend no cambia: sigue
devolviendo solo franjas libres, por lo que no se puede etiquetar el motivo ("reservada" vs
"bloqueada") y esa distincion queda como mejora posible (L-26). En canchas sin horario configurado
persiste el caso limite de L-27.

**Evidencia:** `frontend/e2e` (script temporal `_bloqueo-check.mjs`) sobre datos reales: cancha nueva
con horario 08:00-15:00 y bloqueo 10:00-11:00; la API pasa de 7 a 6 franjas libres, la rejilla mantiene
las 7 filas con la de 10:00 como "Sin cupo", los dias sin horario se rotulan "Cerrado" y ya no existe
el bloque "Horario semanal". Build OK, 22/22 unitarios, E2E usuario OK, propietario/admin OK y a11y
34 rutas OK (2026-10-18).

## ADR-0042 - Los bloqueos de la cancha los gestiona tambien el personal de la sede

**Contexto:** los bloqueos operativos (RN-03) solo los podia registrar el propietario
(`VenuesRepositorio.esPropietarioDeCancha`). El dia a dia de la sede lo lleva el personal de recepcion
(rol `OPERADOR`, ADR-0038): si una cancha entraba en mantenimiento, el operador tenia que pedirselo al
propietario. Era la limitacion L-25.

**Decision:**
1. Se anade `VenuesRepositorio.esGestorDeCancha(idCancha, idUsuario)`: propietario del establecimiento
   o personal asignado a la sede (`operador_establecimiento` con `activo = 1`, `V18`).
2. `GestionAgendaService` usa ese predicado en los bloqueos (`listarBloqueos`, `registrarBloqueo`,
   `eliminarBloqueo`) y mantiene `esPropietarioDeCancha` en los horarios: el horario y el precio son
   configuracion comercial del propietario, el bloqueo es operacion de la sede.
3. No se crean endpoints nuevos: el portal de recepcion reutiliza `/api/v1/canchas/{id}/bloqueos` con
   el token del operador. La pantalla "Agenda del dia" gana la seccion "Bloqueos de la fecha" (listado
   del dia para toda la sede y alta/baja de franjas).
4. Las horas se muestran con el helper compartido `horaLima()`
   (`frontend/src/app/shared/util/fecha-lima.ts`), que tambien usa la ficha publica.

**Consecuencias:** el personal asignado a la sede bloquea y levanta franjas sin depender del
propietario, y la autorizacion sigue en un unico punto (el caso de uso), sin ruta alternativa que la
evite: un usuario ajeno a la sede y un titular sin asignacion reciben `403`. El modulo `venues` lee la
tabla de asignacion `operador_establecimiento` (introducida por el modulo de recepcion): es una lectura
de datos de la misma base, coherente con los adaptadores nativos (ADR-0012). La reserva presencial sin
cuenta registrada sigue pendiente (L-24).

**Evidencia:** `RecepcionTest` (el operador bloquea y levanta en su sede; `403` al bloquear una cancha
de otra sede, al crear un horario y con un `USER` sin asignacion); E2E propietario/admin (el operador
bloquea 10:00-11:00 desde "Agenda del dia", lo ve listado y lo levanta; evidencia
`docs/evidencias/ui/recepcion-bloqueo-1440.png`); backend 160/160, build OK, 22/22 unitarios, E2E
usuario OK, a11y 34 rutas OK, humo 153/153 PS y 134/134 Bash (2026-10-18).

## ADR-0043 - La ocupacion semanal del propietario se deriva de sus reservas y bloqueos

**Contexto:** el panel del propietario mostraba la agenda como configuracion (horario semanal
recurrente y bloqueos) y las reservas como una bandeja en lista (`/panel/reservas`). Para responder
"que horas de esta semana tengo ocupadas" habia que cruzar a mano la lista de reservas con el
horario, y la unica vista de rejilla existente era la del cliente en la ficha publica (ADR-0041).

**Decision:**
1. `GET /api/v1/reservas/recibidas` acepta tres filtros opcionales mas: `idCancha`, `desde` y `hasta`
   (el rango se aplica por solape, `fin > desde AND inicio < hasta`); `limite` sigue con 50 por
   defecto y tope de 100. La consulta sigue restringida a las canchas del propietario del token
   (subconsulta de propiedad + filtro), de modo que un `idCancha` ajeno devuelve vacio, no `403` con
   datos: no se abre acceso lateral.
2. La pantalla "Agenda" estrena la seccion "Ocupacion de la semana": rejilla de 7 dias por las horas
   del horario configurado (tramos de 60 min), estado por hora (`Libre`, `Reservada`, `Bloqueada`,
   `Ya paso`, `Cerrado` para el dia sin horario y `Fuera de horario`) y navegacion de semana.
3. El estado se calcula en el frontend con las mismas reglas que la disponibilidad publica: ocupan
   cupo las reservas `PENDIENTE`/`CONFIRMADA` y los bloqueos; el tramo que ya termino se rotula
   aparte. No se anade un endpoint de disponibilidad para el propietario ni se duplica la regla en el
   backend.
4. La rejilla es informativa; bloquear y levantar sigue en "Bloqueos y excepciones" (que el personal
   de la sede tambien puede usar, ADR-0042).
5. Los ayudantes de calendario (semana, dias, etiquetas, minutos del dia, fecha y hora de Lima) se
   mueven a `frontend/src/app/shared/util/fecha-lima.ts`; la ficha publica los importa en lugar de
   mantener su copia local.

**Consecuencias:** el propietario ve de un vistazo la ocupacion de sus canchas y detecta huecos y
solapes sin cruzar datos a mano, alimentandose del contrato existente con dos filtros nuevos y sin
tablas ni cache. La semana en Lima se implementa una sola vez y la comparten ficha y panel. La
rejilla trabaja en tramos de 60 min sobre horarios configurados: si la cancha no tiene horarios la
seccion lo indica (L-27/L-28).

**Evidencia:** `ReservasTest` (bandeja filtrada por cancha y por rango: aparece la reserva de la
cancha y del dia pedidos y no la otra; rango sin solape vacio; `desde` mal formado => 400); E2E
propietario/admin (nueva semana con horas "libre" y dias "cerrado", y vuelta a la semana actual;
evidencia `docs/evidencias/ui/panel-agenda-1440.png`); backend 162/162, build OK, 22/22 unitarios,
E2E usuario OK, a11y 34 rutas OK, humo 153/153 PS y 134/134 Bash (2026-10-18).

## ADR-0044 - La bandeja del propietario muestra al titular y su ultimo pago

**Contexto:** `GET /api/v1/reservas/recibidas` devolvia la reserva sin datos del titular: el
propietario veia cancha, fecha, importes y estado, pero no a quien estaba confirmando ni si habia
pagado. Para decidir una solicitud `PENDIENTE` tenia que pedir esos datos por fuera del sistema. El
portal de recepcion ya mostraba titular y estado del pago de su sede (`ReservaRecepcion`) y
`ReservaResponse` documenta a proposito que no expone al titular porque se deduce del token.

**Decision:**
1. El puerto `ReservaRepositorio.listarPorPropietario` devuelve `ReservaRecibida` (la reserva mas
   `cliente`, `telefono` y `pagoEstado`), un read model de la bandeja del propietario. La consulta
   nativa agrega el `JOIN usuario` y una subconsulta con el estado del ultimo pago.
2. `GET /api/v1/reservas/recibidas` responde `ReservaRecibidaResponse`: los mismos campos de
   `ReservaResponse` mas `cliente`, `telefono` y `pagoEstado` (nulo si la reserva no tiene pagos). Se
   mantiene un DTO distinto del de `/reservas/mias` para que los datos de contacto no puedan viajar
   por los endpoints del propio titular.
3. El contrato es aditivo: los filtros (`estado`, `idCancha`, `desde`, `hasta`, `limite`) y el alcance
   por token no cambian. `pagoEstado` es el estado del pago mas reciente (`APROBADO`, `RECHAZADO` o
   `SOLICITADO`), no el de la reserva.
4. El panel muestra "Cliente: nombre" con el telefono como enlace `tel:` (etiqueta accesible
   "Llamar a ...") y un distintivo del ultimo pago (`Pagado`, `Pago rechazado`, `Reembolsado`,
   `Pago pendiente` o `Sin pago`) junto al estado. La respuesta de confirmar/rechazar/revocar sigue
   siendo `ReservaResponse`, asi que la fila conserva los datos del titular al actualizarse.

**Consecuencias:** el propietario atiende con el mismo criterio que el personal de la sede (quien es y
si pago) sin abrir una ruta nueva ni exponer al titular en la vista del cliente. El dato personal se
limita a nombre y telefono, que es lo que ya ve el operador de la sede; el correo no se publica. Se
anade una lectura extra por reserva (estado del ultimo pago) acotada al limite de la bandeja (100).

**Evidencia:** `ReservasTest` (`laBandejaDelPropietarioMuestraAlTitularYSuUltimoPago`: nombre y
telefono del titular, `pagoEstado` nulo antes de pagar y `APROBADO` tras el pago; la suite de la
bandeja sigue en verde); E2E propietario/admin (un cliente reserva y paga por API el lunes siguiente,
la rejilla marca la franja "reservada" y la bandeja muestra "Rosa Cliente ..." con su telefono y
"Pagado"; evidencias `docs/evidencias/ui/panel-agenda-1440.png` y
`docs/evidencias/ui/panel-reservas-1440.png`); backend 162/162, build OK, 22/22 unitarios, E2E usuario
OK, a11y 34 rutas OK, humo 153/153 PS y 134/134 Bash (2026-10-18).

## ADR-0045 - La base de demostracion se regenera con un reset reproducible

**Contexto:** la base local acumulaba miles de filas creadas por el humo y los E2E (mas de 3,000
establecimientos y 3,800 canchas de prueba), asi que el catalogo, la administracion y los KPIs
mostraban datos irrelevantes en la demostracion. Limpiar "a mano" exigia ordenar decenas de claves
foraneas, dejaba los `AUTO_INCREMENT` altos y no era repetible.

**Decision:**
1. `scripts/demo-reset.ps1` centraliza el proceso: copia de seguridad (`mysqldump` en `%TEMP%`),
   `DROP`/`CREATE` de `DB_NAME` con el usuario administrador, reinicio del backend para que Flyway
   migre el esquema y siembra del escenario demo. Exige `-Force` o confirmacion escrita.
2. La siembra usa la API real (registro, propietario, establecimientos, canchas, deportes,
   caracteristicas, fotos, horarios, reservas, pagos, confirmacion, operadores y actividades), no
   INSERT de negocio: el escenario queda coherente con las reglas vigentes (cupos, locking, auditoria)
   y de paso ejercita el contrato completo.
3. Solo tres pasos escapan a la API porque no hay ruta para ellos: crear el primer `PLATFORM_ADMIN`
   (`INSERT` de roles), leer el `id` del distrito y verificar el conteo de tablas.
4. El escenario es fijo para que las suites puedan apoyarse en el: administrador con las credenciales
   de `.env`, propietario validado con 4 sedes y 16 canchas (deportes, caracteristicas, dos fotos y
   horario de 7 dias por cancha), operador vinculado a la sede principal, cliente con una reserva
   `PENDIENTE` pagada y otra `CONFIRMADA`, y una actividad publica. Las cuentas demo usan una clave
   conocida y parametrizable (`-DemoPassword`, por defecto `DemoCancha2026`).
5. Las fotos son marcadores de `picsum.photos`, el mismo criterio que la migracion `V11`.

**Consecuencias:** la demostracion arranca con un catalogo pequeno y coherente (16 canchas, 4 sedes) y
el script se puede repetir cuantas veces haga falta; el respaldo previo permite volver atras. Es
destructivo por diseno y no se ejecuta en produccion: la validacion del nombre de la base y la
confirmacion evitan accidentes sobre otra base del mismo servidor. Correr humo/E2E despues vuelve a
sembrar filas de prueba, asi que el reset se ejecuta al final, antes de exponer la demo.

**Evidencia:** dos ejecuciones consecutivas del script dejaron `usuarios=4 establecimientos=4
canchas=16 horarios=112 fotos=32 reservas=2 actividades=1`; sobre esa base pasaron backend 162/162,
E2E usuario OK (catalogo con 16 canchas y dos paginas), E2E propietario/admin OK, a11y 34 rutas OK y
humo 153/153 PS y 134/134 Bash. Capturas `docs/evidencias/ui/demo-catalogo-1440.png` y
`demo-ficha-1440.png` (2026-10-18).

## ADR-0046 - El tema se controla tambien dentro de los portales

**Contexto:** desde ADR-0040 el modo claro/oscuro se guarda en `cd.modo` y se aplica a toda la
aplicacion, pero el unico control vivia en la topbar publica y en el pie del sitio. Dentro de
`/panel`, `/admin`, `/recepcion` y `/cuenta` (chrome propio, sin topbar publica) no habia forma de
cambiarlo sin salir del portal.

**Decision:**
1. Nuevo componente compartido `CdThemeToggleComponent` (`shared/ui/theme-toggle`): boton de 36 px con
   icono de sol/luna, `aria-pressed` y `aria-label` que anuncia la accion ("Cambiar a modo oscuro" /
   "Cambiar a modo claro"), igual que la topbar publica. Usa el mismo `ThemeService`, asi que la
   preferencia es una sola para el sitio y los portales.
2. Se agrega a la cabecera de los cuatro portales, junto al aviso de notificaciones, sin tocar la
   navegacion ni anadir menus.
3. `PortalIconComponent` gana los iconos `sun` y `moon` con el mismo trazo de linea del resto.
4. Se corrigen los pares de color que el modo oscuro dejaba por debajo de 4.5:1 y que el nuevo control
   volvia alcanzables: `--cd-color-on-danger` para los botones de peligro (antes blanco fijo sobre un
   rojo claro), `--cd-color-on-accent` para el distintivo de acento, el texto neutro de `.cd-badge`, el
   verde de exito y el ambar de aviso de la paleta clara, y las iniciales del avatar (usaban un hex
   fijo que desaparecia en oscuro).
5. Nuevo `frontend/e2e/contraste-modos.mjs`: recorre los cuatro portales en claro y oscuro y falla si
   algun boton, distintivo o accion de cabecera baja de 4.5:1 (mide el color calculado sobre el fondo
   efectivo).

**Consecuencias:** el usuario cambia el tema donde esta trabajando y la preferencia se mantiene al
volver al sitio publico; el estado del boton es perceptible por lector de pantalla sin depender del
icono. El contraste queda verificado de forma automatica en ambos modos, lo que cubre el pendiente de
a11y de contraste para los portales (L-23). El control expone solo el modo claro/oscuro: la paleta
sigue eligiendose desde el menu del sitio publico para no duplicar selectores en cuatro cabeceras.

**Evidencia:** `CdThemeToggleComponent` 2/2 unitarios (alterna, aplica `data-theme` y comparte la
preferencia con `ThemeService`); E2E usuario (la cuenta del cliente cambia a oscuro y vuelve, captura
`docs/evidencias/ui/cuenta-oscuro-1440.png`) y E2E propietario/admin (el panel cambia a oscuro,
persiste tras recargar y vuelve a claro, captura `docs/evidencias/ui/panel-oscuro-1440.png`);
`contraste-modos.mjs` OK en los cuatro portales y ambos modos; build OK, 24/24 unitarios, E2E usuario
OK, E2E propietario/admin OK, a11y 34 rutas OK, humo 153/153 PS y 134/134 Bash (2026-10-18).

## ADR-0047 - La disponibilidad publica informa el motivo de cada tramo ocupado

**Contexto:** la rejilla de la ficha se deriva del horario configurado (ADR-0041), pero toda hora no
libre se rotulaba "Sin cupo": el cliente no sabia si estaba reservada (podria liberarse) o bloqueada
por mantenimiento. `GET /canchas/{id}/disponibilidad` solo devolvia `franjas` libres y la regla de
ocupacion vivia en el SQL de `ocupacionEn`, sin el motivo. Era la limitacion L-26.

**Decision:**
1. `ReservaRepositorio.ocupacionEn` devuelve `List<OcupacionFranja>` (franja + `RESERVADA`/`BLOQUEADA`);
   la consulta nativa agrega una columna constante por rama del `UNION` (reservas vigentes y bloqueos).
   Hay un solo llamador (la disponibilidad), asi que no se mantienen dos firmas.
2. `DisponibilidadDia` y `DisponibilidadResponse` agregan `ocupadas[]` con `{inicio, fin, estado}`. El
   contrato es aditivo: `franjas` sigue igual y el calculo de cupo usa las mismas reglas
   (`PENDIENTE`/`CONFIRMADA` + bloqueos).
3. Solo se expone el motivo: ni el titular, ni el identificador de la reserva, ni la nota del bloqueo.
4. La ficha rotula la celda con "Reservada" (gris) o "Bloqueada" (ambar) y `aria-label` "dia a las
   HH:MM, reservada/bloqueada"; el fallback "Sin cupo" queda para horas fuera del horario. La leyenda
   pasa a Libre/Reservada/Bloqueada/Cerrado y los chips de dia sin agenda dicen "Cerrado" en lugar de
   "0". El emparejamiento es por solape del tramo con la hora de la fila, no por hora de inicio
   exacta, para bloqueos de 90/120 min o desalineados.
5. `demo-reset.ps1` siembra una reserva y un bloqueo para que la demo muestre ambos estados.

**Consecuencias:** el cliente entiende por que no puede reservar y si conviene volver a mirar (reserva)
o buscar otra hora (bloqueo), sin abrir datos personales ni la operacion interna. El coste es una lista
mas por dia consultado (como maximo las reservas y bloqueos de esa cancha) y ningun cambio en el
calculo de cupo. "Cerrado" sigue siendo del frontend: el backend no modela el cierre como ocupacion.

**Evidencia:** `ReservasTest` (`laDisponibilidadDistingueReservaYBloqueo`: la reserva y el bloqueo no se
ofrecen y figuran como `RESERVADA`/`BLOQUEADA`, la hora libre sigue disponible y la nota del bloqueo no
se filtra); humo PS y Bash (la franja reservada/bloqueada no esta en `franjas` y si en `ocupadas` con su
estado); E2E propietario/admin (rejilla del panel con ambas celdas y ficha publica con `aria-label`
"reservada"/"bloqueada", captura `docs/evidencias/ui/ficha-motivos-1440.png`); backend 163/163, build
OK, 24/24 unitarios, E2E usuario OK, a11y 34 rutas OK, contraste OK y humo 153/153 PS y 134/134 Bash
(2026-10-18).

