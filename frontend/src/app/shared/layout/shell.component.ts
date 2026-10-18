import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, HostListener, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthStore } from '../../core/auth/auth.store';
import { LoadingService } from '../../core/http/loading.service';
import { PALETTES, PaletteId, ThemeService } from '../../core/theme/theme.service';
import { CdToastHostComponent } from '../ui/toast/cd-toast-host.component';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, A11yModule, CdToastHostComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="cd-skip-link" href="#contenido">Saltar al contenido</a>

    @if (loading.active()) {
      <div class="progress" role="progressbar" aria-label="Cargando"></div>
    }

    <header class="topbar">
      <div class="topbar__inner">
        <button
          type="button"
          class="icono-btn"
          aria-label="Abrir menú de navegación"
          [attr.aria-expanded]="drawerOpen()"
          (click)="drawerOpen.set(true)"
        >
          <span aria-hidden="true">☰</span>
        </button>

        <a routerLink="/" class="marca" aria-label="CanchaDirecta, ir al inicio">
          <span class="marca__icono" aria-hidden="true">CD</span>
          <span class="marca__texto">CanchaDirecta</span>
        </a>

        <nav class="nav" aria-label="Navegación principal">
          <a routerLink="/" routerLinkActive="activo" [routerLinkActiveOptions]="{ exact: true }">Inicio</a>
          <a routerLink="/canchas" routerLinkActive="activo">Canchas</a>
          @if (auth.isAuthenticated()) {
            <a routerLink="/mis-reservas" routerLinkActive="activo">Mis reservas</a>
            <a routerLink="/reclamos" routerLinkActive="activo">Reclamos</a>
          }
          @if (auth.isOwner() || auth.isAdmin()) {
            <a routerLink="/panel" routerLinkActive="activo">Panel</a>
          }
          @if (auth.isAdmin()) {
            <a routerLink="/admin" routerLinkActive="activo">Admin</a>
          }
        </nav>

        <div class="cuenta">
          @if (auth.isAuthenticated()) {
            <a routerLink="/notificaciones" class="icono-btn" aria-label="Notificaciones">
              <span aria-hidden="true">◔</span>
            </a>
            <a routerLink="/perfil" class="usuario" [attr.aria-label]="'Perfil de ' + auth.fullName()">
              <span class="avatar" aria-hidden="true">{{ inicial() }}</span>
              <span class="usuario__nombre">{{ auth.fullName() }}</span>
            </a>
            <button type="button" class="cd-btn cd-btn--ghost cd-btn--sm" (click)="salir()">Salir</button>
          } @else {
            <a routerLink="/auth/login" class="cd-btn cd-btn--ghost cd-btn--sm">Ingresar</a>
            <a routerLink="/auth/registro" class="cd-btn cd-btn--primary cd-btn--sm registro-top">Crear cuenta</a>
          }
        </div>
      </div>
    </header>

    @if (drawerOpen()) {
      <div class="backdrop" (click)="drawerOpen.set(false)" aria-hidden="true"></div>
      <aside class="drawer" role="dialog" aria-modal="true" aria-label="Navegación" cdkTrapFocus [cdkTrapFocusAutoCapture]="true">
        <div class="drawer__cabecera">
          <strong>Menú</strong>
          <button type="button" class="icono-btn" aria-label="Cerrar menú" (click)="drawerOpen.set(false)">×</button>
        </div>
        <nav class="drawer__nav" aria-label="Navegación móvil" (click)="drawerOpen.set(false)">
          <a routerLink="/">Inicio</a>
          <a routerLink="/canchas">Canchas</a>
          @if (auth.isAuthenticated()) {
            <a routerLink="/mis-reservas">Mis reservas</a>
            <a routerLink="/reclamos">Reclamos</a>
            <a routerLink="/notificaciones">Notificaciones</a>
            <a routerLink="/perfil">Mi perfil</a>
          }
          @if (auth.isOwner() || auth.isAdmin()) {
            <a routerLink="/panel">Panel del propietario</a>
          }
          @if (auth.isAdmin()) {
            <a routerLink="/admin">Administración</a>
          }
          @if (!auth.isAuthenticated()) {
            <a routerLink="/auth/login">Ingresar</a>
            <a routerLink="/auth/registro">Crear cuenta</a>
          }
        </nav>
        <div class="drawer__paleta">
          <label class="cd-field">
            <span class="cd-field__label">Paleta de color</span>
            <select cdInput [value]="theme.palette()" (change)="cambiarPaleta($event)">
              @for (paleta of palettes; track paleta.id) {
                <option [value]="paleta.id">{{ paleta.nombre }}</option>
              }
            </select>
          </label>
        </div>
      </aside>
    }

    <main id="contenido" tabindex="-1">
      <router-outlet />
    </main>

    <footer class="pie">
      <div class="cd-container pie__inner">
        <p class="cd-xs cd-muted">CanchaDirecta · Pagos y liquidaciones son simulados.</p>
        <label class="pie__paleta">
          <span class="cd-xs cd-muted">Paleta</span>
          <select class="pie__select" [value]="theme.palette()" (change)="cambiarPaleta($event)" aria-label="Paleta de color">
            @for (paleta of palettes; track paleta.id) {
              <option [value]="paleta.id">{{ paleta.nombre }}</option>
            }
          </select>
        </label>
      </div>
    </footer>

    <cd-toast-host />
  `,
  styles: `
    :host {
      display: block;
      min-height: 100dvh;
    }
    .progress {
      position: fixed;
      inset: 0 0 auto 0;
      height: 3px;
      z-index: 1001;
      background: linear-gradient(90deg, var(--cd-color-accent), var(--cd-color-primary));
      animation: cargar 1.2s ease-in-out infinite;
      transform-origin: left;
    }
    @keyframes cargar {
      0% {
        transform: scaleX(0.05);
      }
      50% {
        transform: scaleX(0.6);
      }
      100% {
        transform: scaleX(1);
      }
    }
    .topbar {
      position: sticky;
      top: 0;
      z-index: 100;
      background: var(--cd-color-surface);
      border-bottom: 1px solid var(--cd-color-border);
    }
    .topbar__inner {
      display: flex;
      align-items: center;
      gap: var(--cd-space-3);
      min-height: var(--cd-topbar-height);
      max-width: var(--cd-container-max);
      margin-inline: auto;
      padding-inline: var(--cd-space-4);
    }
    .marca {
      display: inline-flex;
      align-items: center;
      gap: var(--cd-space-2);
      font-weight: 800;
      color: var(--cd-color-primary);
      text-decoration: none;
      white-space: nowrap;
    }
    .marca__icono {
      display: inline-grid;
      place-items: center;
      width: 34px;
      height: 34px;
      border-radius: 10px;
      background: var(--cd-color-primary);
      color: var(--cd-color-on-primary);
      font-size: 12px;
      letter-spacing: 0.04em;
    }
    .nav {
      display: none;
      gap: var(--cd-space-1);
      margin-inline: auto;
    }
    .nav a {
      padding: 8px 10px;
      border-radius: var(--cd-radius-sm);
      font-size: var(--cd-text-sm);
      font-weight: 600;
      color: var(--cd-color-text-muted);
      text-decoration: none;
    }
    .nav a:hover {
      background: var(--cd-color-surface-alt);
      color: var(--cd-color-text);
    }
    .nav a.activo {
      color: var(--cd-color-primary);
      background: var(--cd-color-surface-alt);
    }
    .cuenta {
      display: flex;
      align-items: center;
      gap: var(--cd-space-2);
      margin-left: auto;
    }
    .usuario {
      display: inline-flex;
      align-items: center;
      gap: var(--cd-space-2);
      text-decoration: none;
      color: var(--cd-color-text);
    }
    .usuario__nombre {
      display: none;
      font-size: var(--cd-text-sm);
      font-weight: 600;
      max-width: 160px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .avatar {
      display: inline-grid;
      place-items: center;
      width: 34px;
      height: 34px;
      border-radius: 50%;
      background: var(--cd-color-accent);
      color: #04211d;
      font-weight: 800;
    }
    .icono-btn {
      display: inline-grid;
      place-items: center;
      width: 40px;
      height: 40px;
      border: 1px solid transparent;
      border-radius: var(--cd-radius-sm);
      background: transparent;
      font-size: 20px;
      cursor: pointer;
      color: var(--cd-color-text);
      text-decoration: none;
    }
    .icono-btn:hover {
      background: var(--cd-color-surface-alt);
    }
    .backdrop {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, 0.45);
      z-index: 300;
    }
    .drawer {
      position: fixed;
      inset: 0 auto 0 0;
      width: min(320px, 88vw);
      background: var(--cd-color-surface);
      z-index: 310;
      display: flex;
      flex-direction: column;
      padding: var(--cd-space-4);
      gap: var(--cd-space-4);
      box-shadow: var(--cd-shadow-3);
      overflow-y: auto;
    }
    .drawer__cabecera {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .drawer__nav {
      display: grid;
      gap: 2px;
    }
    .drawer__nav a {
      padding: 12px;
      border-radius: var(--cd-radius-sm);
      text-decoration: none;
      color: var(--cd-color-text);
      font-weight: 600;
    }
    .drawer__nav a:hover {
      background: var(--cd-color-surface-alt);
    }
    .drawer__paleta {
      margin-top: auto;
    }
    main {
      min-height: calc(100dvh - var(--cd-topbar-height) - 70px);
    }
    .pie {
      border-top: 1px solid var(--cd-color-border);
      background: var(--cd-color-surface);
      padding-block: var(--cd-space-4);
    }
    .pie__inner {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--cd-space-3);
      flex-wrap: wrap;
    }
    .pie__paleta {
      display: inline-flex;
      align-items: center;
      gap: var(--cd-space-2);
    }
    .pie__select {
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-sm);
      padding: 4px 8px;
      background: var(--cd-color-surface);
      font-size: var(--cd-text-xs);
    }
    @media (min-width: 1024px) {
      .topbar > .topbar__inner > .icono-btn {
        display: none;
      }
      .nav {
        display: flex;
      }
      .usuario__nombre {
        display: inline;
      }
    }
    @media (max-width: 1023px) {
      .nav {
        display: none;
      }
    }
    @media (max-width: 640px) {
      .registro-top {
        display: none;
      }
      .topbar__inner {
        padding-inline: var(--cd-space-3);
        gap: var(--cd-space-2);
      }
    }
  `,
})
export class ShellComponent {
  protected readonly auth = inject(AuthStore);
  protected readonly loading = inject(LoadingService);
  protected readonly theme = inject(ThemeService);
  private readonly router = inject(Router);

  protected readonly palettes = PALETTES;
  protected readonly drawerOpen = signal(false);
  protected readonly inicial = computed(() => this.auth.fullName().charAt(0).toUpperCase() || '?');

  @HostListener('document:keydown.escape')
  protected cerrarDrawer(): void {
    this.drawerOpen.set(false);
  }

  protected cambiarPaleta(event: Event): void {
    const value = (event.target as HTMLSelectElement).value as PaletteId;
    this.theme.setPalette(value);
  }

  protected async salir(): Promise<void> {
    await this.auth.logout();
    await this.router.navigate(['/']);
  }
}
