import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiUrl } from '../http/api-url';
import { Pago, PagoPayload } from './reservas.models';

@Injectable({ providedIn: 'root' })
export class PagosApi {
  private readonly http = inject(HttpClient);

  pagar(idReserva: number, payload: PagoPayload): Observable<Pago> {
    return this.http.post<Pago>(apiUrl(`/reservas/${idReserva}/pago`), payload);
  }

  obtener(idReserva: number): Observable<Pago> {
    return this.http.get<Pago>(apiUrl(`/reservas/${idReserva}/pago`));
  }
}
