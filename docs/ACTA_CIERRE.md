# Acta de cierre - CanchaDirecta

- **Proyecto:** CanchaDirecta - reserva de canchas deportivas en Lima.
- **Curso:** Curso Integrador I - UTP, Ciclo 6.
- **Fecha:** 17 de octubre de 2026.
- **Version evaluada:** imagenes Docker `canchadirecta/backend:0.1.0` y
  `canchadirecta/frontend:0.1.0` (stack en `http://localhost:8083`).
- **Estado:** cerrado funcionalmente; quedan solo tareas opcionales indicadas en la seccion 6.

## 1. Alcance entregado

- Backend Quarkus 3.28 con 112 operaciones verificadas por humo, seguridad JWT con refresh
  rotado, RBAC (`USER`, `OWNER`, `PLATFORM_ADMIN`), auditoria y migraciones Flyway.
- Base MySQL 8.4 con esquema versionado y datos de catalogo; sin `drop-and-create`.
- Frontend Angular 22 standalone con:
  - catalogo paginado, busqueda, filtros, comparacion y detalle con calendario;
  - reserva, pago simulado, mis reservas, perfil y reenvio de verificacion;
  - reclamos, notificaciones y solicitud/validacion de propietario;
  - panel del propietario: establecimiento (alta, edicion y baja), canchas, fotos, cocheras,
    agenda, promociones, reservas recibidas y liquidaciones;
  - administracion: propietarios, reclamos, usuarios/roles en modal, promociones, cocheras,
    supervision y auditoria.
- Paginacion reutilizable en todas las listas extensas (10 por pagina; catalogo 12).
- Regla de roles corregida: un `PLATFORM_ADMIN` no solicita ser propietario; si necesita
  publicar canchas propias se le asigna `OWNER`. La supervision de establecimientos ajenos
  sigue siendo de solo lectura.

## 2. Criterios de aceptacion y resultado

| Criterio | Evidencia | Resultado |
|---|---|---|
| Backend compila y pasa pruebas | `backend/mvnw.cmd -B -f backend/pom.xml test` | 146/146 PASS |
| Humo end-to-end de API | `scripts/smoke.ps1 -BaseUrl http://localhost:8089` | 112/112 PASS |
| Frontend compila | `npm run build` | OK |
| Pruebas unitarias frontend | `npm test -- --watch=false` | 15/15 PASS |
| Flujo de usuario en navegador | `npm run test:e2e` (Docker 8083) | 15/15 PASS |
| Flujo propietario/admin en navegador | `npm run test:e2e:owner` | 28/28 PASS |
| Accesibilidad de teclado y estructura | `npm run test:a11y` (E-42) + E-41 | 14 rutas y teclado PASS; modal/paginador PASS en E-41 |
| Stack Docker operativo | `/q/health/ready`, frontend 8083 | 200 / 200 |

## 3. Evidencias

- Resultados detallados: `docs/RESULTADOS_PRUEBAS.md` (E-01 a E-43).
- Trazabilidad de requisitos: `docs/TRAZABILIDAD.md`.
- Capturas de UI: `docs/evidencias/ui/` (flujos usuario, propietario/admin).
- Capturas de accesibilidad:
  - `a11y-foco-skip-link.png`
  - `a11y-modal-roles.png`
  - `a11y-paginacion-auditoria.png`
- Decisiones y limitaciones: `docs/DECISIONES_ARQUITECTURA.md` y
  `docs/LIMITACIONES_CONOCIDAS.md`.

## 4. Correcciones finales

- `GET /establecimientos/mios`: el panel descubre el local automáticamente y deja de pedir el ID.
- Registro con auto-login y retorno a la ruta original (reserva o mis reservas).
- Paginador reutilizable con ancho fijo y numeración tabular.
- “Vista previa” abre el detalle en la misma pestaña, con botón “Volver a configurar” que retorna
  al panel con la cancha en edición.
- Galería completa: todas las miniaturas, foto principal seleccionable, visor y fallback para
  imágenes no disponibles; migración V11 corrige las URLs de demo rotas.
- “Horario semanal recurrente” y “Bloqueos y excepciones” con textos explicativos.
- Edicion y baja de establecimiento desde el panel con `GET /establecimientos/{id}`.
- Reenvio del enlace de verificacion desde el perfil.
- Paginacion en usuarios, propietarios, reclamos, promociones, cocheras, supervision,
  auditoria, reservas, liquidaciones, notificaciones y listas propias.
- Roles en modal accesible en lugar de fila inline.
- Correccion de jerarquia de encabezados en estados vacios (`h2` dentro de `h1`).
- Humo endurecido: la cancha se busca por establecimiento y los reclamos con `limite=200`.

## 5. Exclusiones acordadas

- Pasarela de pago y desembolso reales (F05): el pago es de demostracion.
- Penalidad parcial o no-show (L-12).
- Excepciones de agenda por fecha (L-13).
- Envio real de correos: el enlace de verificacion se registra para el entorno local.
- Revision con lector de pantalla (NVDA/Narrator): no forma parte del alcance automatizado.

## 6. Tareas opcionales posteriores

1. Revision manual con lector de pantalla en Windows.
2. Endurecer el arranque de la cuenta superadministrador con un script idempotente de bootstrap.
3. Cambiar la contrasena local del superadministrador antes de cualquier despliegue real.
4. Fase 2: modo oscuro, asistente de configuracion y rediseno de bloqueos.

## 7. Conformidad

| Rol | Nombre | Firma | Fecha |
|---|---|---|---|
| Responsable del proyecto |  |  |  |
| Docente / evaluador |  |  |  |

**Conclusion:** el sistema cumple el alcance funcional comprometido para el ciclo, con
backend, frontend y base de datos integrados, evidencia reproducible y limitaciones
documentadas.
