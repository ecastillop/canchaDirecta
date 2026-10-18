import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiUrl } from '../http/api-url';
import { Actividad, ActividadPayload, Inscripcion } from './actividades.models';

@Injectable({ providedIn: 'root' })
export class ActividadesApi {
  private readonly http = inject(HttpClient);

  publicas(desde?: string, limite = 30): Observable<Actividad[]> {
    let params = new HttpParams().set('limite', limite);
    if (desde) {
      params = params.set('desde', desde);
    }
    return this.http.get<Actividad[]>(apiUrl('/actividades'), { params });
  }

  detalle(id: number): Observable<Actividad> {
    return this.http.get<Actividad>(apiUrl(`/actividades/${id}`));
  }

  delEstablecimiento(idEstablecimiento: number): Observable<Actividad[]> {
    return this.http.get<Actividad[]>(apiUrl(`/establecimientos/${idEstablecimiento}/actividades`));
  }

  crear(idEstablecimiento: number, payload: ActividadPayload): Observable<Actividad> {
    return this.http.post<Actividad>(apiUrl(`/establecimientos/${idEstablecimiento}/actividades`), payload);
  }

  actualizar(id: number, payload: ActividadPayload): Observable<Actividad> {
    return this.http.put<Actividad>(apiUrl(`/actividades/${id}`), payload);
  }

  cancelar(id: number): Observable<void> {
    return this.http.delete<void>(apiUrl(`/actividades/${id}`));
  }

  inscribir(id: number): Observable<Inscripcion> {
    return this.http.post<Inscripcion>(apiUrl(`/actividades/${id}/inscripciones`), {});
  }

  cancelarInscripcion(id: number): Observable<void> {
    return this.http.delete<void>(apiUrl(`/actividades/${id}/inscripciones`));
  }

  misInscripciones(): Observable<Inscripcion[]> {
    return this.http.get<Inscripcion[]>(apiUrl('/inscripciones/mias'));
  }

  inscritos(id: number): Observable<Inscripcion[]> {
    return this.http.get<Inscripcion[]>(apiUrl(`/actividades/${id}/inscripciones`));
  }
}
