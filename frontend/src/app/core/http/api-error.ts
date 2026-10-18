import { HttpErrorResponse } from '@angular/common/http';

export interface ErrorCampo {
  campo: string;
  mensaje: string;
}

/** Formato RFC 9457 que emite el backend. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  code?: string;
  detail?: string;
  errors?: ErrorCampo[];
}

/** Error normalizado para la UI: nunca expone stack ni detalles internos. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly campos: ErrorCampo[] = [],
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

const MENSAJES: Record<string, string> = {
  DATOS_INVALIDOS: 'Revisa los datos ingresados.',
  CREDENCIALES_INVALIDAS: 'Correo o contraseña incorrectos.',
  NO_AUTENTICADO: 'Tu sesión expiró. Vuelve a iniciar sesión.',
  SIN_PERMISO: 'No tienes permisos para esta acción.',
  CUENTA_NO_VERIFICADA: 'Debes verificar tu correo antes de continuar.',
  NO_ENCONTRADO: 'No encontramos el recurso solicitado.',
  CONFLICTO: 'La operación no se pudo completar por un conflicto de estado.',
  FRANJA_NO_DISPONIBLE: 'Esa franja ya no está disponible. Elige otra.',
  CORREO_YA_REGISTRADO: 'Ese correo ya tiene una cuenta.',
  DEMASIADOS_INTENTOS: 'Demasiados intentos. Espera unos minutos e intenta de nuevo.',
  RECURSO_BLOQUEADO: 'El recurso está en uso. Intenta nuevamente en unos segundos.',
};

const MENSAJES_POR_ESTADO: Record<number, string> = {
  0: 'No se pudo conectar con el servidor. Verifica tu conexión.',
  400: 'Revisa los datos ingresados.',
  401: 'Tu sesión expiró. Vuelve a iniciar sesión.',
  403: 'No tienes permisos para esta acción.',
  404: 'No encontramos el recurso solicitado.',
  409: 'La operación no se pudo completar por un conflicto de estado.',
  429: 'Demasiados intentos. Espera unos minutos e intenta de nuevo.',
  500: 'Ocurrió un error inesperado. Intenta nuevamente.',
  503: 'El servicio no está disponible en este momento.',
};

/** Traduce cualquier error HTTP a `ApiError` con mensaje en español. */
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }
  if (error instanceof HttpErrorResponse) {
    const body = (error.error ?? {}) as ProblemDetail;
    const code = body.code ?? `HTTP_${error.status}`;
    const message =
      MENSAJES[code] ?? body.detail ?? MENSAJES_POR_ESTADO[error.status] ?? 'Ocurrió un error inesperado.';
    return new ApiError(error.status, code, message, body.errors ?? []);
  }
  return new ApiError(0, 'DESCONOCIDO', 'Ocurrió un error inesperado.');
}
