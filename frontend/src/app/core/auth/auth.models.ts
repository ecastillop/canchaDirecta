export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface UsuarioResumen {
  id: number;
  nombres: string;
  apellidos: string;
  correo: string;
  estado: string;
  roles: string[];
}

export interface UsuarioActual extends UsuarioResumen {
  telefono: string | null;
  correoVerificado: boolean;
  tokenId?: string;
}

export interface RegistroPayload {
  nombres: string;
  apellidos: string;
  correo: string;
  telefono?: string;
  password: string;
  confirmacionPassword: string;
}

export interface ActualizarPerfilPayload {
  nombres: string;
  apellidos: string;
  telefono?: string;
}

export interface SesionGuardada {
  refreshToken: string;
}
