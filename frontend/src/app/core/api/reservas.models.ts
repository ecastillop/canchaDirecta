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
