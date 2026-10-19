# Cuentas de desarrollo

Cuentas locales para probar cada perfil. **Solo entorno de desarrollo**: la clave es conocida y
debil (8 digitos), nunca debe existir en un despliegue real.

Todas las cuentas nuevas se crean con `scripts/usuario-dev.ps1`, que registra la cuenta por la API
(el backend hashea la clave) y asigna roles y perfil por SQL con las credenciales de `.env`.

## Cuentas nuevas (clave `12345678`)

| Perfil | Correo | Clave | Roles | Pantallas |
|---|---|---|---|---|
| Superadministrador | `elvis@canchadirecta.pe` | `12345678` | USER, OWNER, PLATFORM_ADMIN | Todo: publico, cuenta, `/panel`, `/recepcion`, `/admin` |
| Cliente | `cliente.dev@canchadirecta.pe` | `12345678` | USER | Publico, cuenta (`/mi-resumen`, `/mis-reservas`, ...) |
| Propietario | `propietario.dev@canchadirecta.pe` | `12345678` | USER, OWNER (propietario validado) | `/panel` y sus secciones |
| Operador de recepcion | `operador.dev@canchadirecta.pe` | `12345678` | USER, OPERADOR (vinculado a la primera sede) | `/recepcion` y sus secciones |

El propietario y el operador recien creados no tienen canchas ni reservas propias: el panel y la
recepcion muestran estados vacios hasta que se les asigne un establecimiento. Para datos completos
usar las cuentas demo.

## Cuentas demo existentes

Se recrean con `.\scripts\demo-reset.ps1 -Force`.

> `demo-reset.ps1 -Force` borra la base: las cuentas de desarrollo (`elvis@`, `*.dev@`) desaparecen.
> Despues del reset hay que volver a ejecutar `scripts/usuario-dev.ps1` para cada perfil.

| Perfil | Correo | Clave | Notas |
|---|---|---|---|
| Cliente | `cliente@canchadirecta.pe` | `DemoCancha2026` | Con reservas y favoritos sembrados |
| Propietario | `propietario@canchadirecta.pe` | `DemoCancha2026` | Con sedes, canchas, agenda y liquidaciones |
| Operador | `operador@canchadirecta.pe` | `DemoCancha2026` | Vinculado a la sede demo |

## Superadministrador del `.env`

`ADMIN_CORREO` / `ADMIN_PASSWORD` de `.env` corresponden al administrador que crea
`demo-reset.ps1`. `scripts/usuario-dev.ps1` no los modifica ni los imprime. Si se cambia esa clave
en `.env`, el siguiente `demo-reset.ps1 -Force` la vuelve a aplicar.

## Crear o actualizar una cuenta

```powershell
# Superadministrador (por defecto)
.\scripts\usuario-dev.ps1

# Otros perfiles
.\scripts\usuario-dev.ps1 -Correo cliente2@canchadirecta.pe -Nombres Cliente -Rol USER
.\scripts\usuario-dev.ps1 -Correo propietario2@canchadirecta.pe -Nombres Propietario -Rol OWNER
.\scripts\usuario-dev.ps1 -Correo operador2@canchadirecta.pe -Nombres Operador -Rol OPERADOR
```

El script es idempotente: si la cuenta existe, verifica la clave y completa roles y perfil. Si la
clave no coincide se detiene sin tocar la cuenta (usar otro correo o recuperar la clave).
