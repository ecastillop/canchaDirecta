# Estrategia de pruebas

Herramientas: JUnit 5, RestAssured, Mockito (unitarias), Testcontainers MySQL
(integracion con motor real), Playwright (E2E). Resultados reales en
`RESULTADOS_PRUEBAS.md`.

## 1. Unitarias (dominio/aplicacion)
Precio base y por franja, promociones y redondeo (2 dec.), fechas validas, ventanas de
cancelacion, tasa de comision, liquidaciones, expiracion de pendientes, validacion de
capacidad de cochera, politicas de dueno, transiciones de estado, JWT/login.
`Clock` falso determinista.

## 2. Integracion (MySQL real + REST)
- Migraciones desde base vacia; FK invalidas; unicidad de correo (case-insensitive).
- Unicidad parcial adaptada (foto principal; pago aprobado).
- Una sola calificacion por reserva; no doble liquidacion de la misma reserva.
- **Concurrencia:** 20-50 solicitudes paralelas al mismo slot -> exactamente una reserva
  retiene cupo, resto `409`; intervalos contiguos si; cruce parcial no; mantenimiento no.
- Cochera capacidad 1/2/3 por intervalos solapados; pago aprobado/rechazado/expirado;
  doble evento de pago simulado (idempotencia).
- Autorizacion IDOR: OWNER A sobre recursos de B; usuario A ve reserva de B; invitado
  invoca operacion privada; USER obtiene permiso admin -> todas fallan.
- Codigos: 400/401/403/404/409/429.

## 3. E2E (UI + API real + MySQL)
Visitante filtra -> detalle -> registro -> login -> disponibilidad -> reserva -> pago
simulado aprobado -> mis reservas -> cancelacion + aviso. Pago rechazado -> libera cupo ->
segundo usuario reserva. Propietario -> validacion admin -> establecimiento/cancha/
horario -> publica -> usuario reserva -> liquidacion. Promocion, cochera llena/vacia,
bloqueo, modificacion, calificacion, reclamo, notificaciones, recuperacion.
UI desktop/tablet/mobile, teclado, estados carga/vacio/error/success.

## 4. Rendimiento y seguridad
Paginacion de catalogo (sin N+1/escaneos), disponibilidad por cancha/fecha con
indices, dependencias vulnerables, logs sin secretos, CORS/cookies/headers, roles,
inyeccion basica, validacion de uploads.

## 5. Comandos

```powershell
# Backend (desde canchadirecta, con .env cargado)
backend\mvnw.cmd -B -f backend\pom.xml test
powershell -File scripts\smoke.ps1 -BaseUrl http://localhost:8089

# Frontend (requiere Node 24)
cd frontend
npm run build
npm test -- --watch=false
$env:BASE='http://localhost:8083'; npm run test:e2e
$env:BASE='http://localhost:8083'; $env:ADMIN_CORREO=...; $env:ADMIN_PASSWORD=...; npm run test:e2e:owner
$env:BASE='http://localhost:8083'; $env:ADMIN_CORREO=...; $env:ADMIN_PASSWORD=...; npm run test:a11y
# Accesibilidad de lo publico y las pantallas de acceso (sin credenciales)
$env:BASE='http://localhost:8083'; $env:A11Y_SIN_SESION='1'; npm run test:a11y
```

`test:a11y` revisa estructura (un solo `main` y un solo `h1`, jerarquia de encabezados,
nombres accesibles en controles, campos, imagenes, tablas y dialogos), enlace de salto,
recorrido de teclado, foco que pasa al contenido al cambiar de pantalla y capturas. La variante
`A11Y_SIN_SESION=1` cubre ademas `/auth/*`, que el guard de invitado esconde con sesion iniciada.
La revision con lector de pantalla se guia con `docs/ACCESIBILIDAD_MANUAL.md`. Las cuentas por
perfil para pruebas manuales estan en `docs/CUENTAS_DESARROLLO.md` (clave `12345678`; por ejemplo
`elvis@canchadirecta.pe` para la pasada completa).

Testcontainers requiere Docker operativo (Docker ya operativo: migrar los tests de integracion,
ver `L-09`).
