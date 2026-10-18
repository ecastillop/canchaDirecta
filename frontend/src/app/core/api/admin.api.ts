import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Actividad } from './actividades.models';
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

export interface AdminOperador {
  idUsuario: number;
  nombre: string;
  correo: string;
  cargo: string | null;
  activo: boolean;
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
  limiteUsos: number | null;
  limitePorUsuario: number | null;
  usos: number;
  canchas: Array<{ id: number; nombre: string }>;
}

export interface AdminReporteResumen {
  usuarios: number;
  usuariosNuevos: number;
  propietariosValidados: number;
  establecimientosActivos: number;
  reservasPeriodo: number;
  ingresosAprobados: number;
  pagosAprobados: number;
  reclamosAbiertos: number;
}

export interface AdminReportePunto {
  fecha: string;
  reservas: number;
  usuariosNuevos: number;
  ingresos: number;
}

export interface AdminReporteEstado {
  estado: string;
  total: number;
}

export interface AdminReporteEstablecimiento {
  id: number;
  nombre: string;
  distrito: string;
  reservas: number;
  ingresos: number;
}

export interface AdminReporte {
  dias: number;
  desde: string;
  hasta: string;
  resumen: AdminReporteResumen;
  serie: AdminReportePunto[];
  estados: AdminReporteEstado[];
  establecimientos: AdminReporteEstablecimiento[];
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

export interface AdminConfiguracion {
  comisionPorcentaje: number;
  horasCancelacionGratis: number;
  horasMinimasCancelacion: number;
  diasAnticipacionMax: number;
  correoSoporte: string;
  fechaActualizacion: string | null;
}

const paramsLimite = (limite: number) => new HttpParams().set('limite', limite);

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);

  reporte(dias = 30): Observable<AdminReporte> {
    return this.http.get<AdminReporte>(apiUrl('/admin/reportes'), {
      params: new HttpParams().set('dias', dias),
    });
  }

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

  estadoEstablecimiento(id: number, estado: 'ACTIVO' | 'INACTIVO'): Observable<AdminEstablecimiento> {
    return this.http.post<AdminEstablecimiento>(apiUrl(`/admin/establecimientos/${id}/estado`), { estado });
  }

  actividades(): Observable<Actividad[]> {
    return this.http.get<Actividad[]>(apiUrl('/admin/actividades'), { params: paramsLimite(200) });
  }

  cancelarActividad(id: number): Observable<void> {
    return this.http.delete<void>(apiUrl(`/admin/actividades/${id}`));
  }

  establecimientos(): Observable<AdminEstablecimiento[]> {
    return this.http.get<AdminEstablecimiento[]>(apiUrl('/admin/establecimientos'), {
      params: paramsLimite(100),
    });
  }

  reservas(): Observable<AdminReserva[]> {
    return this.http.get<AdminReserva[]>(apiUrl('/admin/reservas'), { params: paramsLimite(100) });
  }

  confirmarReserva(id: number): Observable<unknown> {
    return this.http.post(apiUrl(`/admin/reservas/${id}/confirmar`), {});
  }

  rechazarReserva(id: number, motivo?: string): Observable<unknown> {
    return this.http.post(apiUrl(`/admin/reservas/${id}/rechazar`), { motivo: motivo ?? null });
  }

  crearReclamoAdmin(payload: {
    idUsuario: number;
    idReserva?: number | null;
    asunto: string;
    descripcion: string;
  }): Observable<AdminReclamo> {
    return this.http.post<AdminReclamo>(apiUrl('/admin/reclamos'), payload);
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
    limiteUsos?: number | null;
    limitePorUsuario?: number | null;
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

  configuracion(): Observable<AdminConfiguracion> {
    return this.http.get<AdminConfiguracion>(apiUrl('/admin/configuracion'));
  }

  actualizarConfiguracion(payload: Omit<AdminConfiguracion, 'fechaActualizacion'>): Observable<AdminConfiguracion> {
    return this.http.put<AdminConfiguracion>(apiUrl('/admin/configuracion'), payload);
  }

  operadores(idEstablecimiento: number): Observable<AdminOperador[]> {
    return this.http.get<AdminOperador[]>(apiUrl(`/admin/establecimientos/${idEstablecimiento}/operadores`));
  }

  asignarOperador(idEstablecimiento: number, idUsuario: number, cargo?: string): Observable<AdminOperador[]> {
    return this.http.post<AdminOperador[]>(
      apiUrl(`/admin/establecimientos/${idEstablecimiento}/operadores`),
      { idUsuario, cargo: cargo ?? null },
    );
  }

  removerOperador(idEstablecimiento: number, idUsuario: number): Observable<AdminOperador[]> {
    return this.http.delete<AdminOperador[]>(
      apiUrl(`/admin/establecimientos/${idEstablecimiento}/operadores/${idUsuario}`),
    );
  }
}
