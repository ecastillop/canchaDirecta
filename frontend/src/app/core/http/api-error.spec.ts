import { HttpErrorResponse } from '@angular/common/http';
import { ApiError, toApiError } from './api-error';

describe('toApiError', () => {
  it('usa detail y code del cuerpo RFC 9457', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: { code: 'SLOT_UNAVAILABLE', detail: 'Franja ocupada' },
    });
    const apiError = toApiError(error);
    expect(apiError).toBeInstanceOf(ApiError);
    expect(apiError.status).toBe(409);
    expect(apiError.code).toBe('SLOT_UNAVAILABLE');
    expect(apiError.message).toBe('Franja ocupada');
  });

  it('traduce codigos conocidos cuando no hay detail', () => {
    const error = new HttpErrorResponse({
      status: 401,
      error: { code: 'NO_AUTENTICADO' },
    });
    expect(toApiError(error).message).toContain('sesión');
  });

  it('cae al mensaje por estado HTTP', () => {
    const error = new HttpErrorResponse({ status: 503, error: {} });
    expect(toApiError(error).message).toContain('no está disponible');
  });

  it('mapea errores de red (status 0)', () => {
    const error = new HttpErrorResponse({ status: 0 });
    expect(toApiError(error).message).toContain('conectar');
  });

  it('es idempotente', () => {
    const original = new ApiError(400, 'X', 'mensaje');
    expect(toApiError(original)).toBe(original);
  });
});
