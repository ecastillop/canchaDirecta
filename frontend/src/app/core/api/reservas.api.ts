import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiUrl } from '../http/api-url';
import { CrearReservaPayload, EstadoReserva, Reserva } from './reservas.models';

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

  /** Reservas recibidas por el propietario. */
  recibidas(estado?: EstadoReserva | null, limite = 50): Observable<Reserva[]> {
    let params = new HttpParams().set('limite', limite);
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<Reserva[]>(apiUrl('/reservas/recibidas'), { params });
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
