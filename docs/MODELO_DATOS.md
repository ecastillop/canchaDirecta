# Modelo de datos

Motor: **MySQL 8.0.41** (host), InnoDB, utf8mb4 / utf8mb4_0900_ai_ci. Esquema
versionado con **Flyway** (`backend/src/main/resources/db/migration`).

## 1. Migraciones

| Version | Archivo | Contenido |
|---|---|---|
| V1 | `V1__baseline_schema.sql` | 28 tablas fuente (traducidas de SQL Server a MySQL) |
| V2 | `V2__geography_hierarchy.sql` | `departamento`, `provincia`, `distrito.id_provincia` + `codigo_ubigeo` |
| V3 | `V3__integrity_and_extensions.sql` | unicidad parcial (columna generada) + 7 tablas de extension |
| V4 | `V4__seed_catalogs.sql` | roles, deportes, caracteristicas, geografia minima de Lima |
| V5 | `V5__estado_reserva_rechazada.sql` | `reserva.estado` admite `RECHAZADA` (rechazo del propietario, ADR-0018) |
| V6 | `V6__estado_reserva_expirada.sql` | `reserva.estado` admite `EXPIRADA` (hold de pago vencido, ADR-0022) |

Total tras aplicar: **38 tablas** (28 fuente + 2 geografia + 7 extensiones + 1 historial Flyway).

## 2. Traduccion SQL Server -> MySQL

| SQL Server | MySQL | Nota |
|---|---|---|
| `IDENTITY(1,1)` | `AUTO_INCREMENT` | |
| `DATETIME2` | `DATETIME(6)` | instantes (UTC por convencion de app) |
| `BIT` | `TINYINT(1)` | |
| `TIME` | `TIME` | horarios semanales (`LocalTime`) |
| `CREATE UNIQUE INDEX ... WHERE principal=1` | columna generada + `UNIQUE` | ADR-0006 |
| `CREATE UNIQUE INDEX ... WHERE estado='APROBADO'` | columna generada + `UNIQUE` | ADR-0006 |
| triggers `TR_reserva_sin_solapamiento` / `..._bloqueos` | **no traducidos** | se garantiza en el caso de uso con `SELECT ... FOR UPDATE` (ADR-0005) |
| `GO`, `THROW`, `inserted` | - | no aplican |

## 3. Unicidad parcial (patron MySQL)

```sql
-- foto principal por cancha
ALTER TABLE foto_cancha
  ADD COLUMN id_cancha_principal INT
    GENERATED ALWAYS AS (CASE WHEN principal = 1 THEN id_cancha ELSE NULL END) STORED,
  ADD CONSTRAINT uq_foto_cancha_principal UNIQUE (id_cancha_principal);

-- un solo pago APROBADO por reserva
ALTER TABLE pago
  ADD COLUMN id_reserva_aprobada BIGINT
    GENERATED ALWAYS AS (CASE WHEN estado = 'APROBADO' THEN id_reserva ELSE NULL END) STORED,
  ADD CONSTRAINT uq_pago_aprobado_reserva UNIQUE (id_reserva_aprobada);
```

`UNIQUE` ignora `NULL`, por lo que solo restringe las filas que cumplen la condicion.

## 4. Reglas de integridad en el motor (CHECK)

`fin > inicio` en `reserva`, `bloqueo_cancha`, `horario_cancha`, `reserva_cochera`;
`precio_base >= 0`, `capacidad > 0`, `dia_semana BETWEEN 1 AND 7`,
`monto_descuento <= monto_cancha`, `porcentaje_comision BETWEEN 0 AND 100`,
`puntaje BETWEEN 1 AND 5`, enums de estado por `CHECK` (`reserva.estado`:
`PENDIENTE`, `CONFIRMADA`, `CANCELADA`, `RECHAZADA`, `EXPIRADA`, `FINALIZADA`), `valor > 0` en promocion.

## 5. Geografia y semillas

`departamento -> provincia -> distrito`; `distrito.codigo_ubigeo` (6 dig. INEI, unico);
`UNIQUE(id_provincia, nombre)`. Semilla minima: Lima (departamento/provincia + 19
distritos). Ampliable con catalogos oficiales verificables.

## 6. Extensiones (ADR-0007)

`auth_token` (verificacion/reset hasheados), `sesion_refresh` (refresh rotativo
revocable), `idempotencia` (clave+operacion), `solicitud_cambio_reserva`,
`config_establecimiento` (politicas: slot, duraciones, hold, cancelacion,
anticipacion, zona), `reclamo_seguimiento`, `pago_evento`.

## 7. Pendiente

- Diccionario de datos actualizado (a partir de `diccionario_datos.xlsx`).
- Scripts de rollback/recuperacion de migracion.

El ER simplificado y el resto de diagramas estan en `docs/DIAGRAMAS.md`.
