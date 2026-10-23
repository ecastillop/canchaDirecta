import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { OwnerStore } from '../../core/api/owner.store';
import { AuthStore } from '../../core/auth/auth.store';
import { PortalIconComponent } from '../../shared/ui/portal-icon/portal-icon.component';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdThemeToggleComponent } from '../../shared/ui/theme-toggle/cd-theme-toggle.component';
import { PortalDrawerDirective } from '../../shared/layout/portal-drawer.directive';

@Component({
  selector: 'app-panel-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CdSkeletonComponent, PortalIconComponent, CdThemeToggleComponent, PortalDrawerDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="panel-shell" cdPortalDrawer #drawer="cdPortalDrawer">
      <div class="portal-backdrop" [class.visible]="drawer.open()" (click)="drawer.cerrar()" aria-hidden="true"></div>
      <aside class="panel-side" id="panel-menu" [class.abierto]="drawer.open()">
        <button type="button" class="drawer-close" (click)="drawer.cerrar()" aria-label="Cerrar menú">×</button>
        <a routerLink="/" class="marca-lateral" aria-label="CanchaDirecta, ir al inicio">
          <span class="logo-symbol" aria-hidden="true"><span></span><span></span></span>
          <span class="logo-name">
            cancha<span>directa</span>
            <small>RESERVA · JUEGA · REPITE</small>
          </span>
        </a>

        <div class="workspace-label">ESPACIO DE TRABAJO <span>Propietario</span></div>
        <nav class="panel-side__nav" aria-label="Menú del panel del propietario">
          <a routerLink="/panel" routerLinkActive="activo" [routerLinkActiveOptions]="{ exact: true }">
            <cd-portal-icon name="grid" /> Resumen
          </a>
          <a routerLink="/panel/metricas" routerLinkActive="activo"><cd-portal-icon name="chart" /> Métricas</a>
          <a routerLink="/panel/reservas" routerLinkActive="activo"><cd-portal-icon name="ticket" /> Reservas</a>
          <a routerLink="/panel/agenda" routerLinkActive="activo"><cd-portal-icon name="calendar" /> Agenda</a>
          <a routerLink="/panel/actividades" routerLinkActive="activo"><cd-portal-icon name="spark" /> Actividades</a>
          <a routerLink="/panel/promociones" routerLinkActive="activo"><cd-portal-icon name="receipt" /> Promociones</a>
          <a routerLink="/panel/liquidaciones" routerLinkActive="activo"><cd-portal-icon name="wallet" /> Liquidaciones</a>
        </nav>

        <div class="panel-side__end">
          <div class="panel-side__help">
            <span class="help-signal" aria-hidden="true"><cd-portal-icon name="spark" [size]="16" /></span>
            <strong>Entorno de prueba</strong>
            <p>Datos reales del backend. Pagos y liquidaciones simulados.</p>
          </div>
          <a routerLink="/" class="panel-side__site"><cd-portal-icon name="logout" [size]="17" /> Volver al sitio</a>
          <button type="button" class="panel-side__salir" (click)="salir()">Cerrar sesión</button>
        </div>
      </aside>

      <div class="panel-main">
        <header class="portal-top">
          <button
            type="button"
            class="portal-menu-btn"
            (click)="drawer.alternar()"
            [attr.aria-expanded]="drawer.open()"
            aria-controls="panel-menu"
            aria-label="Abrir menú del panel"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
          </button>
          <div class="portal-top-left">
            <span class="breadcrumb">CanchaDirecta <span>/</span> Propietario</span>
            <h1 class="portal-title">Gestión de espacios</h1>
          </div>
          <div class="portal-actions">
            <cd-theme-toggle />
            <a routerLink="/notificaciones" class="icono-mini" aria-label="Notificaciones">
              <cd-portal-icon name="bell" [size]="19" />
            </a>
            <a routerLink="/canchas" class="accion-sitio">Ver sitio →</a>
            <span class="avatar" aria-hidden="true">{{ iniciales() }}</span>
          </div>
        </header>

        <div class="portal-content">
          @if (store.cargando()) {
            <cd-skeleton height="80px" radius="12px" />
          } @else if (auth.isAdmin() && !auth.isOwner()) {
            <div class="cd-card pendiente" role="status">
              <h2>Cuenta de administración</h2>
              <p class="cd-muted">
                No necesitas solicitar el rol de propietario. Si vas a publicar canchas propias, asigna el rol
                <strong>OWNER</strong> a tu cuenta desde Administración → Usuarios → Roles. Si solo vas a supervisar
                la operación, usa Administración → Supervisión.
              </p>
              <div class="cd-row">
                <a routerLink="/admin" class="cd-btn cd-btn--primary">Ir a Administración</a>
                <a routerLink="/perfil" class="cd-btn cd-btn--secondary">Ver mi perfil</a>
              </div>
            </div>
          } @else if (!store.aprobado()) {
            <div class="cd-card pendiente" role="status">
              <h2>Solicitud de propietario no aprobada</h2>
              <p class="cd-muted">
                @if (store.solicitud(); as s) {
                  Tu solicitud está en estado <strong>{{ s.estadoValidacion }}</strong>. Un administrador debe
                  validarla antes de publicar canchas.
                } @else {
                  Aún no tienes solicitud de propietario. Envía tu documento desde tu perfil.
                }
              </p>
              <a routerLink="/perfil" class="cd-btn cd-btn--primary">Ir a mi perfil</a>
            </div>
          } @else {
            <router-outlet />
          }
        </div>
      </div>
    </div>
  `,
  styles: `
    .panel-shell {
      display: grid;
      grid-template-columns: 252px minmax(0, 1fr);
      min-height: 100dvh;
      background: var(--cd-color-bg);
    }
    .panel-side {
      position: sticky;
      top: 0;
      align-self: start;
      display: flex;
      flex-direction: column;
      height: 100dvh;
      min-height: 100dvh;
      min-width: 0;
      padding: 27px 14px 18px;
      background: #121f21;
      color: #ddece5;
    }
    .panel-main {
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
      font-family: var(--cd-font-sans, 'DM Sans', sans-serif);
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
    .panel-side__nav {
      display: grid;
      gap: 3px;
    }
    .panel-side__nav a {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 14px;
      border-radius: 10px;
      color: #96b0a7;
      font-size: 12px;
      font-weight: 700;
      text-decoration: none;
      transition: background 0.15s ease, color 0.15s ease;
    }
    .panel-side__nav a:hover {
      background: #233b37;
      color: #fff;
    }
    .panel-side__nav a.activo {
      background: #284638;
      color: #ddffd4;
      box-shadow: inset 3px 0 #b4ed7c;
    }
    .panel-side__nav a cd-portal-icon {
      opacity: 0.85;
    }
    .panel-side__end {
      margin-top: auto;
      display: grid;
      gap: 6px;
    }
    .panel-side__help {
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
    .panel-side__help strong {
      font-size: 12px;
    }
    .panel-side__help p {
      margin: 5px 0 0;
      color: #809f90;
      font-size: 10px;
      line-height: 1.6;
    }
    .panel-side__site,
    .panel-side__salir {
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
    .panel-side__site:hover {
      background: #233b37;
      color: #fff;
    }
    .panel-side__salir:hover {
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
    .accion-sitio {
      color: var(--cd-color-teal);
      font-size: 11px;
      font-weight: 800;
      text-decoration: none;
    }
    .accion-sitio:hover {
      text-decoration: underline;
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
    .pendiente {
      display: grid;
      gap: var(--cd-space-3);
      justify-items: start;
    }
    @media (max-width: 900px) {
      .panel-shell {
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
      .panel-side {
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
      .panel-side.abierto {
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
      .panel-side__nav {
        display: grid;
        gap: 3px;
        width: auto;
        max-width: none;
        overflow: visible;
      }
      .panel-side__nav a {
        padding: 12px 14px;
      }
      .panel-side__end {
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
export class PanelLayoutComponent implements OnInit {
  protected readonly store = inject(OwnerStore);
  protected readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  ngOnInit(): void {
    void this.store.init();
  }

  protected async salir(): Promise<void> {
    await this.auth.logout();
    await this.router.navigate(['/']);
  }

  protected iniciales(): string {
    return this.auth
      .fullName()
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((parte) => parte.charAt(0).toUpperCase())
      .join('') || 'CD';
  }
}
