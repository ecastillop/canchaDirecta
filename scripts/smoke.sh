#!/usr/bin/env bash
# ============================================================================
# Prueba de humo de la API de CanchaDirecta (Linux/macOS/Git Bash).
# Uso: ./smoke.sh [base_url]      (por defecto http://localhost:8088)
# Requiere: curl y python3 (o jq) para leer el JSON. Los pasos de calificacion
# requieren ademas el cliente `mysql` y credenciales (DB_USER/DB_PASSWORD/DB_NAME en
# el entorno o en `.env`); si faltan, se omiten con un aviso.
# Recorre identidad -> venues/reservas -> disponibilidad -> agenda/bloqueos ->
# cochera -> pago/reembolso -> liquidaciones del propietario -> revocacion ->
# calificaciones de una reserva finalizada -> reclamos y avisos in-app ->
# moderacion de reclamos (PLATFORM_ADMIN).
# Uso en contenedor: DB_PORT=3309 bash scripts/smoke.sh http://localhost:8089
# ============================================================================
set -uo pipefail

BASE_URL="${1:-http://localhost:8088}"
BASE_URL="${BASE_URL%/}"
ID_DISTRITO="${ID_DISTRITO:-1}"
PASOS=0
FALLOS=0

# .env del proyecto (no sobreescribe variables ya exportadas): credenciales de MySQL
# para retrasar la franja de la reserva que se califica.
ENV_FILE="$(cd "$(dirname "$0")/.." && pwd)/.env"
if [ -f "$ENV_FILE" ]; then
  while IFS= read -r linea; do
    [[ "$linea" =~ ^[[:space:]]*# ]] && continue
    [[ "$linea" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)[[:space:]]*=(.*)$ ]] || continue
    clave="${BASH_REMATCH[1]}"
    valor="${BASH_REMATCH[2]}"
    valor="${valor%$'\r'}"
    valor="${valor%\"}"; valor="${valor#\"}"
    if [ -z "${!clave+x}" ]; then export "$clave=$valor"; fi
  done < "$ENV_FILE"
fi
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-canchadirecta}"
DB_DISPONIBLE=0
if command -v mysql >/dev/null 2>&1 && [ -n "${DB_USER:-}" ] && [ -n "${DB_PASSWORD:-}" ]; then
  DB_DISPONIBLE=1
fi

verde() { printf '\033[32m%s\033[0m\n' "$1"; }
rojo()  { printf '\033[31m%s\033[0m\n' "$1"; }
cyan()  { printf '\033[36m%s\033[0m\n' "$1"; }

# json_get <clave> <<< json
# Se elige el primer lector de JSON que funcione de verdad (`jq`, `python3` o `python`).
# En Windows, `python3` suele ser un alias del Microsoft Store que no ejecuta Python,
# por eso se comprueba con una invocacion real y no solo con `command -v`.
JSON_LECTOR=""
json_get() {
  local clave="$1"
  if [ -z "$JSON_LECTOR" ]; then
    if command -v jq >/dev/null 2>&1; then
      JSON_LECTOR=jq
    elif command -v python3 >/dev/null 2>&1 && python3 -c 'print(1)' >/dev/null 2>&1; then
      JSON_LECTOR=python3
    elif command -v python >/dev/null 2>&1 && python -c 'print(1)' >/dev/null 2>&1; then
      JSON_LECTOR=python
    else
      echo 'ERROR_JSON: se requiere jq o python para leer las respuestas' >&2
      return 1
    fi
  fi
  if [ "$JSON_LECTOR" = jq ]; then
    jq -r --arg k "$clave" '.[$k] // empty'
  else
    "$JSON_LECTOR" -c "import sys,json;print(json.load(sys.stdin).get('$clave',''))"
  fi
}

# api <metodo> <ruta> <cuerpo|-> <token|-> <codigos_esperados_csv>
api() {
  local metodo="$1" ruta="$2" cuerpo="$3" token="$4" esperados="$5"
  local args=(-sS -X "$metodo" "$BASE_URL$ruta" -H 'Content-Type: application/json' -w '\n%{http_code}')
  [ "$token" != "-" ] && args+=(-H "Authorization: Bearer $token")
  [ "$cuerpo" != "-" ] && args+=(-d "$cuerpo")

  local salida codigo
  salida="$(curl "${args[@]}" 2>/dev/null)" || { echo "ERROR_DE_RED"; return 1; }
  codigo="$(printf '%s' "$salida" | tail -n1)"
  CUERPO_RESP="$(printf '%s' "$salida" | sed '$d')"

  if [[ ",$esperados," != *",$codigo,"* ]]; then
    return 1
  fi
  return 0
}

paso() {
  local nombre="$1"; shift
  PASOS=$((PASOS+1))
  if "$@"; then verde "[PASS] $nombre"; else rojo "[FAIL] $nombre"; FALLOS=$((FALLOS+1)); fi
}

cyan "== Smoke test CanchaDirecta :: $BASE_URL =="

CORREO="smoke.$(date +%s%N)@canchadirecta.pe"
PASSWORD="ClaveSegura123"

t_ping()      { api GET  /api/v1/system/ping         - - 200; }
t_health()    { api GET  /q/health/ready             - - 200 && printf '%s' "$CUERPO_RESP" | grep -q '"UP"'; }
t_registro()  { api POST /api/v1/auth/register "$REG" - 201; }
t_duplicado() { api POST /api/v1/auth/register "$REG" - 409; }
t_login()     { api POST /api/v1/auth/login "$LOGIN" - 200 && ACCESS="$(printf '%s' "$CUERPO_RESP" | json_get accessToken)" && REFRESH="$(printf '%s' "$CUERPO_RESP" | json_get refreshToken)" && [ -n "$ACCESS" ] && [ -n "$REFRESH" ]; }
t_me_sin()    { api GET  /api/v1/users/me            - - 401; }
t_me_con()    { api GET  /api/v1/users/me            - "$ACCESS" 200; }
t_refresh()   { api POST /api/v1/auth/refresh "{\"refreshToken\":\"$REFRESH\"}" - 200 && ROTADO="$(printf '%s' "$CUERPO_RESP" | json_get refreshToken)" && [ "$ROTADO" != "$REFRESH" ]; }
t_reuso()     { api POST /api/v1/auth/refresh "{\"refreshToken\":\"$REFRESH\"}" - 401; }
t_logout()    {
  api POST /api/v1/auth/login "$LOGIN" - 200 || return 1
  local otra; otra="$(printf '%s' "$CUERPO_RESP" | json_get refreshToken)"
  api POST /api/v1/auth/logout "{\"refreshToken\":\"$otra\"}" - 204 || return 1
  api POST /api/v1/auth/refresh "{\"refreshToken\":\"$otra\"}" - 401
}
t_reset()     { api POST /api/v1/auth/password-reset/request "{\"correo\":\"$CORREO\"}" - 202; }
t_reset_na()  { api POST /api/v1/auth/password-reset/request "{\"correo\":\"noexiste.$RANDOM@canchadirecta.pe\"}" - 202; }

# --- Establecimientos, canchas y reservas (RN-01) ---------------------------
# Franja de prueba alineada a la ventana por defecto (06:00-23:00 local) del endpoint
# de disponibilidad. America/Lima es UTC-5 sin horario de verano: 12:00 local = 17:00 UTC.
fecha_franja() {
  if date -u -d '-5 hours +2 days' '+%Y-%m-%d' >/dev/null 2>&1; then
    date -u -d '-5 hours +2 days' '+%Y-%m-%d'
  else
    date -u -v-5H -v+2d '+%Y-%m-%d'
  fi
}

# Dia ISO (1 = lunes ... 7 = domingo) de esa misma fecha, en hora de Lima.
dia_semana_franja() {
  if date -u -d '-5 hours +2 days' '+%u' >/dev/null 2>&1; then
    date -u -d '-5 hours +2 days' '+%u'
  else
    date -u -v-5H -v+2d '+%u'
  fi
}

t_establecimiento() { api POST /api/v1/establecimientos "$EST" "$ACCESS" 201 && EST_ID="$(printf '%s' "$CUERPO_RESP" | json_get id)" && [ -n "$EST_ID" ]; }
t_cancha()          { api POST "/api/v1/establecimientos/$EST_ID/canchas" "$CANCHA" "$ACCESS" 201 && CANCHA_ID="$(printf '%s' "$CUERPO_RESP" | json_get id)" && [ -n "$CANCHA_ID" ]; }
t_canchas()         { api GET "/api/v1/canchas?idEstablecimiento=$EST_ID&limite=100" - - 200 && printf '%s' "$CUERPO_RESP" | grep -q "\"id\":$CANCHA_ID[^0-9]"; }

# --- Edicion (CU04.1), baja logica (CU04.2) y comparacion (CU02.2) -----------
# Recursos dedicados: dar de baja el complejo principal romperia el flujo previo.
t_gestion_est() {
  local doc="89$(date +%N | cut -c1-8)"
  api POST /api/v1/establecimientos "{\"documentoIdentidad\":\"$doc\",\"idDistrito\":$ID_DISTRITO,\"nombre\":\"Complejo Gestion Humo\",\"direccion\":\"Av. Gestion 100\",\"referencia\":\"Humo\"}" "$ACCESS" 201 || return 1
  GEST_EST_ID="$(printf '%s' "$CUERPO_RESP" | json_get id)"
  [ -n "$GEST_EST_ID" ]
}
t_gestion_cancha() {
  api POST "/api/v1/establecimientos/$GEST_EST_ID/canchas" '{"nombre":"Cancha Gestion Humo","descripcion":"Humo","capacidad":10,"precioBase":60.00}' "$ACCESS" 201 || return 1
  GEST_CANCHA_ID="$(printf '%s' "$CUERPO_RESP" | json_get id)"
  [ -n "$GEST_CANCHA_ID" ]
}
t_gestion_editar_est() {
  api PUT "/api/v1/establecimientos/$GEST_EST_ID" "{\"idDistrito\":$ID_DISTRITO,\"nombre\":\"Complejo Gestion Editado\",\"direccion\":\"Av. Editada 321\",\"referencia\":\"Humo\"}" "$ACCESS" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"nombre":"Complejo Gestion Editado"'
}
t_gestion_editar_cancha() {
  api PUT "/api/v1/canchas/$GEST_CANCHA_ID" '{"nombre":"Cancha Gestion Editada","descripcion":"Editada","capacidad":12,"precioBase":65.00}' "$ACCESS" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"capacidad":12'
}
t_gestion_comparar() {
  api GET "/api/v1/canchas/comparar?ids=$GEST_CANCHA_ID,$CANCHA_ID" - - 200 || return 1
  printf '%s' "$CUERPO_RESP" | grep -q "\"id\":$GEST_CANCHA_ID," &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"id\":$CANCHA_ID[^0-9]"
}
t_gestion_baja_cancha()     { api DELETE "/api/v1/canchas/$GEST_CANCHA_ID" - "$ACCESS" 204; }
t_gestion_baja_cancha_estado() { api GET "/api/v1/canchas/$GEST_CANCHA_ID" - - 200 && printf '%s' "$CUERPO_RESP" | grep -q '"estado":"INACTIVA"'; }
t_gestion_baja_est()        { api DELETE "/api/v1/establecimientos/$GEST_EST_ID" - "$ACCESS" 204; }

# --- Catalogo avanzado (F02/F03, L-10) ---------------------------------------
t_catalogo_distritos() {
  api GET "/api/v1/ubicaciones/distritos?limite=20" - - 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"provincia":"Lima"' &&
    printf '%s' "$CUERPO_RESP" | grep -q '"departamento":"Lima"'
}
t_catalogo_deportes() {
  api GET /api/v1/deportes - - 200 || return 1
  DEPORTE_ID="$(printf '%s' "$CUERPO_RESP" | grep -o '"id":[0-9]*' | head -n1 | cut -d: -f2)"
  [ -n "$DEPORTE_ID" ]
}
t_cancha_deportes() {
  api PUT "/api/v1/canchas/$CANCHA_ID/deportes" "{\"ids\":[$DEPORTE_ID]}" "$ACCESS" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"deportes":\[{'
}
t_cancha_foto() {
  api POST "/api/v1/canchas/$CANCHA_ID/fotos" '{"url":"https://picsum.photos/seed/canchadirecta-humo/800/600","orden":1,"principal":true}' "$ACCESS" 201 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"principal":true'
}
t_cancha_detalle() {
  api GET "/api/v1/canchas/$CANCHA_ID" - - 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"deportes":\[{' &&
    printf '%s' "$CUERPO_RESP" | grep -q '"fotos":\[{' &&
    printf '%s' "$CUERPO_RESP" | grep -q '"departamento":"Lima"'
}
t_cancha_filtro() {
  api GET "/api/v1/canchas?idDeporte=$DEPORTE_ID&precioMax=80&q=Smoke&limite=100" - - 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"id\":$CANCHA_ID,"
}
t_reserva()         { api POST /api/v1/reservas "$(cuerpo_reserva)" "$ACCESS" 201 && printf '%s' "$CUERPO_RESP" | grep -q '"estado":"PENDIENTE"' && RESERVA_ID="$(printf '%s' "$CUERPO_RESP" | json_get id)" && [ -n "$RESERVA_ID" ]; }
t_reserva_dup()     { api POST /api/v1/reservas "$(cuerpo_reserva)" "$ACCESS" 409 && printf '%s' "$CUERPO_RESP" | grep -q '"code":"SLOT_UNAVAILABLE"'; }
t_reservas_mias()   { api GET /api/v1/reservas/mias - "$ACCESS" 200 && printf '%s' "$CUERPO_RESP" | grep -q "\"canchaId\":$CANCHA_ID[^0-9]"; }
t_reserva_sin_tok() { api POST /api/v1/reservas "$(cuerpo_reserva)" - 401; }

# Bandeja del propietario y decisiones sobre las reservas recibidas (HU-008-2).
t_reservas_recibidas() {
  api GET "/api/v1/reservas/recibidas?estado=PENDIENTE" - "$ACCESS" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"id\":$RESERVA_ID[^0-9]" &&
    printf '%s' "$CUERPO_RESP" | grep -q '"estado":"PENDIENTE"'
}
t_confirmar()     { api POST "/api/v1/reservas/$RESERVA_ID/confirmar" - "$ACCESS" 200 && printf '%s' "$CUERPO_RESP" | grep -q '"estado":"CONFIRMADA"'; }
t_confirmar_dup() { api POST "/api/v1/reservas/$RESERVA_ID/confirmar" - "$ACCESS" 409 && printf '%s' "$CUERPO_RESP" | grep -q '"code":"CONFLICT"'; }
t_reserva2()      { api POST /api/v1/reservas "$(cuerpo_reserva2)" "$ACCESS" 201 && RESERVA2_ID="$(printf '%s' "$CUERPO_RESP" | json_get id)" && [ -n "$RESERVA2_ID" ]; }
t_rechazar()      { api POST "/api/v1/reservas/$RESERVA2_ID/rechazar" '{"motivo":"Cancha reservada para mantenimiento"}' "$ACCESS" 200 && printf '%s' "$CUERPO_RESP" | grep -q '"estado":"RECHAZADA"'; }
t_disponibilidad2() { api GET "/api/v1/canchas/$CANCHA_ID/disponibilidad?fecha=$FECHA&duracionMinutos=60" - - 200 && printf '%s' "$CUERPO_RESP" | grep -q "\"inicio\":\"$INICIO2\""; }

# La disponibilidad es publica; se comprueba que la franja de prueba este libre o no.
disponibilidad() { api GET "/api/v1/canchas/$CANCHA_ID/disponibilidad?fecha=$FECHA&duracionMinutos=60" - - 200; }
t_disponibilidad()         { disponibilidad && printf '%s' "$CUERPO_RESP" | grep -q "\"inicio\":\"$INICIO\""; }
# La franja ocupada no debe ofrecerse y debe figurar en `ocupadas` con el motivo esperado
# (ADR-0047). El cuerpo es un JSON de una linea: `franjas` va antes de `ocupadas`.
verifica_ocupada() {
  local esperado="$1" franjas
  franjas=$(printf '%s' "$CUERPO_RESP" | sed 's/,"ocupadas".*//')
  printf '%s' "$franjas" | grep -q "\"inicio\":\"$INICIO\"" && return 1
  printf '%s' "$CUERPO_RESP" | grep -q "\"inicio\":\"$INICIO\"[^}]*\"estado\":\"$esperado\"" || return 1
  return 0
}
t_disponibilidad_reservada() { disponibilidad && verifica_ocupada RESERVADA; }
t_disponibilidad_bloqueada() { disponibilidad && verifica_ocupada BLOQUEADA; }
t_cancelar()     { api POST "/api/v1/reservas/$RESERVA_ID/cancelar" - "$ACCESS" 200 && printf '%s' "$CUERPO_RESP" | grep -q '"estado":"CANCELADA"'; }
t_cancelar_dup() { api POST "/api/v1/reservas/$RESERVA_ID/cancelar" - "$ACCESS" 409 && printf '%s' "$CUERPO_RESP" | grep -q '"code":"CONFLICT"'; }

# --- Agenda del propietario (HU-005) y bloqueos operativos (RN-03) -----------
t_horario() {
  api POST "/api/v1/canchas/$CANCHA_ID/horarios" "{\"diaSemana\":$DIA_SEMANA,\"horaInicio\":\"06:00\",\"horaFin\":\"23:00\",\"precio\":80.00}" "$ACCESS" 201 || return 1
  HORARIO_ID="$(printf '%s' "$CUERPO_RESP" | json_get id)"
  [ -n "$HORARIO_ID" ]
}
t_horarios_lista() {
  api GET "/api/v1/canchas/$CANCHA_ID/horarios" - - 200 || return 1
  printf '%s' "$CUERPO_RESP" | grep -q '"horaInicio":"06:00:00"' &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"diaSemana\":$DIA_SEMANA[^0-9]"
}
t_horario_solapado() {
  api POST "/api/v1/canchas/$CANCHA_ID/horarios" "{\"diaSemana\":$DIA_SEMANA,\"horaInicio\":\"22:00\",\"horaFin\":\"23:30\"}" "$ACCESS" 409 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"code":"CONFLICT"'
}
t_bloqueo() {
  api POST "/api/v1/canchas/$CANCHA_ID/bloqueos" "{\"inicio\":\"$INICIO\",\"fin\":\"$FIN\",\"motivo\":\"Mantenimiento de grass\"}" "$ACCESS" 201 || return 1
  BLOQUEO_ID="$(printf '%s' "$CUERPO_RESP" | json_get id)"
  [ -n "$BLOQUEO_ID" ]
}
t_bloqueo_reserva() { api POST /api/v1/reservas "$(cuerpo_reserva)" "$ACCESS" 409 && printf '%s' "$CUERPO_RESP" | grep -q '"code":"SLOT_UNAVAILABLE"'; }
t_bloqueo_levanta() { api DELETE "/api/v1/canchas/$CANCHA_ID/bloqueos/$BLOQUEO_ID" - "$ACCESS" 204; }

# --- Cochera opcional (RN-08) -----------------------------------------------
# Franja libre 20:00 UTC (15:00 local, dentro del horario 06:00-23:00).
t_cochera() {
  api POST "/api/v1/establecimientos/$EST_ID/cocheras" '{"nombre":"Cochera Smoke","capacidad":2,"tarifaHora":5.00}' "$ACCESS" 201 || return 1
  COCHERA_ID="$(printf '%s' "$CUERPO_RESP" | json_get id)"
  [ -n "$COCHERA_ID" ]
}
t_cocheras_lista() {
  api GET "/api/v1/establecimientos/$EST_ID/cocheras" - "$ACCESS" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"id\":$COCHERA_ID[^0-9]" &&
    printf '%s' "$CUERPO_RESP" | grep -q '"capacidad":2'
}
t_cupo_cochera() {
  api GET "/api/v1/canchas/$CANCHA_ID/cocheras?inicio=$INICIO_COCHERA&fin=$FIN_COCHERA" - - 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"id\":$COCHERA_ID[^0-9]" &&
    printf '%s' "$CUERPO_RESP" | grep -q '"cupoDisponible":2'
}
t_reserva_cochera() {
  api POST /api/v1/reservas "$(cuerpo_reserva_cochera)" "$ACCESS" 201 || return 1
  printf '%s' "$CUERPO_RESP" | grep -q "\"cocheraId\":$COCHERA_ID[^0-9]" &&
    printf '%s' "$CUERPO_RESP" | grep -q '"montoCochera":5' &&
    RESERVA_COCHERA_ID="$(printf '%s' "$CUERPO_RESP" | json_get id)" && [ -n "$RESERVA_COCHERA_ID" ]
}
t_cupo_cochera_1() {
  api GET "/api/v1/canchas/$CANCHA_ID/cocheras?inicio=$INICIO_COCHERA&fin=$FIN_COCHERA" - - 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"cupoDisponible":1'
}
t_cancelar_cochera() { api POST "/api/v1/reservas/$RESERVA_COCHERA_ID/cancelar" - "$ACCESS" 200 && printf '%s' "$CUERPO_RESP" | grep -q '"estado":"CANCELADA"'; }
t_cupo_cochera_2() {
  api GET "/api/v1/canchas/$CANCHA_ID/cocheras?inicio=$INICIO_COCHERA&fin=$FIN_COCHERA" - - 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"cupoDisponible":2'
}
t_cochera_baja() { api DELETE "/api/v1/establecimientos/$EST_ID/cocheras/$COCHERA_ID" - "$ACCESS" 204; }

# --- Pago simulado y reembolso (CU05.1 / CU05.3, RN-05, L-12) ---------------
t_reserva_pago() {
  api POST /api/v1/reservas "$(cuerpo_reserva_pago)" "$ACCESS" 201 || return 1
  PAY_RESERVA_ID="$(printf '%s' "$CUERPO_RESP" | json_get id)"
  [ -n "$PAY_RESERVA_ID" ]
}
t_pago() {
  api POST "/api/v1/reservas/$PAY_RESERVA_ID/pago" '{"medioPago":"TARJETA"}' "$ACCESS" 201 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"estado":"APROBADO"' &&
    printf '%s' "$CUERPO_RESP" | grep -q '"monto":80'
}
t_pago_consulta() {
  api GET "/api/v1/reservas/$PAY_RESERVA_ID/pago" - "$ACCESS" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"estado":"APROBADO"' &&
    printf '%s' "$CUERPO_RESP" | grep -q '"reembolsos":\[\]'
}
t_pago_dup() { api POST "/api/v1/reservas/$PAY_RESERVA_ID/pago" '{"medioPago":"TARJETA"}' "$ACCESS" 409 && printf '%s' "$CUERPO_RESP" | grep -q '"code":"CONFLICT"'; }
t_pago_cancelar() { api POST "/api/v1/reservas/$PAY_RESERVA_ID/cancelar" - "$ACCESS" 200 && printf '%s' "$CUERPO_RESP" | grep -q '"estado":"CANCELADA"'; }
t_pago_reembolsado() {
  api GET "/api/v1/reservas/$PAY_RESERVA_ID/pago" - "$ACCESS" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"estado":"REEMBOLSADO"' &&
    printf '%s' "$CUERPO_RESP" | grep -q '"estado":"PROCESADO"'
}

# --- Liquidaciones / comisiones (CU04.8 / CU05.2 / CU05.4, RN-07) -----------
t_reserva_liquidacion() {
  api POST /api/v1/reservas "$(cuerpo_reserva_liquidacion)" "$ACCESS" 201 || return 1
  LIQ_RESERVA_ID="$(printf '%s' "$CUERPO_RESP" | json_get id)"
  [ -n "$LIQ_RESERVA_ID" ]
}
t_pago_liquidacion() {
  api POST "/api/v1/reservas/$LIQ_RESERVA_ID/pago" '{"medioPago":"TARJETA"}' "$ACCESS" 201 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"estado":"APROBADO"'
}
t_confirmar_liquidacion() {
  api POST "/api/v1/reservas/$LIQ_RESERVA_ID/confirmar" - "$ACCESS" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"estado":"CONFIRMADA"'
}
t_liquidacion() {
  api POST /api/v1/liquidaciones "$(cuerpo_liquidacion)" "$ACCESS" 201 || return 1
  printf '%s' "$CUERPO_RESP" | grep -q '"montoBruto":80' &&
    printf '%s' "$CUERPO_RESP" | grep -q '"montoComision":8' &&
    printf '%s' "$CUERPO_RESP" | grep -q '"montoNeto":72' &&
    LIQUIDACION_ID="$(printf '%s' "$CUERPO_RESP" | json_get id)" && [ -n "$LIQUIDACION_ID" ]
}
t_liquidacion_dup() {
  api POST /api/v1/liquidaciones "$(cuerpo_liquidacion)" "$ACCESS" 409 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"code":"CONFLICT"'
}
t_liquidacion_detalle() {
  api GET "/api/v1/liquidaciones/$LIQUIDACION_ID" - "$ACCESS" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"estado":"PENDIENTE"' &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"reservaId\":$LIQ_RESERVA_ID[^0-9]"
}

# --- Revocacion de una reserva confirmada (L-14) ----------------------------
t_reserva_revocacion() {
  api POST /api/v1/reservas "$(cuerpo_reserva_revocacion)" "$ACCESS" 201 || return 1
  RESERVA_REV_ID="$(printf '%s' "$CUERPO_RESP" | json_get id)"
  [ -n "$RESERVA_REV_ID" ]
}
t_confirmar_revocacion() {
  api POST "/api/v1/reservas/$RESERVA_REV_ID/confirmar" - "$ACCESS" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"estado":"CONFIRMADA"'
}
t_revocar() {
  api POST "/api/v1/reservas/$RESERVA_REV_ID/revocar" '{"motivo":"Mantenimiento imprevisto"}' "$ACCESS" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"estado":"RECHAZADA"'
}
t_revocar_dup() {
  api POST "/api/v1/reservas/$RESERVA_REV_ID/revocar" '{"motivo":"otra vez"}' "$ACCESS" 409 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"code":"CONFLICT"'
}

# --- Calificaciones de reservas finalizadas (F06, CU03.8/CU02.5, RN-04) ------
# La API no permite reservar en el pasado: se retrasa la franja por SQL (inicio/fin
# son literales UTC, ADR-0014) y se espera al job de cierre (60 s).
sql_exec() {
  MYSQL_PWD="$DB_PASSWORD" mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" \
    --protocol=TCP -N -B "$DB_NAME" -e "$1"
}
t_reserva_calificacion() {
  api POST /api/v1/reservas "$(cuerpo_reserva_calificacion)" "$ACCESS" 201 || return 1
  RESERVA_CAL_ID="$(printf '%s' "$CUERPO_RESP" | json_get id)"
  [ -n "$RESERVA_CAL_ID" ]
}
t_confirmar_calificacion() {
  api POST "/api/v1/reservas/$RESERVA_CAL_ID/confirmar" - "$ACCESS" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"estado":"CONFIRMADA"'
}
t_finalizar_calificacion() {
  sql_exec "UPDATE reserva SET inicio = UTC_TIMESTAMP(6) - INTERVAL 2 HOUR, fin = UTC_TIMESTAMP(6) - INTERVAL 1 HOUR WHERE id_reserva = $RESERVA_CAL_ID" >/dev/null || return 1
  local limite=$(( $(date +%s) + 90 )) estado=""
  while [ "$(date +%s)" -lt "$limite" ]; do
    estado="$(sql_exec "SELECT estado FROM reserva WHERE id_reserva = $RESERVA_CAL_ID" | tr -d '\r')"
    [ "$estado" = "FINALIZADA" ] && return 0
    sleep 5
  done
  return 1
}
t_calificar() {
  api POST "/api/v1/reservas/$RESERVA_CAL_ID/calificacion" \
    '{"puntaje":5,"comentario":"Cancha en buen estado"}' "$ACCESS" 201 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"puntaje":5' &&
    printf '%s' "$CUERPO_RESP" | grep -q '"fechaRegistro":"'
}
t_calificar_dup() {
  api POST "/api/v1/reservas/$RESERVA_CAL_ID/calificacion" '{"puntaje":4}' "$ACCESS" 409 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"code":"CONFLICT"'
}
t_calificaciones_cancha() {
  api GET "/api/v1/canchas/$CANCHA_ID/calificaciones" - - 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"total":1' &&
    printf '%s' "$CUERPO_RESP" | grep -q '"promedio":5' &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"reservaId\":$RESERVA_CAL_ID[^0-9]"
}

# --- Soporte del usuario: reclamos y avisos in-app (CU06.6, F06) -------------
t_reclamo_crear() {
  api POST /api/v1/reclamos '{"asunto":"Consulta de humo","descripcion":"Reclamo generado por el humo automatico."}' "$ACCESS" 201 || return 1
  printf '%s' "$CUERPO_RESP" | grep -q '"estado":"ABIERTO"' || return 1
  RECLAMO_ID="$(printf '%s' "$CUERPO_RESP" | json_get id)"
  [ -n "$RECLAMO_ID" ]
}
t_reclamo_listar() {
  api GET /api/v1/reclamos/mios - "$ACCESS" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"id\":$RECLAMO_ID,"
}
t_reclamo_comentar() {
  api POST "/api/v1/reclamos/$RECLAMO_ID/seguimientos" '{"comentario":"Adjunto el comprobante."}' "$ACCESS" 201 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"comentario":"Adjunto el comprobante."'
}
t_reclamo_detalle() {
  api GET "/api/v1/reclamos/$RECLAMO_ID" - "$ACCESS" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"seguimientos":\[{' &&
    printf '%s' "$CUERPO_RESP" | grep -q '"comentario":"Adjunto el comprobante."'
}
t_notificaciones() {
  api GET /api/v1/notificaciones - "$ACCESS" 200 || return 1
  NO_LEIDAS="$(printf '%s' "$CUERPO_RESP" | json_get noLeidas)"
  [ "${NO_LEIDAS:-0}" -ge 1 ] || return 1
  NOTIF_ID="$(printf '%s' "$CUERPO_RESP" | grep -o '"id":[0-9]*' | head -n1 | cut -d: -f2)"
  [ -n "$NOTIF_ID" ]
}
t_notificacion_leida() {
  api POST "/api/v1/notificaciones/$NOTIF_ID/leida" - "$ACCESS" 204 || return 1
  api GET /api/v1/notificaciones - "$ACCESS" 200 || return 1
  printf '%s' "$CUERPO_RESP" | grep -q "\"noLeidas\":$((NO_LEIDAS - 1))" &&
    printf '%s' "$CUERPO_RESP" | grep -q '"leida":true'
}

# --- Moderacion de reclamos (PLATFORM_ADMIN, F07 sobre CU06.6) ---------------
# El rol viaja en el JWT: se promueve al usuario de humo en la base y se vuelve a iniciar
# sesion para obtener un token con `groups=PLATFORM_ADMIN`.
admin_login() {
  sql_exec "INSERT IGNORE INTO usuario_rol (id_usuario, id_rol) SELECT u.id_usuario, r.id_rol FROM usuario u JOIN rol r ON r.nombre = 'PLATFORM_ADMIN' WHERE u.correo = '$CORREO'" >/dev/null || return 1
  api POST /api/v1/auth/login "$LOGIN" - 200 || return 1
  ADMIN_TOKEN="$(printf '%s' "$CUERPO_RESP" | json_get accessToken)"
  [ -n "$ADMIN_TOKEN" ]
}
t_admin_lista() {
  api GET "/api/v1/admin/reclamos?estado=ABIERTO&limite=200" - "$ADMIN_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"id\":$RECLAMO_ID,"
}
t_admin_lista_user() {
  api GET /api/v1/admin/reclamos - "$ACCESS" 403
}
t_admin_estado() {
  api POST "/api/v1/admin/reclamos/$RECLAMO_ID/estado" '{"estado":"EN_REVISION","comentario":"Estamos revisando el caso."}' "$ADMIN_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"estado":"EN_REVISION"'
}
t_admin_responde() {
  api POST "/api/v1/admin/reclamos/$RECLAMO_ID/seguimientos" '{"comentario":"Caso resuelto."}' "$ADMIN_TOKEN" 201 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"comentario":"Caso resuelto."'
}
t_admin_detalle() {
  api GET "/api/v1/admin/reclamos/$RECLAMO_ID" - "$ADMIN_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"comentario":"Estamos revisando el caso."' &&
    printf '%s' "$CUERPO_RESP" | grep -q '"comentario":"Caso resuelto."'
}
t_admin_avisos() {
  api GET /api/v1/notificaciones - "$ACCESS" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"titulo":"Respuesta a tu reclamo"' &&
    printf '%s' "$CUERPO_RESP" | grep -q '"titulo":"Reclamo actualizado"'
}
t_admin_valida_propietario() {
  api GET "/api/v1/admin/propietarios?limite=200" - "$ADMIN_TOKEN" 200 || return 1
  PROPIETARIO_ID="$(printf '%s' "$CUERPO_RESP" | tr '}' '\n' | grep -F "\"correo\":\"$CORREO\"" \
    | grep -o '"id":[0-9]*' | head -n1 | cut -d: -f2)"
  [ -n "$PROPIETARIO_ID" ] || return 1
  api POST "/api/v1/admin/propietarios/$PROPIETARIO_ID/validacion" '{"estado":"VALIDADO"}' "$ADMIN_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"estadoValidacion":"VALIDADO"'
}
t_admin_aviso_propietario() {
  api GET /api/v1/notificaciones - "$ACCESS" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"titulo":"Propietario validado"'
}
t_admin_establecimientos() {
  api GET "/api/v1/admin/establecimientos?limite=200" - "$ADMIN_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"id\":$EST_ID,"
}
t_admin_reservas() {
  api GET "/api/v1/admin/reservas?limite=200" - "$ADMIN_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"id\":$RESERVA_CAL_ID,"
}
t_admin_pagos() {
  api GET "/api/v1/admin/pagos?limite=200" - "$ADMIN_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"idReserva\":$PAY_RESERVA_ID,"
}
t_admin_auditoria() {
  api GET "/api/v1/admin/auditoria?limite=20" - "$ADMIN_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"accion":"PROPIETARIO_VALIDADO"' &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"idEntidad\":\"$PROPIETARIO_ID\""
}
t_admin_usuarios() {
  api GET "/api/v1/admin/usuarios?correo=$CORREO" - "$ADMIN_TOKEN" 200 || return 1
  printf '%s' "$CUERPO_RESP" | grep -q "\"correo\":\"$CORREO\"" || return 1
  printf '%s' "$CUERPO_RESP" | grep -q '"PLATFORM_ADMIN"' || return 1
  USUARIO_ID="$(printf '%s' "$CUERPO_RESP" | tr '}' '\n' | grep -F "\"correo\":\"$CORREO\"" \
    | grep -o '"id":[0-9]*' | head -n1 | cut -d: -f2)"
  [ -n "$USUARIO_ID" ]
}
t_admin_roles() {
  api POST "/api/v1/admin/usuarios/$USUARIO_ID/roles" '{"roles":["USER","OWNER","PLATFORM_ADMIN"]}' "$ADMIN_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"OWNER"' &&
    # L-20: el cambio de roles revoca el token vigente; se renuevan ambos para los pasos siguientes
    admin_login &&
    api POST /api/v1/auth/login "$LOGIN" - 200 &&
    ACCESS="$(printf '%s' "$CUERPO_RESP" | json_get accessToken)" && [ -n "$ACCESS" ]
}
t_admin_estado_usuario() {
  api POST "/api/v1/admin/usuarios/$USUARIO_ID/estado" '{"estado":"INACTIVO"}' "$ADMIN_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"estado":"INACTIVO"'
}
t_admin_login_bloqueado() {
  api POST /api/v1/auth/login "$LOGIN" - 403
}
t_admin_promo_crear() {
  local codigo="HUMO$(date +%Y%m%d%H%M%S)"
  local cuerpo="{\"codigo\":\"$codigo\",\"descripcion\":\"Promocion de humo\",\"tipoDescuento\":\"PORCENTAJE\",\"valor\":10.00,\"fechaInicio\":\"$(date +%Y-%m-%d)\",\"fechaFin\":\"$(date -d '+30 days' +%Y-%m-%d)\",\"canchas\":[$CANCHA_ID]}"
  api POST /api/v1/admin/promociones "$cuerpo" "$ADMIN_TOKEN" 201 || return 1
  printf '%s' "$CUERPO_RESP" | grep -q '"estado":"ACTIVA"' || return 1
  PROMO_ID="$(printf '%s' "$CUERPO_RESP" | json_get id)"
  [ -n "$PROMO_ID" ]
}
t_admin_promo_listar() {
  api GET "/api/v1/admin/promociones?estado=ACTIVA&limite=200" - "$ADMIN_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"id\":$PROMO_ID,"
}
t_admin_promo_estado() {
  api POST "/api/v1/admin/promociones/$PROMO_ID/estado" '{"estado":"INACTIVA"}' "$ADMIN_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"estado":"INACTIVA"'
}
t_admin_cocheras() {
  api POST "/api/v1/establecimientos/$EST_ID/cocheras" '{"nombre":"Cochera admin","capacidad":6,"tarifaHora":4.50}' "$ACCESS" 201 || return 1
  COCHERA_ADMIN_ID="$(printf '%s' "$CUERPO_RESP" | json_get id)"
  [ -n "$COCHERA_ADMIN_ID" ] || return 1
  api GET "/api/v1/admin/cocheras?estado=ACTIVA&limite=200" - "$ADMIN_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"id\":$COCHERA_ADMIN_ID,"
}
t_admin_cochera_estado() {
  api POST "/api/v1/admin/cocheras/$COCHERA_ADMIN_ID/estado" '{"estado":"INACTIVA"}' "$ADMIN_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"estado":"INACTIVA"'
}
t_admin_configuracion() {
  api GET /api/v1/admin/configuracion - "$ADMIN_TOKEN" 200 || return 1
  CFG_COMISION="$(printf '%s' "$CUERPO_RESP" | json_get comisionPorcentaje)"
  CFG_HORAS_GRATIS="$(printf '%s' "$CUERPO_RESP" | json_get horasCancelacionGratis)"
  CFG_HORAS_MINIMAS="$(printf '%s' "$CUERPO_RESP" | json_get horasMinimasCancelacion)"
  CFG_DIAS="$(printf '%s' "$CUERPO_RESP" | json_get diasAnticipacionMax)"
  CFG_CORREO="$(printf '%s' "$CUERPO_RESP" | json_get correoSoporte)"
  [ -n "$CFG_COMISION" ] && [ -n "$CFG_DIAS" ] && [ -n "$CFG_CORREO" ]
}
t_admin_configuracion_actualizar() {
  api PUT /api/v1/admin/configuracion \
    '{"comisionPorcentaje":12.50,"horasCancelacionGratis":48,"horasMinimasCancelacion":3,"diasAnticipacionMax":120,"correoSoporte":"ayuda@canchadirecta.pe"}' \
    "$ADMIN_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"diasAnticipacionMax":120' &&
    printf '%s' "$CUERPO_RESP" | grep -q '"correoSoporte":"ayuda@canchadirecta.pe"'
}
t_admin_configuracion_invalida() {
  api PUT /api/v1/admin/configuracion \
    '{"comisionPorcentaje":101,"horasCancelacionGratis":24,"horasMinimasCancelacion":2,"diasAnticipacionMax":365,"correoSoporte":"soporte@canchadirecta.pe"}' \
    "$ADMIN_TOKEN" 400
}
t_admin_configuracion_restaurar() {
  api PUT /api/v1/admin/configuracion \
    "{\"comisionPorcentaje\":$CFG_COMISION,\"horasCancelacionGratis\":$CFG_HORAS_GRATIS,\"horasMinimasCancelacion\":$CFG_HORAS_MINIMAS,\"diasAnticipacionMax\":$CFG_DIAS,\"correoSoporte\":\"$CFG_CORREO\"}" \
    "$ADMIN_TOKEN" 200
}

# --- Recepcion (Bloque D, ADR-0038) -----------------------------------------
# El personal opera solo las sedes que la administracion le asigna; el rol viaja en el JWT.
# Hora de Lima = UTC-5 (sin horario de verano): la franja del walk-in es "ahora + 40 min".
recep_fecha() {
  if date -u -d '-5 hours +40 minutes' '+%Y-%m-%d' >/dev/null 2>&1; then
    date -u -d '-5 hours +40 minutes' '+%Y-%m-%d'
  else
    date -u -v-5H -v+40M '+%Y-%m-%d'
  fi
}
recep_hora() {
  if date -u -d '-5 hours +40 minutes' '+%H:%M' >/dev/null 2>&1; then
    date -u -d '-5 hours +40 minutes' '+%H:%M'
  else
    date -u -v-5H -v+40M '+%H:%M'
  fi
}
cobro_fecha() {
  if date -u -d '-5 hours +3 hours' '+%Y-%m-%d' >/dev/null 2>&1; then
    date -u -d '-5 hours +3 hours' '+%Y-%m-%d'
  else
    date -u -v-5H -v+3H '+%Y-%m-%d'
  fi
}
cobro_hora() {
  if date -u -d '-5 hours +3 hours' '+%H:%M' >/dev/null 2>&1; then
    date -u -d '-5 hours +3 hours' '+%H:%M'
  else
    date -u -v-5H -v+3H '+%H:%M'
  fi
}
t_recep_registro() {
  RECEP_CORREO="recepcion.$(date +%s%N)@canchadirecta.pe"
  RECEP_LOGIN="{\"correo\":\"$RECEP_CORREO\",\"password\":\"$PASSWORD\"}"
  api POST /api/v1/auth/register "$(printf '{"nombres":"Operador","apellidos":"Recepcion","correo":"%s","telefono":"955444333","password":"%s","confirmacionPassword":"%s"}' "$RECEP_CORREO" "$PASSWORD" "$PASSWORD")" - 201 || return 1
  RECEP_ID="$(sql_exec "SELECT id_usuario FROM usuario WHERE correo='$RECEP_CORREO'" | tr -d '\r' | head -n1)"
  [ -n "$RECEP_ID" ]
}
t_recep_sin_rol() {
  api POST /api/v1/auth/login "$RECEP_LOGIN" - 200 || return 1
  RECEP_TOKEN="$(printf '%s' "$CUERPO_RESP" | json_get accessToken)"
  [ -n "$RECEP_TOKEN" ] || return 1
  api GET /api/v1/recepcion/establecimientos - "$RECEP_TOKEN" 403
}
t_recep_asignar() {
  api POST "/api/v1/admin/establecimientos/$EST_ID/operadores" \
    "{\"idUsuario\":$RECEP_ID,\"cargo\":\"Recepcion\"}" "$ADMIN_TOKEN" 201 &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"correo\":\"$RECEP_CORREO\""
}
t_recep_login() {
  api POST /api/v1/auth/login "$RECEP_LOGIN" - 200 || return 1
  RECEP_TOKEN="$(printf '%s' "$CUERPO_RESP" | json_get accessToken)"
  [ -n "$RECEP_TOKEN" ]
}
t_recep_establecimientos() {
  api GET /api/v1/recepcion/establecimientos - "$RECEP_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"id\":$EST_ID"
}
t_recep_canchas() {
  api GET "/api/v1/recepcion/canchas?idEstablecimiento=$EST_ID" - "$RECEP_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"id\":$CANCHA_ID"
}
t_recep_resumen() {
  api GET /api/v1/recepcion/resumen - "$RECEP_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"canchasDisponibles"'
}
t_recep_clientes() {
  api GET '/api/v1/recepcion/clientes?q=Smoke' - "$RECEP_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"id\":$USUARIO_ID"
}
t_recep_walkin() {
  local cuerpo
  cuerpo="$(printf '{"idEstablecimiento":%s,"idCancha":%s,"idCliente":%s,"fecha":"%s","horaInicio":"%s","duracionMinutos":60,"pagoRecibido":true}' \
    "$EST_ID" "$CANCHA_ID" "$USUARIO_ID" "$(recep_fecha)" "$(recep_hora)")"
  api POST /api/v1/recepcion/reservas "$cuerpo" "$RECEP_TOKEN" 201 || return 1
  printf '%s' "$CUERPO_RESP" | grep -q '"pagoEstado":"APROBADO"' || return 1
  printf '%s' "$CUERPO_RESP" | grep -q '"estado":"CONFIRMADA"' || return 1
  RECEP_RESERVA_ID="$(printf '%s' "$CUERPO_RESP" | json_get id)"
  [ -n "$RECEP_RESERVA_ID" ]
}
t_recep_agenda() {
  api GET "/api/v1/recepcion/agenda?fecha=$(recep_fecha)&idEstablecimiento=$EST_ID" - "$RECEP_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q "\"id\":$RECEP_RESERVA_ID,"
}
t_recep_confirma() {
  api POST "/api/v1/reservas/$RECEP_RESERVA_ID/confirmar" - "$ACCESS" 409
}
t_recep_checkin() {
  api POST "/api/v1/recepcion/reservas/$RECEP_RESERVA_ID/checkin" - "$RECEP_TOKEN" 200 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"estado":"FINALIZADA"'
}
t_recep_checkin_repetido() {
  api POST "/api/v1/recepcion/reservas/$RECEP_RESERVA_ID/checkin" - "$RECEP_TOKEN" 409
}
t_recep_cobro() {
  local cuerpo pid
  cuerpo="$(printf '{"idEstablecimiento":%s,"idCancha":%s,"idCliente":%s,"fecha":"%s","horaInicio":"%s","duracionMinutos":60,"pagoRecibido":false}' \
    "$EST_ID" "$CANCHA_ID" "$USUARIO_ID" "$(cobro_fecha)" "$(cobro_hora)")"
  api POST /api/v1/recepcion/reservas "$cuerpo" "$RECEP_TOKEN" 201 || return 1
  pid="$(printf '%s' "$CUERPO_RESP" | json_get id)"
  [ -n "$pid" ] || return 1
  api POST "/api/v1/recepcion/reservas/$pid/cobro" '{"medioPago":"YAPE"}' "$RECEP_TOKEN" 200 || return 1
  printf '%s' "$CUERPO_RESP" | grep -q '"pagoEstado":"APROBADO"' || return 1
  printf '%s' "$CUERPO_RESP" | grep -q '"estado":"CONFIRMADA"' || return 1
  api POST "/api/v1/recepcion/reservas/$pid/cobro" '{"medioPago":"EFECTIVO"}' "$RECEP_TOKEN" 409
}
t_recep_incidencias() {
  api POST /api/v1/recepcion/incidencias \
    "{\"asunto\":\"Cancha mojada\",\"descripcion\":\"Se solicito mantenimiento.\",\"idReserva\":$RECEP_RESERVA_ID}" \
    "$RECEP_TOKEN" 201 || return 1
  printf '%s' "$CUERPO_RESP" | grep -q '"estado":"ABIERTO"' || return 1
  api GET /api/v1/recepcion/incidencias - "$RECEP_TOKEN" 200
}
t_recep_sin_token() {
  api GET /api/v1/recepcion/establecimientos - - 401
}
t_recep_remover() {
  api DELETE "/api/v1/admin/establecimientos/$EST_ID/operadores/$RECEP_ID" - "$ADMIN_TOKEN" 200 || return 1
  printf '%s' "$CUERPO_RESP" | grep -q '^\[\]$'
}
t_recep_sin_asignacion() {
  api GET "/api/v1/recepcion/agenda?idEstablecimiento=$EST_ID" - "$RECEP_TOKEN" 403
}

# --- Promociones del propietario aplicadas a la reserva (CU04.6) ------------
t_promo_propietario() {
  PROMO_CODIGO="HUMO$(date +%m%d%H%M%S)"
  local cuerpo="{\"codigo\":\"$PROMO_CODIGO\",\"descripcion\":\"Promocion de humo 10%\",\"tipoDescuento\":\"PORCENTAJE\",\"valor\":10.00,\"fechaInicio\":\"$(date +%Y-%m-%d)\",\"fechaFin\":\"$(date -d '+30 days' +%Y-%m-%d)\"}"
  api POST "/api/v1/establecimientos/$EST_ID/promociones" "$cuerpo" "$ACCESS" 201 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"estado":"ACTIVA"'
}
t_reserva_promo() {
  local cuerpo="{\"canchaId\":$CANCHA_ID,\"inicio\":\"$INICIO_PROMO\",\"fin\":\"$FIN_PROMO\",\"codigoPromocion\":\"$PROMO_CODIGO\"}"
  api POST /api/v1/reservas "$cuerpo" "$ACCESS" 201 &&
    printf '%s' "$CUERPO_RESP" | grep -q '"montoDescuento":8'
}

REG="{\"nombres\":\"Smoke\",\"apellidos\":\"Test\",\"correo\":\"$CORREO\",\"telefono\":\"900000000\",\"password\":\"$PASSWORD\",\"confirmacionPassword\":\"$PASSWORD\"}"
LOGIN="{\"correo\":\"$CORREO\",\"password\":\"$PASSWORD\"}"
DOCUMENTO="7$(date +%s%N | cut -c11-18)"
EST="{\"documentoIdentidad\":\"$DOCUMENTO\",\"idDistrito\":$ID_DISTRITO,\"nombre\":\"Complejo Smoke $RANDOM\",\"direccion\":\"Av. Prueba 123\",\"referencia\":\"Prueba de humo\"}"
CANCHA="{\"nombre\":\"Cancha Smoke $RANDOM\",\"descripcion\":\"Futbol 7\",\"capacidad\":14,\"precioBase\":80.00}"
FECHA="$(fecha_franja)"
DIA_SEMANA="$(dia_semana_franja)"
INICIO="${FECHA}T17:00:00Z"
FIN="${FECHA}T18:00:00Z"
INICIO2="${FECHA}T18:00:00Z"
FIN2="${FECHA}T19:00:00Z"
INICIO_COCHERA="${FECHA}T20:00:00Z"
FIN_COCHERA="${FECHA}T21:00:00Z"
INICIO_PAGO="${FECHA}T21:00:00Z"
FIN_PAGO="${FECHA}T22:00:00Z"
INICIO_LIQ="${FECHA}T22:00:00Z"
FIN_LIQ="${FECHA}T23:00:00Z"
INICIO_REV="${FECHA}T16:00:00Z"
FIN_REV="${FECHA}T17:00:00Z"
INICIO_CAL="${FECHA}T19:00:00Z"
FIN_CAL="${FECHA}T20:00:00Z"
INICIO_PROMO="${FECHA}T15:00:00Z"
FIN_PROMO="${FECHA}T16:00:00Z"
PERIODO_INICIO="$FECHA"
PERIODO_FIN="$FECHA"
ACCESS=""; REFRESH=""; ROTADO=""; EST_ID=""; CANCHA_ID=""; RESERVA_ID=""
HORARIO_ID=""; BLOQUEO_ID=""; RESERVA2_ID=""; COCHERA_ID=""; RESERVA_COCHERA_ID=""; PAY_RESERVA_ID=""
LIQ_RESERVA_ID=""; LIQUIDACION_ID=""; RESERVA_REV_ID=""; RESERVA_CAL_ID=""
RECLAMO_ID=""; NOTIF_ID=""; NO_LEIDAS=0; ADMIN_TOKEN=""; PROPIETARIO_ID=""; USUARIO_ID=""; PROMO_ID=""; COCHERA_ADMIN_ID=""; PROMO_CODIGO=""; DEPORTE_ID=""
RECEP_CORREO=""; RECEP_ID=""; RECEP_TOKEN=""; RECEP_LOGIN=""; RECEP_RESERVA_ID=""

# El cuerpo de la reserva se construye en cada llamada porque necesita el id de la cancha.
cuerpo_reserva() { printf '{"canchaId":%s,"inicio":"%s","fin":"%s"}' "$CANCHA_ID" "$INICIO" "$FIN"; }
cuerpo_reserva2() { printf '{"canchaId":%s,"inicio":"%s","fin":"%s"}' "$CANCHA_ID" "$INICIO2" "$FIN2"; }
cuerpo_reserva_cochera() { printf '{"canchaId":%s,"inicio":"%s","fin":"%s","cocheraId":%s}' "$CANCHA_ID" "$INICIO_COCHERA" "$FIN_COCHERA" "$COCHERA_ID"; }
cuerpo_reserva_pago() { printf '{"canchaId":%s,"inicio":"%s","fin":"%s"}' "$CANCHA_ID" "$INICIO_PAGO" "$FIN_PAGO"; }
cuerpo_reserva_liquidacion() { printf '{"canchaId":%s,"inicio":"%s","fin":"%s"}' "$CANCHA_ID" "$INICIO_LIQ" "$FIN_LIQ"; }
cuerpo_liquidacion() { printf '{"periodoInicio":"%s","periodoFin":"%s"}' "$PERIODO_INICIO" "$PERIODO_FIN"; }
cuerpo_reserva_revocacion() { printf '{"canchaId":%s,"inicio":"%s","fin":"%s"}' "$CANCHA_ID" "$INICIO_REV" "$FIN_REV"; }
cuerpo_reserva_calificacion() { printf '{"canchaId":%s,"inicio":"%s","fin":"%s"}' "$CANCHA_ID" "$INICIO_CAL" "$FIN_CAL"; }

paso 'GET /api/v1/system/ping'            t_ping
paso 'GET /q/health/ready'                t_health
paso 'POST /api/v1/auth/register (201)'   t_registro
paso 'POST /api/v1/auth/register (409)'   t_duplicado
paso 'POST /api/v1/auth/login (200)'      t_login
paso 'GET  /api/v1/users/me sin token'    t_me_sin
paso 'GET  /api/v1/users/me con token'    t_me_con
paso 'POST /api/v1/auth/refresh (rota)'   t_refresh
paso 'POST /api/v1/auth/refresh reuso'    t_reuso
paso 'POST /api/v1/auth/logout (204)'     t_logout
paso 'POST password-reset/request (202)'  t_reset
paso 'POST password-reset inexistente'    t_reset_na
paso 'POST /api/v1/establecimientos'      t_establecimiento
paso 'POST establecimientos/{id}/canchas' t_cancha
paso 'GET  /api/v1/canchas'               t_canchas
paso 'GET  /api/v1/ubicaciones/distritos' t_catalogo_distritos
paso 'GET  /api/v1/deportes'              t_catalogo_deportes
paso 'PUT  canchas/{id}/deportes'         t_cancha_deportes
paso 'POST canchas/{id}/fotos'            t_cancha_foto
paso 'GET  canchas/{id} (ficha)'          t_cancha_detalle
paso 'GET  canchas con filtros'           t_cancha_filtro
paso 'GET  canchas/{id}/disponibilidad'   t_disponibilidad
paso 'POST /api/v1/reservas (201)'        t_reserva
paso 'POST /api/v1/reservas duplicada'    t_reserva_dup
paso 'POST promociones del propietario'   t_promo_propietario
paso 'POST /api/v1/reservas con promo'    t_reserva_promo
paso 'GET  disponibilidad ocupada (RESERVADA)' t_disponibilidad_reservada
paso 'GET  /api/v1/reservas/mias'         t_reservas_mias
paso 'GET  reservas/recibidas (PENDIENTE)' t_reservas_recibidas
paso 'POST reservas/{id}/confirmar'       t_confirmar
paso 'POST reservas/{id}/confirmar 409'   t_confirmar_dup
paso 'POST /api/v1/reservas contigua'     t_reserva2
paso 'POST reservas/{id2}/rechazar'       t_rechazar
paso 'GET  disponibilidad franja rechazada' t_disponibilidad2
paso 'POST /api/v1/reservas sin token'    t_reserva_sin_tok
paso 'POST /api/v1/reservas/{id}/cancelar'      t_cancelar
paso 'POST /api/v1/reservas/{id}/cancelar 409'  t_cancelar_dup
paso 'GET  disponibilidad liberada'       t_disponibilidad
paso 'POST canchas/{id}/horarios (201)'   t_horario
paso 'GET  canchas/{id}/horarios'         t_horarios_lista
paso 'POST canchas/{id}/horarios solapado' t_horario_solapado
paso 'GET  disponibilidad con horario'    t_disponibilidad
paso 'POST canchas/{id}/bloqueos (201)'   t_bloqueo
paso 'GET  disponibilidad bloqueada (BLOQUEADA)' t_disponibilidad_bloqueada
paso 'POST reservas en franja bloqueada'  t_bloqueo_reserva
paso 'DELETE canchas/{id}/bloqueos/{id}'  t_bloqueo_levanta
paso 'GET  disponibilidad sin bloqueo'    t_disponibilidad
paso 'POST establecimientos/{id}/cocheras' t_cochera
paso 'GET  establecimientos/{id}/cocheras' t_cocheras_lista
paso 'GET  canchas/{id}/cocheras cupo 2'  t_cupo_cochera
paso 'POST /api/v1/reservas con cochera'  t_reserva_cochera
paso 'GET  cocheras cupo baja a 1'        t_cupo_cochera_1
paso 'POST reservas/{id}/cancelar cochera' t_cancelar_cochera
paso 'GET  cocheras cupo vuelve a 2'      t_cupo_cochera_2
paso 'DELETE establecimientos/{id}/cocheras/{id}' t_cochera_baja
paso 'POST /api/v1/reservas para pagar'   t_reserva_pago
paso 'POST reservas/{id}/pago (APROBADO)' t_pago
paso 'GET  reservas/{id}/pago'            t_pago_consulta
paso 'POST reservas/{id}/pago de nuevo'   t_pago_dup
paso 'POST reservas/{id}/cancelar (pago)' t_pago_cancelar
paso 'GET  reservas/{id}/pago reembolsado' t_pago_reembolsado
paso 'POST /api/v1/reservas para liquidar' t_reserva_liquidacion
paso 'POST reservas/{id}/pago (liquidar)'  t_pago_liquidacion
paso 'POST reservas/{id}/confirmar (liq)'  t_confirmar_liquidacion
paso 'POST /api/v1/liquidaciones (201)'    t_liquidacion
paso 'POST /api/v1/liquidaciones de nuevo' t_liquidacion_dup
paso 'GET  liquidaciones/{id} detalle'     t_liquidacion_detalle
paso 'POST /api/v1/reservas para revocar'   t_reserva_revocacion
paso 'POST reservas/{id}/confirmar (rev)'   t_confirmar_revocacion
paso 'POST reservas/{id}/revocar (L-14)'    t_revocar
paso 'POST reservas/{id}/revocar de nuevo'  t_revocar_dup

if [ "$DB_DISPONIBLE" -eq 1 ]; then
  paso 'POST /api/v1/reservas para calificar'  t_reserva_calificacion
  paso 'POST reservas/{id}/confirmar (cal)'    t_confirmar_calificacion
  paso 'Job de cierre deja FINALIZADA (L-15)'  t_finalizar_calificacion
  paso 'POST reservas/{id}/calificacion'       t_calificar
  paso 'POST reservas/{id}/calificacion 409'   t_calificar_dup
  paso 'GET  canchas/{id}/calificaciones'      t_calificaciones_cancha
else
  printf '\033[33m[SKIP] Calificaciones: falta el cliente mysql o DB_USER/DB_PASSWORD en el entorno/.env\033[0m\n'
fi

# --- Soporte del usuario: reclamos y avisos in-app (CU06.6, F06) -------------
paso 'POST /api/v1/reclamos (ABIERTO)'        t_reclamo_crear
paso 'GET  /api/v1/reclamos/mios'             t_reclamo_listar
paso 'POST reclamos/{id}/seguimientos'        t_reclamo_comentar
paso 'GET  reclamos/{id} con seguimiento'     t_reclamo_detalle
paso 'GET  /api/v1/notificaciones'            t_notificaciones
paso 'POST notificaciones/{id}/leida'         t_notificacion_leida

if [ "$DB_DISPONIBLE" -eq 1 ]; then
  if admin_login; then
    paso 'GET  /api/v1/admin/reclamos (admin)'   t_admin_lista
    paso 'GET  admin/reclamos con token USER'    t_admin_lista_user
    paso 'POST admin/reclamos/{id}/estado'       t_admin_estado
    paso 'POST admin/reclamos/{id}/seguimiento'  t_admin_responde
    paso 'GET  admin/reclamos/{id} detalle'      t_admin_detalle
    paso 'GET  notificaciones tras moderar'      t_admin_avisos
    paso 'POST admin/propietarios/{id}/validar'  t_admin_valida_propietario
    paso 'GET  aviso de propietario validado'    t_admin_aviso_propietario
    paso 'GET  admin/establecimientos'           t_admin_establecimientos
    paso 'GET  admin/reservas'                   t_admin_reservas
    paso 'GET  admin/pagos'                      t_admin_pagos
    paso 'GET  admin/auditoria'                  t_admin_auditoria
    paso 'GET  admin/usuarios'                   t_admin_usuarios
    paso 'POST admin/usuarios/{id}/roles'        t_admin_roles
    # Recepcion (Bloque D, ADR-0038): vincular personal y operar su turno.
    paso 'POST auth/register operador recepcion'  t_recep_registro
    paso 'GET  recepcion sin rol (403)'           t_recep_sin_rol
    paso 'POST admin/establecimientos operadores' t_recep_asignar
    paso 'POST auth/login operador (OPERADOR)'    t_recep_login
    paso 'GET  recepcion/establecimientos'        t_recep_establecimientos
    paso 'GET  recepcion/canchas'                 t_recep_canchas
    paso 'GET  recepcion/resumen'                 t_recep_resumen
    paso 'GET  recepcion/clientes'                t_recep_clientes
    paso 'POST recepcion/reservas (walk-in pagado)' t_recep_walkin
    paso 'GET  recepcion/agenda'                  t_recep_agenda
    paso 'POST reservas/{id}/confirmar (ya confirmada)' t_recep_confirma
    paso 'POST recepcion/{id}/checkin'            t_recep_checkin
    paso 'POST recepcion/{id}/checkin repetido'   t_recep_checkin_repetido
    paso 'POST recepcion/{id}/cobro'              t_recep_cobro
    paso 'POST recepcion/incidencias'             t_recep_incidencias
    paso 'GET  recepcion sin token (401)'         t_recep_sin_token
    paso 'DELETE admin operadores/{id}'           t_recep_remover
    paso 'GET  recepcion tras quitar asignacion'  t_recep_sin_asignacion
    paso 'POST admin/promociones'                t_admin_promo_crear
    paso 'GET  admin/promociones'                t_admin_promo_listar
    paso 'POST admin/promociones/{id}/estado'    t_admin_promo_estado
    paso 'GET  admin/cocheras'                   t_admin_cocheras
    paso 'POST admin/cocheras/{id}/estado'       t_admin_cochera_estado
    paso 'GET  admin/configuracion'              t_admin_configuracion
    paso 'PUT  admin/configuracion'              t_admin_configuracion_actualizar
    paso 'PUT  admin/configuracion invalida'     t_admin_configuracion_invalida
    paso 'PUT  admin/configuracion restaura'     t_admin_configuracion_restaurar
    # CU04.1/CU04.2/CU02.2 antes de desactivar la cuenta de humo
    paso 'POST establecimiento gestion (CU04)'      t_gestion_est
    paso 'POST cancha gestion (CU04)'               t_gestion_cancha
    paso 'PUT  establecimiento gestion'             t_gestion_editar_est
    paso 'PUT  cancha gestion'                      t_gestion_editar_cancha
    paso 'GET  canchas/comparar (CU02.2)'           t_gestion_comparar
    paso 'DELETE cancha gestion (CU04.2)'           t_gestion_baja_cancha
    paso 'GET  cancha dada de baja (INACTIVA)'      t_gestion_baja_cancha_estado
    paso 'DELETE establecimiento gestion (CU04.2)'  t_gestion_baja_est
    paso 'POST admin/usuarios/{id}/estado'       t_admin_estado_usuario
    paso 'POST login con cuenta inactiva'        t_admin_login_bloqueado
  else
    rojo '[FAIL] preparacion PLATFORM_ADMIN (rol en usuario_rol)'
    FALLOS=$((FALLOS+1))
  fi
else
  printf '\033[33m[SKIP] Moderacion: falta el cliente mysql o DB_USER/DB_PASSWORD en el entorno/.env\033[0m\n'
fi

echo
if [ "$FALLOS" -eq 0 ]; then verde "RESULTADO: OK ($PASOS/$PASOS pasos)"; exit 0; fi
rojo "RESULTADO: FALLO ($FALLOS de $PASOS pasos)"; exit 1
