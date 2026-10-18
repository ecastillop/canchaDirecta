# CanchaDirecta - Frontend

Angular 22 (standalone, signals, zoneless) + SCSS con tokens. Consume la API
Quarkus en `/api/v1`.

## Requisitos

- Node.js `>=22.22.3` o `>=24.15` (Angular 22 lo exige). En este equipo el Node
  global es 22.13, por lo que se uso un Node 24 portable para instalar/compilar.
- npm 10+.

## Comandos

```powershell
npm install
npm start          # ng serve -> http://localhost:4200 (proxy /api -> 127.0.0.1:8088)
npm run build      # build de produccion -> dist/canchadirecta-frontend/browser
npm test -- --watch=false   # Vitest
npm run test:e2e            # Playwright (usuario); ver e2e/README.md
npm run test:e2e:owner      # Playwright (propietario/admin; requiere ADMIN_CORREO/PASSWORD)
```

`proxy.conf.json` envia `/api` al backend nativo (`localhost:8088`). En Docker,
nginx proxya `/api/` a `backend:8080`.

## Estructura

```text
src/
  styles/                 tokens de diseno, base, utilidades y estilos de directivas
  app/
    core/
      api/                contratos y clientes HTTP por modulo + OwnerStore/Comparacion
      auth/               AuthStore (signals), AuthApi, guards e interceptor
      http/               ApiError (RFC 9457), loading y error interceptor
      theme/              ThemeService: 3 paletas via data-palette
    shared/
      layout/             shell (topbar, drawer movil, footer)
      ui/                 boton/input (directivas), estados, skeleton, toast, confirm (CDK)
    features/
      auth/               login, registro, verificacion, recuperacion
      public/             home, 404, sin acceso
      catalogo/           catalogo, detalle con disponibilidad, comparacion
      reservas/           crear reserva + pago demo, mis reservas, detalle
      cuenta/             perfil + solicitud de propietario, notificaciones, reclamos
      panel/              panel del propietario (resumen, reservas, agenda, promos, liquidaciones)
      admin/              administracion de plataforma (propietarios, reclamos, usuarios, supervision, auditoria)
```

## Decisiones de UI

- Paletas intercambiables (`cancha`, `grafito`, `arena`) aplicadas con
  `data-palette`; los componentes solo consumen variables `--cd-*`.
- El access token vive solo en memoria; el refresh token en `sessionStorage` y
  se rota con refresh de vuelo unico.
- Los formularios usan controles nativos estilizados (`cdInput`) para conservar
  accesibilidad y evitar dependencias de UI.
- El pago es simulado (modo demo) y se avisa en la interfaz.
- Limitacion conocida: el backend no expone el listado de establecimientos
  propios. El panel del propietario permite registrar uno nuevo o indicar su ID
  una vez (se guarda en `localStorage`).

Ver `../docs/UX_FLOWS.md` para flujos y criterios de aceptacion.
