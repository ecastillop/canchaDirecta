import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { AuthStore } from '../../core/auth/auth.store';
import { RecepcionStore } from '../../core/api/recepcion.store';
import { PortalIconComponent } from '../../shared/ui/portal-icon/portal-icon.component';
import { CdThemeToggleComponent } from '../../shared/ui/theme-toggle/cd-theme-toggle.component';
import { PortalDrawerDirective } from '../../shared/layout/portal-drawer.directive';

const TITULOS: Record<string, string> = {
  '/recepcion': 'Turno actual',
  '/recepcion/agenda': 'Agenda del día',
  '/recepcion/reservas': 'Reservas',
  '/recepcion/nueva': 'Reserva presencial',
  '/recepcion/cobros': 'Cobros',
  '/recepcion/incidencias': 'Incidencias',
};

/** Portal de recepcion: turno, agenda, reserva presencial, cobros e incidencias (ADR-0038). */
@Component({
  selector: 'app-recepcion-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, PortalIconComponent, CdThemeToggleComponent, PortalDrawerDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="admin-shell" cdPortalDrawer #drawer="cdPortalDrawer">
      <div class="portal-backdrop" [class.visible]="drawer.open()" (click)="drawer.cerrar()" aria-hidden="true"></div>
      <aside class="admin-side" id="recepcion-menu" [class.abierto]="drawer.open()">
        <button type="button" class="drawer-close" (click)="drawer.cerrar()" aria-label="Cerrar menú">×</button>
        <a routerLink="/" class="marca-lateral" aria-label="CanchaDirecta, ir al inicio">
          <span class="logo-symbol" aria-hidden="true"><span></span><span></span></span>
          <span class="logo-name">
            cancha<span>directa</span>
            <small>RESERVA · JUEGA · REPITE</small>
          </span>
        </a>

        <div class="workspace-label">ESPACIO DE TRABAJO <span>Recepción</span></div>
        <nav class="admin-side__nav" aria-label="Menú de recepción">
          <a routerLink="/recepcion" routerLinkActive="activo" [routerLinkActiveOptions]="{ exact: true }">
            <cd-portal-icon name="grid" /> Turno actual
          </a>
          <a routerLink="/recepcion/agenda" routerLinkActive="activo">
            <cd-portal-icon name="calendar" /> Agenda del día
          </a>
          <a routerLink="/recepcion/reservas" routerLinkActive="activo">
            <cd-portal-icon name="ticket" /> Reservas
          </a>
          <a routerLink="/recepcion/nueva" routerLinkActive="activo">
            <cd-portal-icon name="plus" /> Reserva presencial
          </a>
          <a routerLink="/recepcion/cobros" routerLinkActive="activo">
            <cd-portal-icon name="credit" /> Cobros
          </a>
          <a routerLink="/recepcion/incidencias" routerLinkActive="activo">
            <cd-portal-icon name="alert" /> Incidencias
          </a>
        </nav>

        <div class="admin-side__end">
          <div class="admin-side__help">
            <span class="help-signal" aria-hidden="true"><cd-portal-icon name="spark" [size]="16" /></span>
            <strong>{{ sedeNombre() }}</strong>
            <p>Operas solo las sedes que te asigna la administración.</p>
          </div>
          <a routerLink="/" class="admin-side__site"><cd-portal-icon name="logout" [size]="17" /> Volver al sitio</a>
          <button type="button" class="admin-side__salir" (click)="salir()">Cerrar sesión</button>
        </div>
      </aside>

      <div class="admin-main">
        <header class="portal-top">
          <button
            type="button"
            class="portal-menu-btn"
            (click)="drawer.alternar()"
            [attr.aria-expanded]="drawer.open()"
            aria-controls="recepcion-menu"
            aria-label="Abrir menú de recepción"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
          </button>
          <div class="portal-top-left">
            <span class="breadcrumb">CanchaDirecta <span>/</span> Recepción</span>
            <h1 class="portal-title">{{ titulo() }}</h1>
          </div>
          <div class="portal-actions">
            <cd-theme-toggle />
            <a routerLink="/notificaciones" class="icono-mini" aria-label="Notificaciones">
              <cd-portal-icon name="bell" [size]="19" />
            </a>
            <span class="avatar" aria-hidden="true">{{ iniciales() }}</span>
          </div>
        </header>

        <div class="portal-content">
          <router-outlet />
        </div>
      </div>
    </div>
  `,
  styles: `
    .admin-shell {
      display: grid;
      grid-template-columns: 252px minmax(0, 1fr);
      min-height: 100dvh;
      background: var(--cd-color-bg);
    }
    .admin-side {
      position: sticky;
      top: 0;
      align-self: start;
      display: flex;
      flex-direction: column;
      height: 100dvh;
      min-width: 0;
      padding: 27px 14px 18px;
      background: #121f21;
      color: #ddece5;
    }
    .admin-main {
      min-width: 0;
    }
    .marca-lateral {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      padding: 5px 13px 26px;
      color: #fff;
      text-decoration: none;
      white-space: nowrap;
    }
    .logo-symbol {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 4px;
      width: 42px;
      height: 42px;
      border-radius: 12px;
      background: var(--cd-color-accent-soft);
      transform: rotate(-7deg);
    }
    .logo-symbol span {
      width: 6px;
      height: 24px;
      border: 3px solid #12352c;
      border-radius: 10px;
      transform: rotate(27deg);
    }
    .logo-symbol span + span {
      transform: rotate(-27deg);
    }
    .logo-name {
      font-family: var(--cd-font-display);
      font-size: 21px;
      font-weight: 900;
      line-height: 0.93;
      letter-spacing: -0.055em;
      color: #fff;
    }
    .logo-name > span {
      color: #9ed77c;
    }
    .logo-name small {
      display: block;
      margin-top: 6px;
      color: #a0b3a8;
      font-size: 7px;
      font-weight: 800;
      letter-spacing: 0.2em;
      text-transform: uppercase;
    }
    .workspace-label {
      margin: 13px 11px 19px;
      color: #607c76;
      font-size: 9px;
      font-weight: 900;
      letter-spacing: 0.17em;
      text-transform: uppercase;
    }
    .workspace-label span {
      display: block;
      margin-top: 9px;
      color: #b9d0c4;
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0;
      text-transform: none;
    }
    .admin-side__nav {
      display: grid;
      gap: 3px;
      flex: 1 1 auto;
      min-height: 0;
      overflow-y: auto;
      align-content: start;
    }
    .admin-side__nav a {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 14px;
      border-radius: 10px;
      color: #96b0a7;
      font-size: 12px;
      font-weight: 700;
      text-decoration: none;
    }
    .admin-side__nav a:hover {
      background: #233b37;
      color: #fff;
    }
    .admin-side__nav a.activo {
      background: #284638;
      color: #ddffd4;
      box-shadow: inset 3px 0 #b4ed7c;
    }
    .admin-side__nav cd-portal-icon {
      opacity: 0.85;
    }
    .admin-side__end {
      margin-top: auto;
      display: grid;
      gap: 6px;
      flex-shrink: 0;
    }
    .admin-side__help {
      margin: 25px 0 17px;
      padding: 16px;
      border: 1px solid #2b433a;
      border-radius: 13px;
      background: #20332f;
    }
    .help-signal {
      display: grid;
      place-items: center;
      width: 32px;
      height: 32px;
      margin-bottom: 13px;
      border-radius: 9px;
      background: var(--cd-color-accent-soft);
      color: #214d37;
    }
    .admin-side__help strong {
      font-size: 12px;
    }
    .admin-side__help p {
      margin: 6px 0 0;
      color: #809f90;
      font-size: 11px;
      line-height: 1.6;
    }
    .admin-side__site,
    .admin-side__salir {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 14px;
      border: 0;
      border-radius: 10px;
      background: transparent;
      color: #96b0a7;
      font: inherit;
      font-size: 12px;
      font-weight: 700;
      text-align: left;
      text-decoration: none;
      cursor: pointer;
    }
    .admin-side__site:hover {
      background: #233b37;
      color: #fff;
    }
    .admin-side__salir:hover {
      background: #3a2628;
      color: #ffb7b7;
    }
    .portal-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 15px;
      height: 78px;
      padding: 0 34px;
      border-bottom: 1px solid var(--cd-color-border);
      background: var(--cd-color-surface);
    }
    .portal-top-left {
      display: flex;
      flex-direction: column;
      gap: 3px;
    }
    .breadcrumb {
      color: #9aa99e;
      font-size: 10px;
    }
    .breadcrumb span {
      margin: 0 4px;
      color: #bdcfc0;
    }
    .portal-title {
      margin: 0;
      font-family: var(--cd-font-display);
      font-size: 18px;
      letter-spacing: -0.02em;
    }
    .portal-actions {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .icono-mini {
      display: grid;
      place-items: center;
      width: 36px;
      height: 36px;
      border-radius: 10px;
      color: var(--cd-color-text-muted);
      text-decoration: none;
    }
    .icono-mini:hover {
      background: var(--cd-color-surface-alt);
      color: var(--cd-color-text);
    }
    .avatar {
      display: grid;
      place-items: center;
      width: 36px;
      height: 36px;
      border-radius: 10px;
      background: var(--cd-color-success-soft);
      color: var(--cd-color-success);
      font-family: var(--cd-font-display);
      font-size: 12px;
      font-weight: 900;
    }
    .portal-content {
      width: 100%;
      max-width: 1520px;
      margin: 0 auto;
      padding: 35px 35px 90px;
      min-width: 0;
    }
    @media (max-width: 900px) {
      .admin-shell {
        grid-template-columns: 1fr;
      }
      .portal-backdrop {
        display: block;
        position: fixed;
        inset: 0;
        z-index: 70;
        background: rgba(10, 20, 18, 0.58);
        opacity: 0;
        visibility: hidden;
        transition: opacity 0.22s ease, visibility 0.22s ease;
      }
      .portal-backdrop.visible {
        opacity: 1;
        visibility: visible;
      }
      .admin-side {
        position: fixed;
        top: 0;
        left: 0;
        z-index: 80;
        width: min(84vw, 304px);
        max-width: 304px;
        height: 100dvh;
        min-height: 0;
        padding: 22px 14px 18px;
        overflow-y: auto;
        border: 0;
        border-right: 1px solid #2b433a;
        display: block;
        transform: translateX(-100%);
        visibility: hidden;
        transition: transform 0.24s ease, visibility 0.24s ease;
      }
      .admin-side.abierto {
        transform: none;
        visibility: visible;
      }
      .drawer-close {
        display: grid;
        place-items: center;
        position: absolute;
        top: 16px;
        right: 14px;
        z-index: 2;
        width: 32px;
        height: 32px;
        border: 1px solid #365049;
        border-radius: 8px;
        background: #1b2f2b;
        color: #dfeee6;
        font-size: 17px;
        line-height: 1;
        cursor: pointer;
      }
      .marca-lateral {
        padding-right: 44px;
      }
      .admin-side__nav {
        display: grid;
        gap: 3px;
        width: auto;
        max-width: none;
        overflow: visible;
      }
      .admin-side__nav a {
        padding: 12px 14px;
      }
      .admin-side__end {
        margin-top: 24px;
      }
      .portal-menu-btn {
        display: inline-grid;
        place-items: center;
        flex: none;
        width: 44px;
        height: 44px;
        padding: 0;
        border: 0;
        border-radius: 10px;
        background: transparent;
        color: var(--cd-color-text);
        cursor: pointer;
      }
      .portal-menu-btn:hover {
        background: var(--cd-color-surface-alt);
      }
      .portal-menu-btn svg {
        display: block;
        width: 24px;
        height: 24px;
      }
      .portal-top {
        position: sticky;
        top: 0;
        z-index: 50;
        height: 64px;
        padding: 0 14px;
        gap: 10px;
      }
      .portal-top-left {
        flex: 1;
        min-width: 0;
      }
      .portal-content {
        padding: 20px 18px 70px;
      }
    }
  `,
})
export class RecepcionLayoutComponent {
  private readonly router = inject(Router);
  private readonly recepcion = inject(RecepcionStore);
  protected readonly auth = inject(AuthStore);

  constructor() {
    void this.recepcion.init();
  }

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((evento): evento is NavigationEnd => evento instanceof NavigationEnd),
      map(() => this.router.url.split('?')[0]),
      startWith(this.router.url.split('?')[0]),
    ),
    { initialValue: this.router.url.split('?')[0] },
  );

  protected readonly titulo = computed(() => TITULOS[this.url()] ?? 'Recepción');
  protected readonly sedeNombre = computed(() => this.recepcion.sede()?.nombre ?? 'Recepción');
  protected readonly iniciales = computed(
    () =>
      this.auth
        .fullName()
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map((parte) => parte.charAt(0).toUpperCase())
        .join('') || 'CD',
  );

  protected async salir(): Promise<void> {
    await this.auth.logout();
    await this.router.navigate(['/']);
  }
}
