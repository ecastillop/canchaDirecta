import { A11yModule } from '@angular/cdk/a11y';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  HostListener,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { NotificacionesStore } from '../../core/api/notificaciones.store';
import { AuthStore } from '../../core/auth/auth.store';
import { LoadingService } from '../../core/http/loading.service';
import { ThemeService } from '../../core/theme/theme.service';
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

    @if (!portal()) {
      <header class="topbar">
      <div class="topbar__inner">
        <button
          type="button"
          class="icono-btn menu-btn"
          aria-label="Abrir menú de navegación"
          [attr.aria-expanded]="drawerOpen()"
          (click)="drawerOpen.set(true)"
        >
          <span aria-hidden="true">☰</span>
        </button>

        <a routerLink="/" class="marca" aria-label="CanchaDirecta, ir al inicio">
          <span class="marca__icono" aria-hidden="true">A</span>
          <span class="marca__texto">
            <strong>cancha<span>directa</span></strong>
            <small>reserva · juega · repite</small>
          </span>
        </a>

        <nav class="nav" aria-label="Navegación principal">
          <a routerLink="/" routerLinkActive="activo" [routerLinkActiveOptions]="{ exact: true }">Inicio</a>
          <a routerLink="/canchas" routerLinkActive="activo">Espacios</a>
          <a routerLink="/actividades" routerLinkActive="activo">Actividades</a>
          <a routerLink="/como-funciona" routerLinkActive="activo">Cómo funciona</a>
          <a routerLink="/ayuda" routerLinkActive="activo">Ayuda</a>
        </nav>

        <div class="cuenta">
          <button
            type="button"
            class="icono-btn"
            (click)="theme.alternarModo()"
            [attr.aria-pressed]="theme.oscuro()"
            [attr.aria-label]="theme.oscuro() ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'"
            title="Modo claro u oscuro"
          >
            <span aria-hidden="true">{{ theme.oscuro() ? '☾' : '☀' }}</span>
          </button>
          @if (auth.isAuthenticated()) {
            <a
              routerLink="/notificaciones"
              class="icono-btn"
              [attr.aria-label]="
                badge() > 0 ? 'Notificaciones, ' + badge() + ' sin leer' : 'Notificaciones'
              "
            >
              <span aria-hidden="true">◔</span>
              @if (badge() > 0) {
                <span class="icono-badge" aria-hidden="true">{{ badge() > 9 ? '9+' : badge() }}</span>
              }
            </a>
          }
          <details #menuCuenta class="cuenta-menu">
            <summary class="cd-btn cd-btn--primary cd-btn--sm cuenta-btn">
              Mi cuenta <span aria-hidden="true">→</span>
            </summary>
            <div class="cuenta-menu__panel" (click)="menuCuenta.open = false">
              @if (auth.isAuthenticated()) {
                <div class="cuenta-menu__perfil">
                  <span class="avatar" aria-hidden="true">{{ inicial() }}</span>
                  <div>
                    <strong>{{ auth.fullName() }}</strong>
                    <small>{{ auth.user()?.correo }}</small>
                  </div>
                </div>
                <a routerLink="/mis-reservas">Mis reservas</a>
                <a routerLink="/favoritos">Favoritos</a>
                <a routerLink="/notificaciones">Notificaciones</a>
                <a routerLink="/ayuda">Centro de ayuda</a>
                <a routerLink="/reclamos">Reclamos</a>
                <a routerLink="/perfil">Mi perfil</a>
                @if (auth.isOwner() || auth.isAdmin()) {
                  <a routerLink="/panel">Panel del propietario</a>
                }
                @if (auth.isOperador()) {
                  <a routerLink="/recepcion">Recepción</a>
                }
                @if (auth.isAdmin()) {
                  <a routerLink="/admin">Administración</a>
                }
                <button type="button" class="cuenta-menu__salir" (click)="salir()">Cerrar sesión</button>
              } @else {
                <p class="cd-small cd-muted">Ingresa para reservar y guardar tus canchas favoritas.</p>
                <a routerLink="/auth/login" class="cd-btn cd-btn--primary cd-btn--sm cd-btn--block">Ingresar</a>
                <a routerLink="/auth/registro" class="cd-btn cd-btn--secondary cd-btn--sm cd-btn--block">Crear cuenta</a>
              }
            </div>
          </details>
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
          <a routerLink="/canchas">Espacios</a>
          <a routerLink="/actividades">Actividades</a>
          <a routerLink="/como-funciona">Cómo funciona</a>
          <a routerLink="/ayuda">Centro de ayuda</a>
          @if (auth.isAuthenticated()) {
            <a routerLink="/mis-reservas">Mis reservas</a>
            <a routerLink="/favoritos">Favoritos</a>
            <a routerLink="/reclamos">Reclamos</a>
            <a routerLink="/notificaciones">Notificaciones</a>
            <a routerLink="/perfil">Mi perfil</a>
          }
          @if (auth.isOwner() || auth.isAdmin()) {
            <a routerLink="/panel">Panel del propietario</a>
          }
          @if (auth.isOperador()) {
            <a routerLink="/recepcion">Recepción</a>
          }
          @if (auth.isAdmin()) {
            <a routerLink="/admin">Administración</a>
          }
          @if (!auth.isAuthenticated()) {
            <a routerLink="/auth/login">Ingresar</a>
            <a routerLink="/auth/registro">Mi cuenta</a>
          }
        </nav>
      </aside>
    }
    }

    <main id="contenido" tabindex="-1">
      <router-outlet />
    </main>

    @if (!portal()) {
      <footer class="pie">
      <div class="cd-container pie__grid">
        <div class="pie__marca">
          <a routerLink="/" class="marca marca--pie" aria-label="CanchaDirecta, ir al inicio">
            <span class="marca__icono" aria-hidden="true">A</span>
            <span class="marca__texto">
              <strong>cancha<span>directa</span></strong>
              <small>reserva · juega · repite</small>
            </span>
          </a>
          <p>La plataforma donde tus ganas de jugar encuentran su lugar.</p>
        </div>
        <div class="pie__col">
          <strong>Explora</strong>
          <a routerLink="/canchas">Buscar espacios</a>
          <a routerLink="/canchas">Comparar canchas</a>
          <a routerLink="/actividades">Actividades</a>
          <a routerLink="/como-funciona">Cómo funciona</a>
        </div>
        <div class="pie__col">
          <strong>Plataforma</strong>
          @if (auth.isAuthenticated()) {
            <a routerLink="/perfil">Mi cuenta</a>
          } @else {
            <a routerLink="/auth/login">Mi cuenta</a>
          }
          @if (auth.isOwner() || auth.isAdmin()) {
            <a routerLink="/panel">Para propietarios</a>
          } @else {
            <a routerLink="/perfil">Para propietarios</a>
          }
          @if (auth.isAdmin()) {
            <a routerLink="/admin">Administración</a>
          }
        </div>
        <div class="pie__col">
          <strong>Ayuda</strong>
          <a routerLink="/ayuda">Centro de ayuda</a>
          <a routerLink="/reclamos">Reclamos</a>
          <span>soporte&#64;canchadirecta.demo</span>
          <span>Lima, Perú</span>
        </div>
      </div>
      <div class="cd-container pie__bottom">
        <span>© {{ year }} CanchaDirecta · Pagos y liquidaciones son simulados.</span>
      </div>
      </footer>
    }

    <cd-toast-host />
  `,
  styles: `
    :host {
      display: block;
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
      position: relative;
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
    .icono-badge {
      position: absolute;
      top: 2px;
      right: 2px;
      min-width: 18px;
      height: 18px;
      padding: 0 4px;
      display: inline-grid;
      place-items: center;
      border-radius: var(--cd-radius-pill);
      background: var(--cd-color-danger);
      color: #fff;
      font-size: 11px;
      font-weight: 700;
      line-height: 1;
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
    /* Fusion skin */
    .topbar {
      background: color-mix(in srgb, var(--cd-color-surface) 94%, transparent);
      backdrop-filter: blur(14px);
    }
    .topbar__inner {
      max-width: var(--cd-container-max);
      min-height: var(--cd-topbar-height);
      padding-inline: var(--cd-space-5);
      gap: var(--cd-space-5);
    }
    .marca {
      gap: 10px;
    }
    .marca__icono {
      width: 42px;
      height: 42px;
      border-radius: 13px;
      background: var(--cd-color-accent-soft);
      color: var(--cd-color-text);
      font-family: var(--cd-font-display);
      font-size: 22px;
      font-weight: 900;
      transform: rotate(-6deg);
    }
    .marca__texto {
      display: grid;
      line-height: 1;
    }
    .marca__texto strong {
      font-family: var(--cd-font-display);
      font-size: 20px;
      font-weight: 800;
      letter-spacing: -0.04em;
      color: var(--cd-color-text);
    }
    .marca__texto strong span {
      color: var(--cd-color-accent);
    }
    .marca__texto small {
      margin-top: 4px;
      color: var(--cd-color-text-muted);
      font-size: 8px;
      font-weight: 700;
      letter-spacing: 0.18em;
      text-transform: uppercase;
    }
    .nav {
      height: var(--cd-topbar-height);
      gap: clamp(14px, 2.2vw, 32px);
      align-items: center;
    }
    .nav a {
      position: relative;
      padding: 0;
      border-radius: 0;
      color: var(--cd-color-text);
      font-size: 14px;
      font-weight: 500;
    }
    .nav a:hover {
      background: transparent;
      color: var(--cd-color-primary);
    }
    .nav a.activo {
      background: transparent;
      font-weight: 700;
    }
    .nav a::after {
      content: '';
      position: absolute;
      left: 0;
      right: 0;
      bottom: 0;
      height: 3px;
      background: transparent;
    }
    .nav a.activo::after {
      background: var(--cd-color-accent-strong);
    }
    .cuenta {
      gap: var(--cd-space-3);
    }
    .usuario {
      padding: 6px 12px 6px 6px;
      border-radius: var(--cd-radius-pill);
      background: var(--cd-color-primary);
      color: var(--cd-color-on-primary);
    }
    .usuario:hover {
      color: var(--cd-color-on-primary);
    }
    .avatar {
      width: 30px;
      height: 30px;
      background: var(--cd-color-accent);
      color: var(--cd-color-primary);
    }
    .cuenta-btn {
      padding-inline: 18px;
      border-radius: var(--cd-radius-pill);
      list-style: none;
      cursor: pointer;
    }
    .cuenta-btn::-webkit-details-marker {
      display: none;
    }
    .cuenta-menu {
      position: relative;
    }
    .cuenta-menu__panel {
      position: absolute;
      right: 0;
      top: calc(100% + 10px);
      z-index: 250;
      display: grid;
      gap: 3px;
      width: min(270px, calc(100vw - 24px));
      padding: 10px;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-md);
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-3);
    }
    .cuenta-menu__perfil {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 8px 8px 12px;
      border-bottom: 1px solid var(--cd-color-border);
      margin-bottom: 4px;
    }
    .cuenta-menu__perfil strong,
    .cuenta-menu__perfil small {
      display: block;
    }
    .cuenta-menu__perfil strong {
      font-size: 13px;
    }
    .cuenta-menu__perfil small {
      max-width: 180px;
      overflow: hidden;
      color: var(--cd-color-text-muted);
      font-size: 10px;
      text-overflow: ellipsis;
    }
    .cuenta-menu__panel > a {
      padding: 9px 10px;
      border-radius: 8px;
      color: var(--cd-color-text);
      font-size: 13px;
      font-weight: 500;
      text-decoration: none;
    }
    .cuenta-menu__panel > a:not(.cd-btn):hover {
      background: var(--cd-color-surface-alt);
    }
    .cuenta-menu__panel .cd-btn {
      margin-top: 4px;
      text-decoration: none;
    }
    .cuenta-menu__panel > a.cd-btn--primary {
      color: var(--cd-color-on-primary);
    }
    .cuenta-menu__panel > a.cd-btn--secondary {
      color: var(--cd-color-primary);
    }
    .cuenta-menu__salir {
      margin-top: 4px;
      padding: 9px 10px;
      border: 0;
      border-radius: 8px;
      background: transparent;
      color: var(--cd-color-danger);
      font-size: 12px;
      text-align: left;
      cursor: pointer;
    }
    .cuenta-menu__salir:hover {
      background: var(--cd-color-danger-soft);
    }
    .pie {
      border-top: 0;
      background: var(--cd-color-inverse-bg);
      color: var(--cd-color-inverse-text);
      padding: var(--cd-space-7) 0 var(--cd-space-4);
    }
    .pie__grid {
      display: grid;
      grid-template-columns: 1.5fr 1fr 1fr 1fr;
      gap: var(--cd-space-6);
    }
    .pie__marca p {
      max-width: 34ch;
      margin-top: var(--cd-space-4);
      color: var(--cd-color-inverse-muted);
      font-size: var(--cd-text-sm);
    }
    .marca--pie .marca__texto strong {
      color: var(--cd-color-inverse-text);
    }
    .marca--pie .marca__texto strong span {
      color: var(--cd-color-brand-light);
    }
    .marca--pie .marca__texto small {
      color: var(--cd-color-inverse-muted);
    }
    .pie__col {
      display: grid;
      align-content: start;
      gap: 10px;
    }
    .pie__col strong {
      font-family: var(--cd-font-display);
      font-size: var(--cd-text-xs);
      letter-spacing: 0.14em;
      text-transform: uppercase;
    }
    .pie__col a,
    .pie__col span {
      color: var(--cd-color-inverse-muted);
      font-size: 13px;
      text-decoration: none;
    }
    .pie__col a:hover {
      color: var(--cd-color-brand-light);
    }
    .pie__bottom {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--cd-space-3);
      flex-wrap: wrap;
      margin-top: var(--cd-space-7);
      padding-top: var(--cd-space-4);
      border-top: 1px solid color-mix(in srgb, var(--cd-color-inverse-text) 16%, transparent);
      color: var(--cd-color-inverse-muted);
      font-size: var(--cd-text-xs);
    }
    .pie__paleta {
      color: var(--cd-color-inverse-muted);
    }
    .pie__select {
      border-color: color-mix(in srgb, var(--cd-color-inverse-text) 22%, transparent);
      background: transparent;
      color: var(--cd-color-inverse-text);
    }
    @media (max-width: 900px) {
      .pie__grid {
        grid-template-columns: 1fr 1fr;
      }
    }
    @media (max-width: 640px) {
      .marca__texto {
        display: none;
      }
      .cuenta .cd-btn--ghost,
      .salir {
        display: none;
      }
      .cuenta-btn span {
        display: none;
      }
      .pie__grid {
        grid-template-columns: 1fr;
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
  private readonly notificaciones = inject(NotificacionesStore);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly badge = this.notificaciones.noLeidas;
  /** Mantiene el badge de la campana al dia con la sesion (carga al entrar, limpia al salir). */
  private readonly sincronizarBadge = effect(() => {
    if (this.auth.isAuthenticated()) this.notificaciones.cargar();
    else this.notificaciones.limpiar();
  });

  protected readonly year = new Date().getFullYear();
  protected readonly drawerOpen = signal(false);
  protected readonly inicial = computed(() => this.auth.fullName().charAt(0).toUpperCase() || '?');
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((evento): evento is NavigationEnd => evento instanceof NavigationEnd),
      map(() => this.router.url),
      startWith(this.router.url),
    ),
    { initialValue: this.router.url },
  );
  /** Las areas privadas usan su propio portal (sidebar + topbar) sin cabecera publica. */
  protected readonly portal = computed(() =>
    /^\/(panel|mi-resumen|mis-reservas|mis-inscripciones|favoritos|notificaciones|reclamos|perfil|admin|recepcion)(\/|$)/.test(
      this.url(),
    ),
  );
  /** Ruta (sin query ni fragmento) de la pantalla anterior. */
  private rutaAnterior = this.router.url.split(/[?#]/)[0];

  constructor() {
    this.router.events
      .pipe(
        filter((evento): evento is NavigationEnd => evento instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((evento) => {
        const destino = evento.urlAfterRedirects.split(/[?#]/)[0];
        const anterior = this.rutaAnterior;
        this.rutaAnterior = destino;
        if (destino === anterior) return;
        // Al cambiar de pantalla el foco vuelve al contenido: el lector de pantalla
        // anuncia el nuevo contexto y el teclado no se queda en un enlace obsoleto.
        setTimeout(() => document.getElementById('contenido')?.focus({ preventScroll: true }), 0);
      });
  }

  @HostListener('document:keydown.escape')
  protected cerrarDrawer(): void {
    this.drawerOpen.set(false);
  }

  protected async salir(): Promise<void> {
    await this.auth.logout();
    await this.router.navigate(['/']);
  }
}
