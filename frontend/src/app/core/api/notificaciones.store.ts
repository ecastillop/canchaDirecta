import { Injectable, inject, signal } from '@angular/core';
import { EngagementApi } from './engagement.api';

/**
 * Conteo compartido de notificaciones sin leer. Lo consumen el badge de la campana
 * (topbar publica, cabecera de cuenta) y la propia pagina de notificaciones para que
 * el numero no se desincronice al marcar como leidas.
 */
@Injectable({ providedIn: 'root' })
export class NotificacionesStore {
  private readonly api = inject(EngagementApi);
  private readonly _noLeidas = signal(0);

  readonly noLeidas = this._noLeidas.asReadonly();

  /** Refresca el conteo; si falla conserva el valor actual (el badge no es critico). */
  cargar(): void {
    this.api.notificaciones(1).subscribe({
      next: (respuesta) => this._noLeidas.set(respuesta.noLeidas),
      error: () => {
        // sin sesion o error de red: se conserva el ultimo conteo conocido
      },
    });
  }

  fijar(total: number): void {
    this._noLeidas.set(Math.max(0, total));
  }

  quitar(cantidad = 1): void {
    this._noLeidas.update((n) => Math.max(0, n - cantidad));
  }

  limpiar(): void {
    this._noLeidas.set(0);
  }
}
