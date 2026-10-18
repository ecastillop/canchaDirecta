export interface Actividad {
  id: number;
  idEstablecimiento: number;
  establecimiento: string;
  distrito: string;
  nombre: string;
  descripcion: string | null;
  inicio: string;
  fin: string;
  cupos: number;
  inscritos: number;
  cuposDisponibles: number;
  precio: number;
  estado: 'ACTIVA' | 'CANCELADA';
}

export interface Inscripcion {
  id: number;
  idActividad: number;
  actividad: string;
  establecimiento: string;
  inicio: string;
  fin: string;
  estado: 'CONFIRMADA' | 'CANCELADA';
  fechaRegistro: string;
  nombreUsuario: string | null;
  correoUsuario: string | null;
}

export interface ActividadPayload {
  nombre: string;
  descripcion?: string | null;
  inicio: string;
  fin: string;
  cupos: number;
  precio: number;
}
