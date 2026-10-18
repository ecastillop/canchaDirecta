/**
 * Fechas y horas en la zona de operacion del negocio.
 *
 * Las reservas, los bloqueos y las actividades son instantes (UTC); leerlos con la zona del
 * navegador cambiaria la hora que ve el usuario segun donde este. Se calculan y muestran siempre
 * en `America/Lima` (la misma zona que usa el backend, `cd.zona-horaria`). Los ayudantes de semana
 * trabajan sobre fechas `yyyy-MM-dd` como texto, sin pasar por la zona del navegador.
 */
const ZONA = 'America/Lima';

/** Desplazamiento de la zona de operacion respecto de UTC, para construir instantes. */
export const DESFASE_LIMA = '-05:00';

export const DIAS = ['', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];
export const DIAS_CORTOS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
export const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
export const MESES_LARGOS = [
  'enero',
  'febrero',
  'marzo',
  'abril',
  'mayo',
  'junio',
  'julio',
  'agosto',
  'septiembre',
  'octubre',
  'noviembre',
  'diciembre',
];

const FORMATO_HORA = new Intl.DateTimeFormat('en-GB', {
  timeZone: ZONA,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

const FORMATO_FECHA = new Intl.DateTimeFormat('en-CA', {
  timeZone: ZONA,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

/** Hora local del negocio (`HH:mm`) de un instante ISO o de un `Date`. */
export function horaLima(instante: string | Date): string {
  return FORMATO_HORA.format(typeof instante === 'string' ? new Date(instante) : instante);
}

/** Fecha local del negocio (`yyyy-MM-dd`) de un instante; sin argumento, hoy. */
export function fechaLima(instante: Date = new Date()): string {
  return FORMATO_FECHA.format(instante);
}

/** Fecha local del negocio de hoy (`yyyy-MM-dd`). */
export function hoyLima(): string {
  return fechaLima();
}

/** Suma dias a una fecha `yyyy-MM-dd` sin depender de la zona del navegador. */
export function sumarDias(fecha: string, dias: number): string {
  const valor = new Date(`${fecha}T12:00:00Z`);
  valor.setUTCDate(valor.getUTCDate() + dias);
  return valor.toISOString().slice(0, 10);
}

/** Lunes de la semana de una fecha `yyyy-MM-dd`. */
export function lunesDe(fecha: string): string {
  const valor = new Date(`${fecha}T12:00:00Z`);
  return sumarDias(fecha, -((valor.getUTCDay() + 6) % 7));
}

/** Indice del dia (0 = lunes), numero, mes y anio de una fecha `yyyy-MM-dd`. */
export function partesDeFecha(fecha: string): { indice: number; numero: number; mes: number; anio: number } {
  const valor = new Date(`${fecha}T12:00:00Z`);
  return {
    indice: (valor.getUTCDay() + 6) % 7,
    numero: valor.getUTCDate(),
    mes: valor.getUTCMonth(),
    anio: valor.getUTCFullYear(),
  };
}

/** Etiqueta legible de la semana que empieza en `inicio`, por ejemplo `12 – 18 oct 2026`. */
export function etiquetaDeSemana(inicio: string): string {
  const fin = sumarDias(inicio, 6);
  const a = partesDeFecha(inicio);
  const b = partesDeFecha(fin);
  if (a.mes === b.mes && a.anio === b.anio) {
    return `${a.numero} – ${b.numero} ${MESES[a.mes]} ${b.anio}`;
  }
  return `${a.numero} ${MESES[a.mes]} – ${b.numero} ${MESES[b.mes]} ${b.anio}`;
}

/** 'HH:mm[:ss]' de un horario configurado a minutos del dia. */
export function minutosDe(hora: string): number {
  const [h, m] = hora.slice(0, 5).split(':');
  return Number(h) * 60 + Number(m);
}

/** Minutos del dia a 'HH:mm'. */
export function horaDeMinutos(minutos: number): string {
  const h = String(Math.floor(minutos / 60)).padStart(2, '0');
  const m = String(minutos % 60).padStart(2, '0');
  return `${h}:${m}`;
}
