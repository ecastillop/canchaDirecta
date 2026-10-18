# Requisitos y reconciliacion de fuentes

Reconstruccion del alcance de CanchaDirecta a partir de los tres entregables,
resolviendo contradicciones de forma **explicita y trazable**. Fuente principal del
analisis: `CANCHADIRECTA_ANALISIS_TECNICO.md`.

## 1. Fuentes

| ID | Archivo | Contenido |
|---|---|---|
| S1 | `Avance_2_Desarrollo_Web_Integrado.pdf` (55 pp) | C01-C08, HU-001...HU-008-2, 11 entidades, MVC/JSP/Servlet/MySQL; **excluye pagos reales** |
| S2 | `Sistema Web Integrado.pptx` (13 diap.) | modelo logico/fisico inicial, tecnologias |
| S3 | `CanchaDirecta_Entrega_02_CORREGIDA_FINAL(1).zip` | 28 tablas, 149 campos, 32 relaciones, 8 reglas, 9 mockups, 6 CU, SQL Server, auditorias |
| S4 | `CANCHADIRECTA_ANALISIS_TECNICO.md` | discrepancias, riesgos, decisiones sugeridas |

Etiquetado de procedencia: `ORIGINAL` (S1/S2), `ZIP_AMPLIADO` (S3), `MEJORA_PROPUESTA`.

## 2. Diferencias y decisiones vinculantes

| Tema | S1/S2 | S3 (ZIP) | Decision adoptada |
|---|---|---|---|
| Stack | Servlet/JSP + Tomcat | Capas, SQL Server | Quarkus 3.40.1 + Java 21 + Maven (ADR-0002). No JSP/Tomcat/SQL Server. |
| Persistencia | MySQL, 11 entidades | SQL Server, 28 tablas | MySQL 8 (host 8.0.41). 28 tablas base + extensiones (ADR-0006/0007). |
| Roles | Usuario/Admin | Usuario/Propietario/Admin plataforma | `USER`, `OWNER`, `PLATFORM_ADMIN` (pueden coexistir con autorizacion). |
| Pagos | Reales excluidos | Pasarela, pago, reembolso, liquidacion | Simulador local determinista + `PaymentGatewayPort` (externo deshabilitado). |
| Reservas | Fecha/inicio-fin/estado | + promo, cochera, pago, historial | Consistencia transaccional + idempotencia + locking por cancha (ADR-0005). |
| Ubicacion | departamento-provincia-distrito | solo distrito | Jerarquia completa + ubigeo (ADR-0003). |
| UI | formularios web | 9 mockups | Conservar controles/flujo; rediseno profesional; pantallas nuevas para lo anadido. |

## 3. Union de funcionalidades (F01-F07 del prompt) - cobertura

Ver `TRAZABILIDAD.md` para el detalle por requisito. Resumen:
F01 Identidad/acceso - F02 Geografia/deportes/catalogo - F03 Propietario/sedes/canchas -
F04 Disponibilidad/reservas - F05 Pagos/liquidaciones - F06 Soporte/avisos/calificaciones -
F07 Administracion global.

## 4. Reconciliacion de las 11 entidades del primer avance

| Entidad S1/S2 | Destino MySQL | Estado | Justificacion |
|---|---|---|---|
| `departamento` | tabla `departamento` (V2) | Implementada | Recuperada del primer avance; jerarquia geografica |
| `provincia` | tabla `provincia` (V2) | Implementada | Recuperada; FK a departamento |
| `distrito` | tabla `distrito` (V1+V2) | Implementada | Se le anade `id_provincia` y `codigo_ubigeo`; se elimina `UNIQUE(nombre)` global |
| `sede` | `establecimiento` | Equivalente | Mismo concepto, nombre del ZIP |
| `tipo_espacio` | **decision explicita** | Ver ADR-0004 | Distinto de `deporte` (superficie/tipo de cancha vs disciplina) -> se mantiene como **atributo/taxonomia** de `cancha`, no se fuerza a `deporte` |
| `espacio_deportivo` | `cancha` | Equivalente | Misma entidad con mas atributos en el ZIP |
| `horario` | `horario_cancha` | Equivalente | Horario semanal por cancha |
| `rol` | `rol` | Conservada | Catalogo de roles |
| `usuario` | `usuario` | Conservada | + credenciales/estado |
| `estado_reserva` | **decision explicita** | Ver ADR-0004 | Se modela como **maquina de estados tipada + CHECK** (no tabla fisica), preservando sus reglas |
| `reserva` | `reserva` | Conservada | + promocion, montos, historial |

Ninguna entidad se elimina en silencio.

## 5. Extensiones minimas justificadas (ADR-0007)

`auth_token`, `sesion_refresh`, `idempotencia`, `solicitud_cambio_reserva`,
`config_establecimiento`, `reclamo_seguimiento`, `pago_evento`.

Cada una tiene caso de uso, constraint e indice. No se anaden tablas sin
justificacion (se descartan colas/event bus, Redis, Kafka, etc.).

## 6. Defaults funcionales (propuestas - configurables, ADR-0008)

Slot 30 min - duracion 30-180 min - hold de pago 10 min - cancelacion gratuita hasta
24 h - comision 10 % - ventana de busqueda 365 dias. Persistidos en
`config_establecimiento` (por establecimiento) y en configuracion de aplicacion.
`Clock` inyectable para pruebas de tiempo.

## 7. Riesgos heredados (de S4) y su tratamiento

R1 concurrencia de reservas -> ADR-0005 + test multi-hilo -
R2 cochera capacidad>1 -> locking de cochera en orden consistente -
R3 estados/expiracion -> maquina de estados + job de expiracion + `pago_evento` -
R4 unicidad filtrada SQL Server -> ADR-0006 -
R5 geografia/ubigeo -> ADR-0003 -
R6 IDOR multi-propietario -> autorizacion por ownership en servicios + tests -
R7 pagos -> simulador + puerto -
R8 mockups incompletos -> disenar pantallas faltantes -
R9 localhost/Docker -> 3 modos documentados (A/B/C).
