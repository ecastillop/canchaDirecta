# Diagramas de arquitectura

Cada diagrama se muestra como imagen para que se vea directamente en VS Code (sin extensiones) y
en cualquier visor Markdown. El codigo fuente Mermaid queda al lado, en `docs/diagramas/*.mmd`,
por si hay que editarlo o volver a exportarlo.

- Imagenes: `docs/evidencias/diagramas/*.png`
- Fuentes: `docs/diagramas/*.mmd`
- Regenerar:

```powershell
npx --yes @mermaid-js/mermaid-cli@11 -i docs/diagramas/01-contexto.mmd `
  -o docs/evidencias/diagramas/01-contexto.png -b white -w 1600 `
  -p puppeteer.json   # { "executablePath": "C:/Program Files/Google/Chrome/Application/chrome.exe" }
```

Convenciones: el sentido de las flechas es "depende de" o "llama a"; los puertos son los del
`compose.yml`; los nombres de tablas y modulos coinciden con el codigo.

## 1. Contexto (C4 nivel 1)

![Contexto del sistema: actores, SPA, backend y MySQL](evidencias/diagramas/01-contexto.png)

Fuente: [`diagramas/01-contexto.mmd`](diagramas/01-contexto.mmd)

## 2. Contenedores y despliegue (Compose)

![Contenedores: Nginx con la SPA, backend Quarkus, MySQL y JWKS](evidencias/diagramas/02-despliegue.png)

Fuente: [`diagramas/02-despliegue.mmd`](diagramas/02-despliegue.mmd)

Modos soportados: backend nativo contra el MySQL del host, Compose contra el MySQL del host
(`host.docker.internal`) y Compose con MySQL aislado (`compose.mysql.yml`).

## 3. Modulos del backend (monolito modular + hexagonal)

![Modulos internos del backend y sus adaptadores](evidencias/diagramas/03-modulos.png)

Fuente: [`diagramas/03-modulos.mmd`](diagramas/03-modulos.mmd)

**No son microservicios.** Todo corre en un unico proceso Quarkus y un unico despliegue: los
modulos son paquetes internos que comparten base de datos y se llaman en memoria a traves de sus
puertos. Microservicios implicaria procesos independientes, con su propia base o contrato de red y
comunicacion entre ellos; ADR-0001 lo descarta. La separacion por modulos y las capas hexagonales
permiten extraer un modulo a futuro sin reescribir el dominio.

Regla de dependencias: `domain` no conoce Quarkus, HTTP ni JPA; los puertos miran hacia dentro y
los adaptadores los implementan. `shared` concentra seguridad JWT, reloj, errores y utilidades.

## 4. Secuencia: reserva concurrente (ADR-0005)

![Secuencia de la reserva concurrente y el pago simulado](evidencias/diagramas/04-reserva-concurrente.png)

Fuente: [`diagramas/04-reserva-concurrente.mmd`](diagramas/04-reserva-concurrente.mmd)

Si el pago no llega dentro del hold, el job de expiracion pasa la reserva a `EXPIRADA` y libera
la franja (ADR-0022).

## 5. Estados de la reserva

![Estados de la reserva y transiciones](evidencias/diagramas/05-estados-reserva.png)

Fuente: [`diagramas/05-estados-reserva.mmd`](diagramas/05-estados-reserva.mmd)

## 6. Portales del frontend

![Portales del frontend y el shell comun](evidencias/diagramas/06-portales-frontend.png)

Fuente: [`diagramas/06-portales-frontend.mmd`](diagramas/06-portales-frontend.mmd)

Los guards son apoyo de UX: la autorizacion real vive en el backend (JWT + roles). Todas las
areas comparten estados de carga, vacio, error y exito, y el tema claro/oscuro se controla dentro
de cada portal.

## 7. Modelo de datos principal (ER simplificado)

![Modelo de datos principal](evidencias/diagramas/07-modelo-datos.png)

Fuente: [`diagramas/07-modelo-datos.mmd`](diagramas/07-modelo-datos.mmd)

El detalle de columnas, migraciones y restricciones esta en `MODELO_DATOS.md`; el esquema real lo
posee Flyway (V1..V18) e Hibernate solo valida.
