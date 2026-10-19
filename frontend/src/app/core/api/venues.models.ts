export interface Item {
  id: number;
  nombre: string;
}

export interface Distrito {
  id: number;
  nombre: string;
  provincia: string;
  departamento: string;
}

export interface Cancha {
  id: number;
  idEstablecimiento: number;
  nombre: string;
  capacidad: number;
  precioBase: number;
  estado: 'ACTIVA' | 'INACTIVA' | 'MANTENIMIENTO';
  idDistrito: number;
  distrito: string;
  establecimiento: string;
  fotoPrincipal: string | null;
  promedioCalificacion: number;
  totalCalificaciones: number;
  deportes: string[];
}

export interface Foto {
  id: number;
  url: string;
  orden: number;
  principal: boolean;
}

export interface Caracteristica {
  nombre: string;
  valor: string;
}

export interface Reputacion {
  total: number;
  promedio: number;
}

export interface CanchaDetalle {
  id: number;
  nombre: string;
  descripcion: string | null;
  capacidad: number;
  precioBase: number;
  estado: 'ACTIVA' | 'INACTIVA' | 'MANTENIMIENTO';
  idEstablecimiento: number;
  establecimiento: string;
  direccion: string;
  referencia: string | null;
  distrito: Distrito;
  deportes: Item[];
  caracteristicas: Caracteristica[];
  fotos: Foto[];
  reputacion: Reputacion;
}

export interface CanchaEspacio {
  id: number;
  nombre: string;
  capacidad: number;
  precioBase: number;
  estado: 'ACTIVA' | 'INACTIVA' | 'MANTENIMIENTO';
  deportes: Item[];
  fotoPrincipal: string | null;
  reputacion: Reputacion;
}

export interface FichaEspacio {
  id: number;
  nombre: string;
  direccion: string;
  referencia: string | null;
  estado: 'ACTIVO' | 'INACTIVO';
  distrito: Distrito;
  servicios: Caracteristica[];
  fotos: Foto[];
  reputacion: Reputacion;
  precioDesde: number | null;
  canchas: CanchaEspacio[];
}

export interface HorarioCancha {
  id: number;
  idCancha: number;
  diaSemana: number;
  horaInicio: string;
  horaFin: string;
  precio: number | null;
  activo: boolean;
}

export interface FranjaDisponible {
  inicio: string;
  fin: string;
  precio: number;
}

/**
 * Tramo que no se ofrece y por que: una reserva que retiene cupo (`RESERVADA`) o un bloqueo
 * operativo (`BLOQUEADA`). Es todo lo que la API expone de una franja ocupada (ADR-0047).
 */
export interface FranjaOcupada {
  inicio: string;
  fin: string;
  estado: 'RESERVADA' | 'BLOQUEADA';
}

export interface Disponibilidad {
  canchaId: number;
  fecha: string;
  zonaHoraria: string;
  duracionMinutos: number;
  franjas: FranjaDisponible[];
  ocupadas: FranjaOcupada[];
}

export interface CocheraDisponible {
  id: number;
  nombre: string;
  capacidad: number;
  cupoDisponible: number;
  tarifaHora: number;
}

export interface PaginaCanchas {
  items: Cancha[];
  total: number;
}

export interface FiltrosCancha {
  idDistrito?: number | null;
  idEstablecimiento?: number | null;
  idDeporte?: number | null;
  precioMax?: number | null;
  q?: string | null;
  orden?: 'destacados' | 'precio' | 'valoracion' | null;
  limite?: number;
  desplazamiento?: number;
}
