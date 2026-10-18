/**
 * Formato de horas en la zona de operacion del negocio.
 *
 * Las reservas y los bloqueos son instantes (UTC); mostrarlos con la zona del navegador
 * cambiaria la hora que ve el usuario segun donde este. Se formatean siempre en
 * `America/Lima` (misma zona que usa el backend, `cd.zona-horaria`).
 */
const ZONA = 'America/Lima';

const FORMATO_HORA = new Intl.DateTimeFormat('en-GB', {
  timeZone: ZONA,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/** Hora local del negocio (`HH:mm`) de un instante ISO o de un `Date`. */
export function horaLima(instante: string | Date): string {
  return FORMATO_HORA.format(typeof instante === 'string' ? new Date(instante) : instante);
}

/** Desplazamiento de la zona de operacion respecto de UTC, para construir instantes. */
export const DESFASE_LIMA = '-05:00';
