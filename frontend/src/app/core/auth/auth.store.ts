import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from './auth.api';
import {
  ActualizarPerfilPayload,
  RegistroPayload,
  TokenResponse,
  UsuarioActual,
  UsuarioResumen,
} from './auth.models';

const REFRESH_KEY = 'cd.refresh';

export type SessionStatus = 'unknown' | 'authenticated' | 'anonymous';

/**
 * Sesion: access token SOLO en memoria; refresh token en sessionStorage.
 * El refresh es de vuelo unico para que varios 401 no disparen varias rotaciones.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly api = inject(AuthApi);

  private readonly _user = signal<UsuarioActual | null>(null);
  private readonly _accessToken = signal<string | null>(null);
  private readonly _status = signal<SessionStatus>('unknown');

  readonly user = this._user.asReadonly();
  readonly status = this._status.asReadonly();
  readonly isAuthenticated = computed(() => this._status() === 'authenticated' && this._user() !== null);
  readonly roles = computed(() => this._user()?.roles ?? []);
  readonly isOwner = computed(() => this.roles().includes('OWNER'));
  readonly isAdmin = computed(() => this.roles().includes('PLATFORM_ADMIN'));
  readonly fullName = computed(() => {
    const user = this._user();
    return user ? `${user.nombres} ${user.apellidos}`.trim() : '';
  });

  private initPromise: Promise<void> | null = null;
  private refreshPromise: Promise<boolean> | null = null;

  /** Idempotente: intenta restaurar la sesion una sola vez. */
  init(): Promise<void> {
    this.initPromise ??= this.restaurar();
    return this.initPromise;
  }

  get accessToken(): string | null {
    return this._accessToken();
  }

  async login(correo: string, password: string): Promise<void> {
    const tokens = await firstValueFrom(this.api.login(correo, password));
    this.guardarTokens(tokens);
    const user = await firstValueFrom(this.api.me());
    this._user.set(user);
    this._status.set('authenticated');
  }

  async register(payload: RegistroPayload): Promise<UsuarioResumen> {
    return firstValueFrom(this.api.register(payload));
  }

  /** Refresca una sola vez; devuelve false si no hay refresh token o fue rechazado. */
  refreshSession(): Promise<boolean> {
    this.refreshPromise ??= this.ejecutarRefresh().finally(() => {
      this.refreshPromise = null;
    });
    return this.refreshPromise;
  }

  async logout(): Promise<void> {
    const refreshToken = this.leerRefresh();
    this.limpiar();
    if (refreshToken) {
      try {
        await firstValueFrom(this.api.logout(refreshToken));
      } catch {
        // el token pudo expirar: la sesion local ya esta cerrada
      }
    }
  }

  async updateProfile(payload: ActualizarPerfilPayload): Promise<UsuarioActual> {
    const user = await firstValueFrom(this.api.updateMe(payload));
    this._user.set(user);
    return user;
  }

  async reloadUser(): Promise<void> {
    if (!this._accessToken()) {
      return;
    }
    this._user.set(await firstValueFrom(this.api.me()));
  }

  clearSession(): void {
    this.limpiar();
  }

  private async restaurar(): Promise<void> {
    const refreshToken = this.leerRefresh();
    if (!refreshToken) {
      this._status.set('anonymous');
      return;
    }
    const ok = await this.refreshSession();
    if (!ok) {
      this._status.set('anonymous');
    }
  }

  private async ejecutarRefresh(): Promise<boolean> {
    const refreshToken = this.leerRefresh();
    if (!refreshToken) {
      return false;
    }
    try {
      const tokens = await firstValueFrom(this.api.refresh(refreshToken));
      this.guardarTokens(tokens);
      const user = await firstValueFrom(this.api.me());
      this._user.set(user);
      this._status.set('authenticated');
      return true;
    } catch {
      this.limpiar();
      return false;
    }
  }

  private guardarTokens(tokens: TokenResponse): void {
    this._accessToken.set(tokens.accessToken);
    this._status.set('authenticated');
    try {
      sessionStorage.setItem(REFRESH_KEY, tokens.refreshToken);
    } catch {
      // sin sessionStorage la sesion no sobrevive a recargas
    }
  }

  private leerRefresh(): string | null {
    try {
      return sessionStorage.getItem(REFRESH_KEY);
    } catch {
      return null;
    }
  }

  private limpiar(): void {
    this._accessToken.set(null);
    this._user.set(null);
    this._status.set('anonymous');
    try {
      sessionStorage.removeItem(REFRESH_KEY);
    } catch {
      // ignorar
    }
  }
}
