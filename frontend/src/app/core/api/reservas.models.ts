export type EstadoReserva = 'PENDIENTE' | 'CONFIRMADA' | 'CANCELADA' | 'RECHAZADA' | 'EXPIRADA' | 'FINALIZADA';

export interface Reserva {
  id: number;
  canchaId: number;
  inicio: string;
  fin: string;
  montoCancha: number;
  montoDescuento: number;
  estado: EstadoReserva;
  fechaCreacion: string;
  cocheraId: number | null;
  montoCochera: number | null;
}

/**
 * Reserva tal como la ve el propietario en su bandeja: agrega el titular y el estado de su
 * ultimo pago (ADR-0044). Solo la devuelve `GET /reservas/recibidas`; la vista del propio
 * cliente (`/reservas/mias`) no expone datos de contacto.
 */
export interface ReservaRecibida extends Reserva {
  cliente: string | null;
  telefono: string | null;
  pagoEstado: Pago['estado'] | null;
}

export interface CrearReservaPayload {
  canchaId: number;
  inicio: string;
  fin: string;
  cocheraId?: number | null;
  codigoPromocion?: string | null;
}

export interface Reembolso {
  id: number;
  monto: number;
  motivo: string;
  estado: 'SOLICITADO' | 'APROBADO' | 'RECHAZADO' | 'PROCESADO';
  fechaSolicitud: string;
  fechaResolucion: string | null;
}

export interface Pago {
  id: number;
  reservaId: number;
  monto: number;
  medioPago: string;
  codigoOperacion: string | null;
  estado: 'PENDIENTE' | 'APROBADO' | 'RECHAZADO' | 'REEMBOLSADO';
  fechaPago: string;
  reembolsos: Reembolso[];
}

export interface PagoPayload {
  medioPago: string;
  codigoOperacion?: string | null;
}
