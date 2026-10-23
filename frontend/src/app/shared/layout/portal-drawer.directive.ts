import { Directive, ElementRef, HostListener, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

/**
 * Menú lateral colapsable para los portales privados.
 *
 * Mantiene el mismo sidebar del escritorio y, en móvil, lo convierte en un
 * panel fuera de pantalla que se abre con el botón hamburguesa. Cierra al
 * navegar, con Escape, al tocar el fondo o al reducir la ventana.
 */
@Directive({
  selector: '[cdPortalDrawer]',
  exportAs: 'cdPortalDrawer',
  standalone: true,
})
export class PortalDrawerDirective {
  readonly open = signal(false);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly router = inject(Router);

  constructor() {
    this.router.events
      .pipe(
        filter((evento): evento is NavigationEnd => evento instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.cerrar(false));
  }

  alternar(): void {
    this.open() ? this.cerrar() : this.abrir();
  }

  abrir(): void {
    this.open.set(true);
    document.body.style.overflow = 'hidden';
    setTimeout(() => {
      const primero = this.host.nativeElement.querySelector<HTMLElement>('aside a, aside button');
      primero?.focus({ preventScroll: true });
    }, 80);
  }

  cerrar(restaurarFoco = true): void {
    if (!this.open()) {
      document.body.style.overflow = '';
      return;
    }
    this.open.set(false);
    document.body.style.overflow = '';
    if (restaurarFoco) {
      document.querySelector<HTMLElement>('.portal-menu-btn')?.focus({ preventScroll: true });
    }
  }

  @HostListener('document:keydown.escape')
  protected alEscape(): void {
    this.cerrar();
  }

  @HostListener('window:resize')
  protected alRedimensionar(): void {
    if (window.innerWidth > 900) {
      this.cerrar(false);
    }
  }
}
