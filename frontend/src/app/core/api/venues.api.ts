import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { apiUrl } from '../http/api-url';
import {
  Cancha,
  CanchaDetalle,
  CocheraDisponible,
  Disponibilidad,
  Distrito,
  FiltrosCancha,
  HorarioCancha,
  Item,
  PaginaCanchas,
} from './venues.models';

/** Catalogo publico, detalle de cancha, agenda y disponibilidad. */
@Injectable({ providedIn: 'root' })
export class VenuesApi {
  private readonly http = inject(HttpClient);

  distritos(q?: string): Observable<Distrito[]> {
    let params = new HttpParams();
    if (q) {
      params = params.set('q', q);
    }
    return this.http.get<Distrito[]>(apiUrl('/ubicaciones/distritos'), { params });
  }

  deportes(): Observable<Item[]> {
    return this.http.get<Item[]>(apiUrl('/deportes'));
  }

  caracteristicas(): Observable<Item[]> {
    return this.http.get<Item[]>(apiUrl('/caracteristicas'));
  }

  canchas(filtros: FiltrosCancha = {}): Observable<Cancha[]> {
    return this.http.get<Cancha[]>(apiUrl('/canchas'), { params: this.paramsCanchas(filtros) });
  }

  /** Listado paginado: el total llega en la cabecera `X-Total-Count`. */
  canchasPaginadas(filtros: FiltrosCancha = {}): Observable<PaginaCanchas> {
    return this.http
      .get<Cancha[]>(apiUrl('/canchas'), {
        params: this.paramsCanchas(filtros),
        observe: 'response',
      })
      .pipe(
        map((respuesta) => ({
          items: respuesta.body ?? [],
          total: Number(respuesta.headers.get('X-Total-Count') ?? respuesta.body?.length ?? 0),
        })),
      );
  }

  private paramsCanchas(filtros: FiltrosCancha): HttpParams {
    let params = new HttpParams();
    if (filtros.idDistrito) {
      params = params.set('idDistrito', filtros.idDistrito);
    }
    if (filtros.idEstablecimiento) {
      params = params.set('idEstablecimiento', filtros.idEstablecimiento);
    }
    if (filtros.idDeporte) {
      params = params.set('idDeporte', filtros.idDeporte);
    }
    if (filtros.precioMax != null) {
      params = params.set('precioMax', filtros.precioMax);
    }
    if (filtros.q) {
      params = params.set('q', filtros.q);
    }
    params = params.set('limite', filtros.limite ?? 50);
    if (filtros.desplazamiento) {
      params = params.set('desplazamiento', filtros.desplazamiento);
    }
    return params;
  }

  detalle(id: number): Observable<CanchaDetalle> {
    return this.http.get<CanchaDetalle>(apiUrl(`/canchas/${id}`));
  }

  comparar(ids: number[]): Observable<CanchaDetalle[]> {
    return this.http.get<CanchaDetalle[]>(apiUrl('/canchas/comparar'), {
      params: new HttpParams().set('ids', ids.join(',')),
    });
  }

  horarios(idCancha: number): Observable<HorarioCancha[]> {
    return this.http.get<HorarioCancha[]>(apiUrl(`/canchas/${idCancha}/horarios`));
  }

  disponibilidad(idCancha: number, fecha: string, duracionMinutos = 60): Observable<Disponibilidad> {
    return this.http.get<Disponibilidad>(apiUrl(`/canchas/${idCancha}/disponibilidad`), {
      params: new HttpParams().set('fecha', fecha).set('duracionMinutos', duracionMinutos),
    });
  }

  cocheras(idCancha: number, inicio: string, fin: string): Observable<CocheraDisponible[]> {
    return this.http.get<CocheraDisponible[]>(apiUrl(`/canchas/${idCancha}/cocheras`), {
      params: new HttpParams().set('inicio', inicio).set('fin', fin),
    });
  }

  favoritos(): Observable<number[]> {
    return this.http.get<number[]>(apiUrl('/favoritos'));
  }

  agregarFavorito(idCancha: number): Observable<void> {
    return this.http.post<void>(apiUrl(`/favoritos/${idCancha}`), {});
  }

  quitarFavorito(idCancha: number): Observable<void> {
    return this.http.delete<void>(apiUrl(`/favoritos/${idCancha}`));
  }
}
