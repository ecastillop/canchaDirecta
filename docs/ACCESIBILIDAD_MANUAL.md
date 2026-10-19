# Guion de revision manual con lector de pantalla

Complementa `npm run test:a11y` (estructura, teclado y foco automatizados) con la unica parte
que no se puede automatizar: la experiencia real con un lector de pantalla. Cierra el punto
abierto de `L-23`.

## Preparacion

- Windows con **NVDA** (gratuito, <https://www.nvaccess.org>) o **Narrador** (`Win + Ctrl + Enter`).
- Chrome o Edge actualizados, zoom al 100 %.
- Frontend en marcha (`docker compose up -d` o `npm start`) y datos de demostracion:
  `.\scripts\demo-reset.ps1 -Force`.
- Cuentas demo (clave `DemoCancha2026`): `cliente@`, `propietario@`, `operador@canchadirecta.pe`.

Recorrer cada caso con el lector activo y anotar el resultado. Se considera correcto cuando el
lector anuncia el nombre, el rol y el estado del control, y el foco nunca queda en un elemento
oculto o fuera de la ventana modal.

## Casos

| # | Pantalla | Pasos | Se espera | Resultado |
|---|---|---|---|---|
| 1 | Cualquiera | Primer `Tab`; luego `Enter` | Anuncia "Saltar al contenido" y lleva el foco al inicio del contenido | |
| 2 | Menú principal | Activar un enlace del menu con `Enter` | Anuncia la nueva pantalla (encabezado principal) y el foco no queda en el enlace anterior | |
| 3 | `/auth/login` | Recorrer el formulario con `Tab` | Anuncia cada etiqueta, el tipo de campo y los errores de validacion | |
| 4 | `/canchas` | Recorrer tarjetas, filtros y paginacion | Anuncia nombre, distrito, precio y el rango "Mostrando X-Y de Z" al cambiar de pagina | |
| 5 | `/canchas/{id}` | Abrir una foto; `Tab`; `Escape` | Anuncia "Fotos de la cancha", el foco queda dentro del visor, `Escape` cierra y el foco vuelve al boton de la foto | |
| 6 | Flujo de reserva | Elegir fecha y franja | Anuncia dia, hora, disponibilidad y precio de la franja seleccionada | |
| 7 | Accion destructiva (p. ej. cancelar reserva) | Abrir el dialogo de confirmacion; `Escape` | Anuncia el titulo del dialogo y el mensaje; el foco inicial queda en la accion de cancelar/volver (no en la destructiva); al cerrar vuelve al boton que lo abrio | |
| 8 | `/panel/liquidaciones`, `/admin/usuarios` | Recorrer una tabla con `Ctrl + Alt + flechas` (NVDA) | Anuncia el nombre de la tabla, las cabeceras y la celda actual | |
| 9 | Cualquier accion con aviso | Guardar y escuchar | El aviso se anuncia una sola vez, sin repetirse | |
| 10 | Tema claro/oscuro | Cambiar el tema y repetir los casos 3 y 4 | No hay perdida de foco ni de etiquetas; el contraste lo cubre `contraste-modos.mjs` | |

## Registro

Anotar en `docs/RESULTADOS_PRUEBAS.md` (nueva evidencia) la fecha, el lector y la version, los
casos con incidencias y su correccion. Si todo pasa, actualizar `L-23` como cerrada.
