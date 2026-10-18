import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthStore } from '../auth/auth.store';
import { OwnerApi } from './owner.api';
import { Establecimiento } from './owner.models';
import { PropietarioSolicitud } from './propietario.models';
import { PropietarioApi } from './propietario.api';
import { VenuesApi } from './venues.api';
import { Cancha } from './venues.models';

const KEY = 'cd.establecimiento';

/** Estado del panel de propietario: solicitud, establecimiento activo y sus canchas. */
@Injectable({ providedIn: 'root' })
export class OwnerStore {
  private readonly propietarios = inject(PropietarioApi);
  private readonly ownerApi = inject(OwnerApi);
  private readonly venues = inject(VenuesApi);
  private readonly auth = inject(AuthStore);

  private readonly _solicitud = signal<PropietarioSolicitud | null>(null);
  private readonly _establecimientoId = signal<number | null>(this.leerId());
  private readonly _establecimientos = signal<Establecimiento[]>([]);
  private readonly _canchas = signal<Cancha[]>([]);
  private readonly _cargando = signal(true);

  readonly solicitud = this._solicitud.asReadonly();
  readonly establecimientoId = this._establecimientoId.asReadonly();
  readonly establecimientos = this._establecimientos.asReadonly();
  readonly canchas = this._canchas.asReadonly();
  readonly cargando = this._cargando.asReadonly();

  readonly aprobado = computed(() => {
    const estado = this._solicitud()?.estadoValidacion;
    return estado === 'VALIDADO' || estado === 'APROBADO' || this.auth.isAdmin();
  });

  async init(): Promise<void> {
    this._cargando.set(true);
    try {
      this._solicitud.set(await firstValueFrom(this.propietarios.miSolicitud()));
    } catch {
      this._solicitud.set(null);
    }
    await this.recargarEstablecimientos();
    this._cargando.set(false);
    await this.cargarCanchas();
  }

  /** Refresca el listado propio y corrige el establecimiento activo si ya no existe. */
  async recargarEstablecimientos(): Promise<void> {
    try {
      const lista = await firstValueFrom(this.ownerApi.misEstablecimientos());
      this._establecimientos.set(lista);
      const actual = this._establecimientoId();
      if (!lista.some((establecimiento) => establecimiento.id === actual)) {
        const preferido = lista.find((establecimiento) => establecimiento.estado === 'ACTIVO') ?? lista[0];
        this.usarEstablecimiento(preferido?.id ?? null);
      }
    } catch {
      this._establecimientos.set([]);
    }
  }

  async cargarCanchas(): Promise<void> {
    const idEstablecimiento = this._establecimientoId();
    if (!idEstablecimiento) {
      this._canchas.set([]);
      return;
    }
    try {
      this._canchas.set(await firstValueFrom(this.venues.canchas({ idEstablecimiento, limite: 100 })));
    } catch {
      this._canchas.set([]);
    }
  }

  usarEstablecimiento(id: number | null): void {
    this._establecimientoId.set(id);
    try {
      if (id) {
        localStorage.setItem(KEY, String(id));
      } else {
        localStorage.removeItem(KEY);
      }
    } catch {
      // sin almacenamiento: el id dura la sesion
    }
    void this.cargarCanchas();
  }

  private leerId(): number | null {
    try {
      const valor = Number(localStorage.getItem(KEY));
      return Number.isFinite(valor) && valor > 0 ? valor : null;
    } catch {
      return null;
    }
  }
}
