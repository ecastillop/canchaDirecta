import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  CanchaRecepcion,
  EstablecimientoRecepcion,
  RecepcionApi,
} from './recepcion.api';

/** Sede activa y canchas compartidas por las pantallas de recepcion. */
@Injectable({ providedIn: 'root' })
export class RecepcionStore {
  private readonly api = inject(RecepcionApi);

  private cargado = false;

  readonly establecimientos = signal<EstablecimientoRecepcion[]>([]);
  readonly seleccionado = signal<number | null>(null);
  readonly canchas = signal<CanchaRecepcion[]>([]);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);

  readonly sede = computed(
    () => this.establecimientos().find((sede) => sede.id === this.seleccionado()) ?? null,
  );

  /** Carga las sedes una vez y selecciona la primera. */
  async init(): Promise<void> {
    if (this.cargado) {
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    try {
      const sedes = await firstValueFrom(this.api.establecimientos());
      this.establecimientos.set(sedes);
      this.seleccionado.set(sedes.length > 0 ? sedes[0].id : null);
      this.cargado = true;
      await this.cargarCanchas();
    } catch (error) {
      this.error.set('No se pudieron cargar tus sedes de recepción.');
    } finally {
      this.cargando.set(false);
    }
  }

  async seleccionar(id: number | null): Promise<void> {
    this.seleccionado.set(id);
    await this.cargarCanchas();
  }

  private async cargarCanchas(): Promise<void> {
    const canchas = await firstValueFrom(this.api.canchas(this.seleccionado()));
    this.canchas.set(canchas);
  }
}

/** Fecha de hoy (`yyyy-MM-dd`) en la zona del navegador. */
export function hoyIso(): string {
  const ahora = new Date();
  const mes = String(ahora.getMonth() + 1).padStart(2, '0');
  const dia = String(ahora.getDate()).padStart(2, '0');
  return `${ahora.getFullYear()}-${mes}-${dia}`;
}
