import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiUrl } from '../http/api-url';
import { CrearReservaPayload, EstadoReserva, Reserva, ReservaRecibida } from './reservas.models';

@Injectable({ providedIn: 'root' })
export class ReservasApi {
  private readonly http = inject(HttpClient);

  crear(payload: CrearReservaPayload, idempotencyKey: string): Observable<Reserva> {
    return this.http.post<Reserva>(apiUrl('/reservas'), payload, {
      headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
    });
  }

  mias(limite = 50): Observable<Reserva[]> {
    return this.http.get<Reserva[]>(apiUrl('/reservas/mias'), {
      params: new HttpParams().set('limite', limite),
    });
  }

  cancelar(id: number): Observable<Reserva> {
    return this.http.post<Reserva>(apiUrl(`/reservas/${id}/cancelar`), {});
  }

  modificar(id: number, inicio: string, fin: string): Observable<Reserva> {
    return this.http.post<Reserva>(apiUrl(`/reservas/${id}/modificar`), { inicio, fin });
  }

  /**
   * Reservas recibidas por el propietario, con el titular y su ultimo pago. `filtros` permite
   * acotar a una cancha y a un rango de instantes (`desde`/`hasta` en ISO-8601, se aplican por
   * solape), que es lo que usa la rejilla de ocupacion semanal del panel.
   */
  recibidas(
    estado?: EstadoReserva | null,
    limite = 50,
    filtros: { idCancha?: number; desde?: string; hasta?: string } = {},
  ): Observable<ReservaRecibida[]> {
    let params = new HttpParams().set('limite', limite);
    if (estado) {
      params = params.set('estado', estado);
    }
    if (filtros.idCancha) {
      params = params.set('idCancha', filtros.idCancha);
    }
    if (filtros.desde) {
      params = params.set('desde', filtros.desde);
    }
    if (filtros.hasta) {
      params = params.set('hasta', filtros.hasta);
    }
    return this.http.get<ReservaRecibida[]>(apiUrl('/reservas/recibidas'), { params });
  }

  confirmar(id: number): Observable<Reserva> {
    return this.http.post<Reserva>(apiUrl(`/reservas/${id}/confirmar`), {});
  }

  rechazar(id: number, motivo?: string): Observable<Reserva> {
    return this.http.post<Reserva>(apiUrl(`/reservas/${id}/rechazar`), { motivo: motivo ?? null });
  }

  revocar(id: number, motivo?: string): Observable<Reserva> {
    return this.http.post<Reserva>(apiUrl(`/reservas/${id}/revocar`), { motivo: motivo ?? null });
  }
}
