import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiUrl } from '../http/api-url';

export interface EstablecimientoRecepcion {
  id: number;
  nombre: string;
  distrito: string | null;
}

export interface CanchaRecepcion {
  id: number;
  idEstablecimiento: number;
  nombre: string;
}

export interface ReservaRecepcion {
  id: number;
  idEstablecimiento: number;
  establecimiento: string;
  idCancha: number;
  cancha: string;
  inicio: string;
  fin: string;
  estado: string;
  pagoEstado: string | null;
  total: number;
  idCliente: number;
  cliente: string;
  telefono: string | null;
}

export interface ResumenTurno {
  reservasDelDia: number;
  checkIns: number;
  cobrosPendientes: number;
  canchasDisponibles: number;
}

export interface ClienteRecepcion {
  id: number;
  nombre: string;
  correo: string;
  telefono: string | null;
}

export interface IncidenciaRecepcion {
  id: number;
  asunto: string;
  descripcion: string;
  estado: string;
  fechaRegistro: string;
  idReserva: number | null;
}

export interface NuevaReservaPresencial {
  idEstablecimiento: number | null;
  idCancha: number;
  idCliente: number;
  fecha: string;
  horaInicio: string;
  duracionMinutos: number;
  pagoRecibido: boolean;
  codigoOperacion?: string | null;
}

/** API del portal de recepcion (Bloque D, ADR-0038). */
@Injectable({ providedIn: 'root' })
export class RecepcionApi {
  private readonly http = inject(HttpClient);

  establecimientos(): Observable<EstablecimientoRecepcion[]> {
    return this.http.get<EstablecimientoRecepcion[]>(apiUrl('/recepcion/establecimientos'));
  }

  canchas(idEstablecimiento: number | null): Observable<CanchaRecepcion[]> {
    const params = idEstablecimiento
      ? new HttpParams().set('idEstablecimiento', idEstablecimiento)
      : undefined;
    return this.http.get<CanchaRecepcion[]>(apiUrl('/recepcion/canchas'), { params });
  }

  resumen(idEstablecimiento: number | null, fecha: string): Observable<ResumenTurno> {
    return this.http.get<ResumenTurno>(apiUrl('/recepcion/resumen'), {
      params: paramsFecha(idEstablecimiento, fecha),
    });
  }

  agenda(idEstablecimiento: number | null, fecha: string): Observable<ReservaRecepcion[]> {
    return this.http.get<ReservaRecepcion[]>(apiUrl('/recepcion/agenda'), {
      params: paramsFecha(idEstablecimiento, fecha),
    });
  }

  reservas(idEstablecimiento: number | null, estado: string): Observable<ReservaRecepcion[]> {
    let params = new HttpParams();
    if (idEstablecimiento) {
      params = params.set('idEstablecimiento', idEstablecimiento);
    }
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<ReservaRecepcion[]>(apiUrl('/recepcion/reservas'), { params });
  }

  clientes(texto: string): Observable<ClienteRecepcion[]> {
    return this.http.get<ClienteRecepcion[]>(apiUrl('/recepcion/clientes'), {
      params: new HttpParams().set('q', texto),
    });
  }

  reservaPresencial(payload: NuevaReservaPresencial): Observable<ReservaRecepcion> {
    return this.http.post<ReservaRecepcion>(apiUrl('/recepcion/reservas'), payload);
  }

  checkIn(idReserva: number): Observable<ReservaRecepcion> {
    return this.http.post<ReservaRecepcion>(apiUrl(`/recepcion/reservas/${idReserva}/checkin`), {});
  }

  cobrar(idReserva: number, medioPago: string, codigoOperacion?: string): Observable<ReservaRecepcion> {
    return this.http.post<ReservaRecepcion>(apiUrl(`/recepcion/reservas/${idReserva}/cobro`), {
      medioPago,
      codigoOperacion: codigoOperacion ?? null,
    });
  }

  incidencias(): Observable<IncidenciaRecepcion[]> {
    return this.http.get<IncidenciaRecepcion[]>(apiUrl('/recepcion/incidencias'));
  }

  registrarIncidencia(payload: {
    asunto: string;
    descripcion: string;
    idReserva: number | null;
  }): Observable<IncidenciaRecepcion> {
    return this.http.post<IncidenciaRecepcion>(apiUrl('/recepcion/incidencias'), payload);
  }
}

function paramsFecha(idEstablecimiento: number | null, fecha: string): HttpParams {
  let params = new HttpParams().set('fecha', fecha);
  if (idEstablecimiento) {
    params = params.set('idEstablecimiento', idEstablecimiento);
  }
  return params;
}
