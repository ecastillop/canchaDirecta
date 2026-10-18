export interface Cochera {
  id: number;
  idEstablecimiento: number;
  nombre: string;
  capacidad: number;
  tarifaHora: number;
  estado: 'ACTIVA' | 'INACTIVA';
}

export interface BloqueoCancha {
  id: number;
  idCancha: number;
  inicio: string;
  fin: string;
  motivo: string;
}

export interface Establecimiento {
  id: number;
  idPropietario: number;
  idDistrito: number;
  nombre: string;
  direccion: string;
  referencia: string | null;
  estado: 'ACTIVO' | 'INACTIVO';
}

export interface Promocion {
  id: number;
  codigo: string;
  descripcion: string;
  tipoDescuento: 'PORCENTAJE' | 'MONTO';
  valor: number;
  fechaInicio: string;
  fechaFin: string;
  estado: 'ACTIVA' | 'INACTIVA';
  limiteUsos: number | null;
  limitePorUsuario: number | null;
  usos: number;
  canchas: Array<{ id: number; nombre: string }>;
}

export interface LiquidacionDetalle {
  reservaId: number;
  montoReserva: number;
  porcentajeComision: number;
  montoComision: number;
  montoNeto: number;
}

export interface Liquidacion {
  id: number;
  periodoInicio: string;
  periodoFin: string;
  montoBruto: number;
  montoComision: number;
  montoNeto: number;
  estado: 'PENDIENTE' | 'PROCESADA' | 'PAGADA';
  detalles: LiquidacionDetalle[];
}
