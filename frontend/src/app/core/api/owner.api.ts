import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiUrl } from '../http/api-url';
import { MetricasEstablecimiento } from './metricas.models';
import {
  BloqueoCancha,
  Cochera,
  Establecimiento,
  Liquidacion,
  Promocion,
} from './owner.models';
import { HorarioCancha } from './venues.models';

export interface CrearCanchaPayload {
  nombre: string;
  descripcion?: string | null;
  capacidad: number;
  precioBase: number;
}

@Injectable({ providedIn: 'root' })
export class OwnerApi {
  private readonly http = inject(HttpClient);

  // Establecimientos
  crearEstablecimiento(payload: {
    documentoIdentidad: string;
    idDistrito: number;
    nombre: string;
    direccion: string;
    referencia?: string | null;
  }): Observable<{ id: number }> {
    return this.http.post<{ id: number }>(apiUrl('/establecimientos'), payload);
  }

  obtenerEstablecimiento(id: number): Observable<Establecimiento> {
    return this.http.get<Establecimiento>(apiUrl(`/establecimientos/${id}`));
  }

  misEstablecimientos(): Observable<Establecimiento[]> {
    return this.http.get<Establecimiento[]>(apiUrl('/establecimientos/mios'));
  }

  actualizarEstablecimiento(
    id: number,
    payload: { idDistrito: number; nombre: string; direccion: string; referencia?: string | null },
  ): Observable<Establecimiento> {
    return this.http.put<Establecimiento>(apiUrl(`/establecimientos/${id}`), payload);
  }

  eliminarEstablecimiento(id: number): Observable<void> {
    return this.http.delete<void>(apiUrl(`/establecimientos/${id}`));
  }

  // Canchas
  crearCancha(idEstablecimiento: number, payload: CrearCanchaPayload): Observable<{ id: number }> {
    return this.http.post<{ id: number }>(apiUrl(`/establecimientos/${idEstablecimiento}/canchas`), payload);
  }

  actualizarCancha(id: number, payload: CrearCanchaPayload): Observable<unknown> {
    return this.http.put(apiUrl(`/canchas/${id}`), payload);
  }

  eliminarCancha(id: number): Observable<void> {
    return this.http.delete<void>(apiUrl(`/canchas/${id}`));
  }

  actualizarDeportes(idCancha: number, ids: number[]): Observable<unknown> {
    return this.http.put(apiUrl(`/canchas/${idCancha}/deportes`), { ids });
  }

  actualizarCaracteristicas(idCancha: number, ids: number[]): Observable<unknown> {
    return this.http.put(apiUrl(`/canchas/${idCancha}/caracteristicas`), { ids });
  }

  agregarFoto(
    idCancha: number,
    payload: { url: string; orden: number; principal: boolean },
  ): Observable<{ id: number; url: string; orden: number; principal: boolean }> {
    return this.http.post<{ id: number; url: string; orden: number; principal: boolean }>(
      apiUrl(`/canchas/${idCancha}/fotos`),
      payload,
    );
  }

  eliminarFoto(idCancha: number, idFoto: number): Observable<void> {
    return this.http.delete<void>(apiUrl(`/canchas/${idCancha}/fotos/${idFoto}`));
  }

  // Cocheraas
  cocheras(idEstablecimiento: number): Observable<Cochera[]> {
    return this.http.get<Cochera[]>(apiUrl(`/establecimientos/${idEstablecimiento}/cocheras`));
  }

  crearCochera(
    idEstablecimiento: number,
    payload: { nombre: string; capacidad: number; tarifaHora: number },
  ): Observable<{ id: number }> {
    return this.http.post<{ id: number }>(apiUrl(`/establecimientos/${idEstablecimiento}/cocheras`), payload);
  }

  eliminarCochera(idEstablecimiento: number, idCochera: number): Observable<void> {
    return this.http.delete<void>(apiUrl(`/establecimientos/${idEstablecimiento}/cocheras/${idCochera}`));
  }

  // Horarios
  crearHorario(
    idCancha: number,
    payload: { diaSemana: number; horaInicio: string; horaFin: string; precio?: number | null },
  ): Observable<{ id: number }> {
    return this.http.post<{ id: number }>(apiUrl(`/canchas/${idCancha}/horarios`), payload);
  }

  eliminarHorario(idCancha: number, idHorario: number): Observable<void> {
    return this.http.delete<void>(apiUrl(`/canchas/${idCancha}/horarios/${idHorario}`));
  }

  horarios(idCancha: number): Observable<HorarioCancha[]> {
    return this.http.get<HorarioCancha[]>(apiUrl(`/canchas/${idCancha}/horarios`));
  }

  // Bloqueos
  bloqueos(idCancha: number, desde?: string, hasta?: string): Observable<BloqueoCancha[]> {
    let params = new HttpParams();
    if (desde) {
      params = params.set('desde', desde);
    }
    if (hasta) {
      params = params.set('hasta', hasta);
    }
    return this.http.get<BloqueoCancha[]>(apiUrl(`/canchas/${idCancha}/bloqueos`), { params });
  }

  crearBloqueo(
    idCancha: number,
    payload: { inicio: string; fin: string; motivo: string },
  ): Observable<{ id: number }> {
    return this.http.post<{ id: number }>(apiUrl(`/canchas/${idCancha}/bloqueos`), payload);
  }

  eliminarBloqueo(idCancha: number, idBloqueo: number): Observable<void> {
    return this.http.delete<void>(apiUrl(`/canchas/${idCancha}/bloqueos/${idBloqueo}`));
  }

  // Promociones
  promociones(idEstablecimiento: number): Observable<Promocion[]> {
    return this.http.get<Promocion[]>(apiUrl(`/establecimientos/${idEstablecimiento}/promociones`));
  }

  crearPromocion(
    idEstablecimiento: number,
    payload: {
      codigo: string;
      descripcion: string;
      tipoDescuento: 'PORCENTAJE' | 'MONTO';
      valor: number;
      fechaInicio: string;
      fechaFin: string;
      limiteUsos?: number | null;
      limitePorUsuario?: number | null;
    },
  ): Observable<Promocion> {
    return this.http.post<Promocion>(apiUrl(`/establecimientos/${idEstablecimiento}/promociones`), payload);
  }

  cambiarEstadoPromocion(
    idEstablecimiento: number,
    idPromocion: number,
    estado: 'ACTIVA' | 'INACTIVA',
  ): Observable<Promocion> {
    return this.http.post<Promocion>(
      apiUrl(`/establecimientos/${idEstablecimiento}/promociones/${idPromocion}/estado`),
      { estado },
    );
  }

  // Metricas del panel
  metricas(idEstablecimiento: number, dias = 30): Observable<MetricasEstablecimiento> {
    return this.http.get<MetricasEstablecimiento>(
      apiUrl(`/establecimientos/${idEstablecimiento}/metricas`),
      { params: { dias } },
    );
  }

  // Liquidaciones
  liquidaciones(): Observable<Liquidacion[]> {
    return this.http.get<Liquidacion[]>(apiUrl('/liquidaciones'));
  }

  liquidacion(id: number): Observable<Liquidacion> {
    return this.http.get<Liquidacion>(apiUrl(`/liquidaciones/${id}`));
  }

  generarLiquidacion(periodoInicio: string, periodoFin: string): Observable<Liquidacion> {
    return this.http.post<Liquidacion>(apiUrl('/liquidaciones'), { periodoInicio, periodoFin });
  }
}
