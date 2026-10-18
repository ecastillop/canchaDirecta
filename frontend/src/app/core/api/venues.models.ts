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

export interface Disponibilidad {
  canchaId: number;
  fecha: string;
  zonaHoraria: string;
  duracionMinutos: number;
  franjas: FranjaDisponible[];
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
  limite?: number;
  desplazamiento?: number;
}
