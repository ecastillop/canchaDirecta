# Pruebas E2E de UI (Playwright + Chrome del sistema)

Estos scripts validan los flujos reales contra backend y base de datos en marcha.
Usan `playwright-core` con el Chrome instalado (no descargan navegadores).

## Prerrequisitos

1. Backend en `http://localhost:8089` y base de datos con datos (el E2E elige una
   franja disponible de los proximos 7 dias).
2. Frontend servido con proxy `/api` al backend:
   - desarrollo: `npm start` (4200, proxy a 8088) y definir `BASE`;
   - con proxy a 8089: `npx ng serve --port 4300 --proxy-config <archivo>`.
3. Chrome instalado (o `CHROME_PATH`).
4. Cuenta con rol `PLATFORM_ADMIN` para el flujo de propietario/admin.

## Ejecucion

```powershell
# Flujo de usuario: registro, login, catalogo, reserva, pago demo, reclamo, responsive
$env:BASE='http://localhost:4300'; npm run test:e2e

# Flujo propietario/admin (requiere admin)
$env:BASE='http://localhost:4300'
$env:ADMIN_CORREO='admin@dominio.com'
$env:ADMIN_PASSWORD='...'
npm run test:e2e:owner
```

Variables: `BASE`, `API` (por defecto `http://localhost:8089/api/v1`), `CHROME_PATH`,
`ADMIN_CORREO`, `ADMIN_PASSWORD`.

Las capturas se guardan en `../docs/evidencias/ui/`. El script falla si hay errores
de consola, respuestas inesperadas >= 400 u overflow horizontal a 390 px.

Nota: crear un administrador exige asignar el rol fuera de la UI (por ejemplo, con
`INSERT` en `usuario_rol`); la validacion de propietarios desde `/admin` ya asigna
automaticamente el rol `OWNER`.
