import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiUrl } from '../http/api-url';

export interface AdminPropietario {
  id: number;
  idUsuario: number;
  nombreCompleto: string;
  correo: string;
  documento: string;
  estadoValidacion: 'PENDIENTE' | 'VALIDADO' | 'RECHAZADO';
}

export interface AdminReclamo {
  id: number;
  idUsuario: number;
  idReserva: number | null;
  asunto: string;
  descripcion: string;
  estado: 'ABIERTO' | 'EN_REVISION' | 'RESUELTO' | 'CERRADO';
  fechaRegistro: string;
  seguimientos: Array<{ id: number; comentario: string; fecha: string }>;
}

export interface AdminUsuario {
  id: number;
  nombreCompleto: string;
  correo: string;
  telefono: string | null;
  estado: string;
  roles: string[];
  fechaRegistro: string;
}

export interface AdminEstablecimiento {
  id: number;
  nombre: string;
  distrito: string;
  propietario: string;
  estado: string;
  canchas: number;
}

export interface AdminReserva {
  id: number;
  cancha: string;
  establecimiento: string;
  titular: string;
  inicio: string;
  fin: string;
  montoCancha: number;
  montoCochera: number | null;
  estado: string;
}

export interface AdminPago {
  id: number;
  idReserva: number;
  monto: number;
  medioPago: string;
  estado: string;
  fechaPago: string;
  reembolsado: boolean;
}

export interface AdminCochera {
  id: number;
  idEstablecimiento: number;
  establecimiento: string;
  nombre: string;
  capacidad: number;
  tarifaHora: number;
  estado: 'ACTIVA' | 'INACTIVA';
}

export interface AdminPromocion {
  id: number;
  codigo: string;
  descripcion: string;
  tipoDescuento: 'PORCENTAJE' | 'MONTO';
  valor: number;
  fechaInicio: string;
  fechaFin: string;
  estado: 'ACTIVA' | 'INACTIVA';
  canchas: Array<{ id: number; nombre: string }>;
}

export interface Auditoria {
  id: number;
  idUsuario: number | null;
  accion: string;
  entidad: string;
  idEntidad: number | null;
  detalle: string;
  fechaEvento: string;
}

const paramsLimite = (limite: number) => new HttpParams().set('limite', limite);

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);

  propietarios(estado?: string): Observable<AdminPropietario[]> {
    let params = paramsLimite(100);
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<AdminPropietario[]>(apiUrl('/admin/propietarios'), { params });
  }

  validarPropietario(id: number, estado: 'VALIDADO' | 'RECHAZADO', motivo?: string): Observable<AdminPropietario> {
    return this.http.post<AdminPropietario>(apiUrl(`/admin/propietarios/${id}/validacion`), {
      estado,
      motivo: motivo ?? null,
    });
  }

  reclamos(estado?: string): Observable<AdminReclamo[]> {
    let params = paramsLimite(100);
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<AdminReclamo[]>(apiUrl('/admin/reclamos'), { params });
  }

  reclamo(id: number): Observable<AdminReclamo> {
    return this.http.get<AdminReclamo>(apiUrl(`/admin/reclamos/${id}`));
  }

  cambiarEstadoReclamo(id: number, estado: string, comentario?: string): Observable<AdminReclamo> {
    return this.http.post<AdminReclamo>(apiUrl(`/admin/reclamos/${id}/estado`), {
      estado,
      comentario: comentario ?? null,
    });
  }

  comentarReclamo(id: number, comentario: string): Observable<unknown> {
    return this.http.post(apiUrl(`/admin/reclamos/${id}/seguimientos`), { comentario });
  }

  usuarios(correo?: string, estado?: string): Observable<AdminUsuario[]> {
    let params = paramsLimite(100);
    if (correo) {
      params = params.set('correo', correo);
    }
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<AdminUsuario[]>(apiUrl('/admin/usuarios'), { params });
  }

  usuario(id: number): Observable<AdminUsuario> {
    return this.http.get<AdminUsuario>(apiUrl(`/admin/usuarios/${id}`));
  }

  cambiarRoles(idUsuario: number, roles: string[]): Observable<AdminUsuario> {
    return this.http.post<AdminUsuario>(apiUrl(`/admin/usuarios/${idUsuario}/roles`), { roles });
  }

  establecimientos(): Observable<AdminEstablecimiento[]> {
    return this.http.get<AdminEstablecimiento[]>(apiUrl('/admin/establecimientos'), {
      params: paramsLimite(100),
    });
  }

  reservas(): Observable<AdminReserva[]> {
    return this.http.get<AdminReserva[]>(apiUrl('/admin/reservas'), { params: paramsLimite(100) });
  }

  pagos(): Observable<AdminPago[]> {
    return this.http.get<AdminPago[]>(apiUrl('/admin/pagos'), { params: paramsLimite(100) });
  }

  promociones(estado?: string): Observable<AdminPromocion[]> {
    let params = paramsLimite(100);
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<AdminPromocion[]>(apiUrl('/admin/promociones'), { params });
  }

  crearPromocion(payload: {
    codigo: string;
    descripcion: string;
    tipoDescuento: 'PORCENTAJE' | 'MONTO';
    valor: number;
    fechaInicio: string;
    fechaFin: string;
  }): Observable<AdminPromocion> {
    return this.http.post<AdminPromocion>(apiUrl('/admin/promociones'), payload);
  }

  estadoPromocion(id: number, estado: 'ACTIVA' | 'INACTIVA'): Observable<AdminPromocion> {
    return this.http.post<AdminPromocion>(apiUrl(`/admin/promociones/${id}/estado`), { estado });
  }

  cocheras(): Observable<AdminCochera[]> {
    return this.http.get<AdminCochera[]>(apiUrl('/admin/cocheras'), { params: paramsLimite(100) });
  }

  estadoCochera(id: number, estado: 'ACTIVA' | 'INACTIVA'): Observable<AdminCochera> {
    return this.http.post<AdminCochera>(apiUrl(`/admin/cocheras/${id}/estado`), { estado });
  }

  auditoria(): Observable<Auditoria[]> {
    return this.http.get<Auditoria[]>(apiUrl('/admin/auditoria'), { params: paramsLimite(100) });
  }
}
