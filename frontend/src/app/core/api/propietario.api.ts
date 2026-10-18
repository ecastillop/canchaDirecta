import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiUrl } from '../http/api-url';
import { PropietarioSolicitud } from './propietario.models';

@Injectable({ providedIn: 'root' })
export class PropietarioApi {
  private readonly http = inject(HttpClient);

  miSolicitud(): Observable<PropietarioSolicitud> {
    return this.http.get<PropietarioSolicitud>(apiUrl('/propietarios/me'));
  }

  solicitar(documentoIdentidad: string): Observable<PropietarioSolicitud> {
    return this.http.post<PropietarioSolicitud>(apiUrl('/propietarios/solicitudes'), { documentoIdentidad });
  }
}
