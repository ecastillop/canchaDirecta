export interface ResumenMetricas {
  totalReservas: number;
  confirmadas: number;
  pendientes: number;
  canceladas: number;
  ingresos: number;
  horasReservadas: number;
  ocupacionPorcentaje: number;
  canchasActivas: number;
}

export interface PuntoDiaMetrica {
  fecha: string;
  reservas: number;
  ingresos: number;
}

export interface CanchaMetrica {
  idCancha: number;
  nombre: string;
  reservas: number;
  ingresos: number;
}

export interface ReservaProxima {
  idReserva: number;
  cliente: string;
  cancha: string;
  inicio: string;
  fin: string;
  estado: 'PENDIENTE' | 'CONFIRMADA';
  monto: number;
}

export interface MetricasEstablecimiento {
  idEstablecimiento: number;
  dias: number;
  desde: string;
  hasta: string;
  resumen: ResumenMetricas;
  serie: PuntoDiaMetrica[];
  canchas: CanchaMetrica[];
  proximas: ReservaProxima[];
}
