import { Injectable, computed, signal } from '@angular/core';

const KEY = 'cd.comparar';
const MAXIMO = 4;

/** Seleccion temporal (sessionStorage) de canchas para comparar, maximo 4. */
@Injectable({ providedIn: 'root' })
export class ComparacionService {
  private readonly _ids = signal<number[]>(this.leer());

  readonly ids = this._ids.asReadonly();
  readonly cantidad = computed(() => this._ids().length);
  readonly puedeComparar = computed(() => this._ids().length >= 2);

  toggle(id: number): void {
    this._ids.update((ids) => {
      if (ids.includes(id)) {
        return ids.filter((x) => x !== id);
      }
      return ids.length >= MAXIMO ? ids : [...ids, id];
    });
    this.persistir();
  }

  quitar(id: number): void {
    this._ids.update((ids) => ids.filter((x) => x !== id));
    this.persistir();
  }

  limpiar(): void {
    this._ids.set([]);
    this.persistir();
  }

  tiene(id: number): boolean {
    return this._ids().includes(id);
  }

  private leer(): number[] {
    try {
      const raw = sessionStorage.getItem(KEY);
      const ids = raw ? (JSON.parse(raw) as unknown) : [];
      return Array.isArray(ids) ? ids.filter((x): x is number => typeof x === 'number').slice(0, MAXIMO) : [];
    } catch {
      return [];
    }
  }

  private persistir(): void {
    try {
      sessionStorage.setItem(KEY, JSON.stringify(this._ids()));
    } catch {
      // sesion sin almacenamiento: comparacion solo en memoria
    }
  }
}
