# Pruebas E2E de UI (Playwright + Chrome del sistema)

Estos scripts validan los flujos reales contra backend y base de datos en marcha.
Usan `playwright-core` con el Chrome instalado (no descargan navegadores).

## Prerrequisitos

1. Backend en `http://localhost:8089` y base de datos con datos (el E2E elige una
   franja disponible de los proximos 7 dias). `scripts/demo-reset.ps1 -Force` deja el
   escenario de demostracion (4 sedes, 16 canchas con horarios, operador y reservas),
   que es la base con la que se validan estas suites; el catalogo necesita mas de 12
   canchas para probar la paginacion.
2. Frontend servido con proxy `/api` al backend:
   - desarrollo: `npm start` (4200, proxy a 8088) y definir `BASE`;
   - con proxy a 8089: `npx ng serve --port 4300 --proxy-config <archivo>`.
3. Chrome instalado (o `CHROME_PATH`).
4. Cuenta con rol `PLATFORM_ADMIN` para el flujo de propietario/admin.

## Ejecucion

```powershell
# Flujo de usuario: registro, login, catalogo, reserva, pago demo, reclamo, responsive
$env:BASE='http://localhost:4300'; npm run test:e2e

# Flujo propietario/admin (requiere admin). Incluye el portal de recepcion: el administrador
# vincula al operador y este bloquea y levanta una franja de las canchas de su sede (ADR-0042),
# la rejilla "Ocupacion de la semana" del panel (semana futura con horas libres y dias
# cerrados, y vuelta a la semana actual; ADR-0043) y la bandeja de reservas recibidas, donde
# un cliente creado por API reserva y paga la franja del lunes siguiente: la rejilla la marca
# "reservada" y la bandeja muestra al titular, su telefono y "Pagado" (ADR-0044). Tambien
# cambia el tema desde la cabecera del panel y comprueba que persiste al recargar (ADR-0046),
# bloquea la franja siguiente desde el panel y comprueba que la ficha publica distingue
# "Reservada" de "Bloqueada" (ADR-0047). En recepcion registra un walk-in cobrado en caja y
# comprueba que queda "CONFIRMADA" con "Check-in" disponible (ADR-0048).
$env:BASE='http://localhost:4300'
$env:ADMIN_CORREO='admin@dominio.com'
$env:ADMIN_PASSWORD='...'
npm run test:e2e:owner

# Tema claro/oscuro: capturas y comprobacion de persistencia (requiere admin)
$env:MODO='oscuro'; node e2e/dark-mode-capturas.mjs
$env:MODO='claro';  node e2e/dark-mode-capturas.mjs

# Contraste en ambos modos de los cuatro portales (requiere los datos demo y una cuenta admin)
$env:BASE='http://localhost:4300'
$env:ADMIN_CORREO='admin@dominio.com'
$env:ADMIN_PASSWORD='...'
node e2e/contraste-modos.mjs
```

Variables: `BASE`, `API` (por defecto `http://localhost:8089/api/v1`), `CHROME_PATH`,
`ADMIN_CORREO`, `ADMIN_PASSWORD`, `MODO` (`oscuro` por defecto en `dark-mode-capturas.mjs`) y
`DEMO_PASSWORD` (clave de las cuentas demo, `DemoCancha2026` por defecto; la usa
`contraste-modos.mjs`).

Las capturas se guardan en `../docs/evidencias/ui/`. El script falla si hay errores
de consola, respuestas inesperadas >= 400 u overflow horizontal a 390 px.

Nota: crear un administrador exige asignar el rol fuera de la UI (por ejemplo, con
`INSERT` en `usuario_rol`); la validacion de propietarios desde `/admin` ya asigna
automaticamente el rol `OWNER`.
