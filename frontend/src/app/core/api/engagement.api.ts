import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiUrl } from '../http/api-url';

export interface Notificacion {
  id: number;
  titulo: string;
  mensaje: string;
  leida: boolean;
  fechaEnvio: string;
}

export interface NotificacionesResponse {
  noLeidas: number;
  total: number;
  notificaciones: Notificacion[];
}

export interface Reclamo {
  id: number;
  idReserva: number | null;
  asunto: string;
  descripcion: string;
  estado: 'ABIERTO' | 'EN_REVISION' | 'RESUELTO' | 'CERRADO';
  fechaRegistro: string;
}

export interface Seguimiento {
  id: number;
  comentario: string;
  fecha: string;
}

export interface ReclamoDetalle extends Reclamo {
  seguimientos: Seguimiento[];
}

export interface Calificacion {
  id: number;
  reservaId: number;
  puntaje: number;
  comentario: string | null;
  fechaRegistro: string;
}

export interface CalificacionesCancha {
  canchaId: number;
  total: number;
  promedio: number;
  calificaciones: Calificacion[];
}

@Injectable({ providedIn: 'root' })
export class EngagementApi {
  private readonly http = inject(HttpClient);

  calificarReserva(idReserva: number, puntaje: number, comentario?: string): Observable<Calificacion> {
    return this.http.post<Calificacion>(apiUrl(`/reservas/${idReserva}/calificacion`), {
      puntaje,
      comentario: comentario ?? null,
    });
  }

  calificacionesCancha(idCancha: number, limite = 20): Observable<CalificacionesCancha> {
    return this.http.get<CalificacionesCancha>(apiUrl(`/canchas/${idCancha}/calificaciones`), {
      params: new HttpParams().set('limite', limite),
    });
  }

  notificaciones(limite = 50): Observable<NotificacionesResponse> {
    return this.http.get<NotificacionesResponse>(apiUrl('/notificaciones'), {
      params: new HttpParams().set('limite', limite),
    });
  }

  marcarLeida(id: number): Observable<void> {
    return this.http.post<void>(apiUrl(`/notificaciones/${id}/leida`), {});
  }

  misReclamos(limite = 50): Observable<Reclamo[]> {
    return this.http.get<Reclamo[]>(apiUrl('/reclamos/mios'), {
      params: new HttpParams().set('limite', limite),
    });
  }

  reclamo(id: number): Observable<ReclamoDetalle> {
    return this.http.get<ReclamoDetalle>(apiUrl(`/reclamos/${id}`));
  }

  crearReclamo(payload: { idReserva?: number | null; asunto: string; descripcion: string }): Observable<Reclamo> {
    return this.http.post<Reclamo>(apiUrl('/reclamos'), payload);
  }

  comentar(id: number, comentario: string): Observable<Seguimiento> {
    return this.http.post<Seguimiento>(apiUrl(`/reclamos/${id}/seguimientos`), { comentario });
  }
}
