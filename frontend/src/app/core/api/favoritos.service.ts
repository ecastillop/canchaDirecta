import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthStore } from '../auth/auth.store';
import { VenuesApi } from './venues.api';

/** Favoritos del usuario autenticado, sincronizados con el backend. */
@Injectable({ providedIn: 'root' })
export class FavoritosService {
  private readonly api = inject(VenuesApi);
  private readonly auth = inject(AuthStore);
  private readonly _ids = signal<ReadonlySet<number>>(new Set());
  private readonly _cargando = signal(false);

  readonly ids = this._ids.asReadonly();
  readonly cantidad = computed(() => this._ids().size);
  readonly cargando = this._cargando.asReadonly();

  constructor() {
    effect(() => {
      if (this.auth.isAuthenticated()) {
        void this.cargar();
      } else {
        this._ids.set(new Set());
      }
    });
  }

  tiene(idCancha: number): boolean {
    return this._ids().has(idCancha);
  }

  async alternar(idCancha: number): Promise<boolean> {
    if (!this.auth.isAuthenticated()) {
      return false;
    }
    const antes = this._ids();
    const activo = !antes.has(idCancha);
    const siguiente = new Set(antes);
    if (activo) {
      siguiente.add(idCancha);
    } else {
      siguiente.delete(idCancha);
    }
    this._ids.set(siguiente);
    try {
      if (activo) {
        await firstValueFrom(this.api.agregarFavorito(idCancha));
      } else {
        await firstValueFrom(this.api.quitarFavorito(idCancha));
      }
      return true;
    } catch {
      this._ids.set(antes);
      return false;
    }
  }

  private async cargar(): Promise<void> {
    this._cargando.set(true);
    try {
      this._ids.set(new Set(await firstValueFrom(this.api.favoritos())));
    } catch {
      this._ids.set(new Set());
    } finally {
      this._cargando.set(false);
    }
  }
}
