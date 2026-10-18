import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiUrl } from '../http/api-url';
import { AyudaCategoria } from './ayuda.models';

@Injectable({ providedIn: 'root' })
export class AyudaApi {
  private readonly http = inject(HttpClient);

  listar(texto?: string): Observable<AyudaCategoria[]> {
    let params = new HttpParams();
    if (texto?.trim()) {
      params = params.set('texto', texto.trim());
    }
    return this.http.get<AyudaCategoria[]>(apiUrl('/ayuda'), { params });
  }
}
