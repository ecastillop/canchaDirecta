export interface PropietarioSolicitud {
  id: number;
  idUsuario: number;
  nombreCompleto: string;
  correo: string;
  documento: string;
  estadoValidacion: 'PENDIENTE' | 'VALIDADO' | 'APROBADO' | 'RECHAZADO';
}
