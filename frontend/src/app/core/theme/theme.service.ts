import { DOCUMENT, Injectable, computed, effect, inject, signal } from '@angular/core';

export type PaletteId = 'cancha' | 'grafito' | 'arena';
export type ModoId = 'claro' | 'oscuro';

export interface PaletteOption {
  id: PaletteId;
  nombre: string;
}

export interface ModoOption {
  id: ModoId;
  nombre: string;
}

export const PALETTES: PaletteOption[] = [
  { id: 'cancha', nombre: 'Bosque' },
  { id: 'grafito', nombre: 'Nocturno' },
  { id: 'arena', nombre: 'Arena' },
];

export const MODOS: ModoOption[] = [
  { id: 'claro', nombre: 'Claro' },
  { id: 'oscuro', nombre: 'Oscuro' },
];

const STORAGE_KEY = 'cd.palette';
const STORAGE_MODO = 'cd.modo';

/** Aplica y persiste la paleta y el modo claro/oscuro sin que los componentes conozcan colores. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly _palette = signal<PaletteId>(this.leerPaleta());
  private readonly _modo = signal<ModoId>(this.leerModo());

  readonly palette = this._palette.asReadonly();
  readonly modo = this._modo.asReadonly();
  readonly oscuro = computed(() => this._modo() === 'oscuro');

  constructor() {
    effect(() => this.aplicar(this._palette(), this._modo()));
  }

  setPalette(palette: PaletteId): void {
    this._palette.set(palette);
    try {
      localStorage.setItem(STORAGE_KEY, palette);
    } catch {
      // almacenamiento no disponible (modo privado): la paleta dura la sesion
    }
  }

  setModo(modo: ModoId): void {
    this._modo.set(modo);
    try {
      localStorage.setItem(STORAGE_MODO, modo);
    } catch {
      // almacenamiento no disponible (modo privado): el modo dura la sesion
    }
  }

  alternarModo(): void {
    this.setModo(this._modo() === 'oscuro' ? 'claro' : 'oscuro');
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

  private leerModo(): ModoId {
    try {
      const guardado = localStorage.getItem(STORAGE_MODO);
      if (guardado === 'claro' || guardado === 'oscuro') return guardado;
    } catch {
      // ignorar
    }
    try {
      if (window.matchMedia?.('(prefers-color-scheme: dark)').matches) return 'oscuro';
    } catch {
      // sin matchMedia: modo claro por defecto
    }
    return 'claro';
  }

  private aplicar(palette: PaletteId, modo: ModoId): void {
    const html = this.document.documentElement;
    html.setAttribute('data-palette', palette);
    if (modo === 'oscuro') html.setAttribute('data-theme', 'dark');
    else html.removeAttribute('data-theme');
    html.style.setProperty('color-scheme', modo === 'oscuro' ? 'dark' : 'light');
  }
}
