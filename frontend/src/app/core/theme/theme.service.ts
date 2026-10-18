import { DOCUMENT, Injectable, effect, inject, signal } from '@angular/core';

export type PaletteId = 'cancha' | 'grafito' | 'arena';

export interface PaletteOption {
  id: PaletteId;
  nombre: string;
}

export const PALETTES: PaletteOption[] = [
  { id: 'cancha', nombre: 'Bosque' },
  { id: 'grafito', nombre: 'Nocturno' },
  { id: 'arena', nombre: 'Arena' },
];

const STORAGE_KEY = 'cd.palette';

/** Aplica y persiste la paleta activa sin que los componentes conozcan colores. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly _palette = signal<PaletteId>(this.leerPaleta());

  readonly palette = this._palette.asReadonly();

  constructor() {
    effect(() => this.aplicar(this._palette()));
  }

  setPalette(palette: PaletteId): void {
    this._palette.set(palette);
    try {
      localStorage.setItem(STORAGE_KEY, palette);
    } catch {
      // almacenamiento no disponible (modo privado): la paleta dura la sesion
    }
  }

  private leerPaleta(): PaletteId {
    try {
      const guardada = localStorage.getItem(STORAGE_KEY);
      if (guardada === 'cancha' || guardada === 'grafito' || guardada === 'arena') {
        return guardada;
      }
    } catch {
      // ignorar
    }
    return 'cancha';
  }

  private aplicar(palette: PaletteId): void {
    const html = this.document.documentElement;
    html.setAttribute('data-palette', palette);
    html.style.setProperty('color-scheme', 'light');
  }
}
