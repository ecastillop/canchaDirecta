import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { apiUrl } from '../http/api-url';
import {
  ActualizarPerfilPayload,
  RegistroPayload,
  TokenResponse,
  UsuarioActual,
  UsuarioResumen,
} from './auth.models';

/** Contratos exactos de `/api/v1/auth` y `/api/v1/users`. */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);

  login(correo: string, password: string): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(apiUrl('/auth/login'), { correo, password });
  }

  register(payload: RegistroPayload): Observable<UsuarioResumen> {
    return this.http.post<UsuarioResumen>(apiUrl('/auth/register'), payload);
  }

  refresh(refreshToken: string): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(apiUrl('/auth/refresh'), { refreshToken });
  }

  logout(refreshToken: string | null): Observable<void> {
    return this.http.post<void>(apiUrl('/auth/logout'), { refreshToken });
  }

  verifyEmail(token: string): Observable<void> {
    return this.http.post<void>(apiUrl('/auth/email/verify'), { token });
  }

  resendVerification(correo: string): Observable<void> {
    return this.http.post<void>(apiUrl('/auth/email/resend'), { correo });
  }

  requestPasswordReset(correo: string): Observable<void> {
    return this.http.post<void>(apiUrl('/auth/password-reset/request'), { correo });
  }

  confirmPasswordReset(token: string, nuevaPassword: string, confirmacionPassword: string): Observable<void> {
    return this.http.post<void>(apiUrl('/auth/password-reset/confirm'), {
      token,
      nuevaPassword,
      confirmacionPassword,
    });
  }

  me(): Observable<UsuarioActual> {
    return this.http.get<UsuarioActual>(apiUrl('/users/me'));
  }

  updateMe(payload: ActualizarPerfilPayload): Observable<UsuarioActual> {
    return this.http.put<UsuarioActual>(apiUrl('/users/me'), payload);
  }
}
