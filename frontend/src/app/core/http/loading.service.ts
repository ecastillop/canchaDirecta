import { Injectable, signal } from '@angular/core';

/** Contador de peticiones en curso para la barra global de progreso. */
@Injectable({ providedIn: 'root' })
export class LoadingService {
  private pendientes = 0;
  private readonly _active = signal(false);

  readonly active = this._active.asReadonly();

  start(): void {
    this.pendientes++;
    this._active.set(true);
  }

  end(): void {
    this.pendientes = Math.max(0, this.pendientes - 1);
    if (this.pendientes === 0) {
      this._active.set(false);
    }
  }
}
