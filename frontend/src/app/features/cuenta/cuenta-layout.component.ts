import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthStore } from '../../core/auth/auth.store';
import { PortalIconComponent } from '../../shared/ui/portal-icon/portal-icon.component';

@Component({
  selector: 'app-cuenta-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, PortalIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cuenta-shell">
      <aside class="cuenta-side">
        <a routerLink="/" class="marca-lateral" aria-label="CanchaDirecta, ir al inicio">
          <span class="logo-symbol" aria-hidden="true"><span></span><span></span></span>
          <span class="logo-name">
            cancha<span>directa</span>
            <small>RESERVA · JUEGA · REPITE</small>
          </span>
        </a>

        <div class="workspace-label">ESPACIO DE TRABAJO <span>Cliente</span></div>
        <nav class="cuenta-side__nav" aria-label="Menú de mi cuenta">
          <a routerLink="/mi-resumen" routerLinkActive="activo"><cd-portal-icon name="grid" /> Resumen</a>
          <a routerLink="/mis-reservas" routerLinkActive="activo"><cd-portal-icon name="calendar" /> Mis reservas</a>
          <a routerLink="/mis-inscripciones" routerLinkActive="activo"><cd-portal-icon name="ticket" /> Mis inscripciones</a>
          <a routerLink="/favoritos" routerLinkActive="activo"><cd-portal-icon name="heart" /> Favoritos</a>
          <a routerLink="/notificaciones" routerLinkActive="activo"><cd-portal-icon name="bell" /> Notificaciones</a>
          <a routerLink="/ayuda" routerLinkActive="activo"><cd-portal-icon name="help" /> Centro de ayuda</a>
          <a routerLink="/reclamos" routerLinkActive="activo"><cd-portal-icon name="receipt" /> Reclamos</a>
          <a routerLink="/perfil" routerLinkActive="activo"><cd-portal-icon name="user" /> Mi perfil</a>
        </nav>

        <div class="cuenta-side__end">
          <div class="cuenta-side__help">
            <span class="help-signal" aria-hidden="true"><cd-portal-icon name="spark" [size]="16" /></span>
            <strong>Entorno de prueba</strong>
            <p>Datos reales del backend. Reservas y pagos simulados.</p>
          </div>
          <a routerLink="/" class="cuenta-side__volver"><cd-portal-icon name="logout" [size]="17" /> Volver al sitio</a>
          <button type="button" class="cuenta-side__salir" (click)="salir()">Cerrar sesión</button>
        </div>
      </aside>

      <div class="cuenta-main">
        <header class="portal-top">
          <div class="portal-top-left">
            <span class="breadcrumb">CanchaDirecta <span>/</span> Cliente</span>
            <strong>Mi cuenta</strong>
          </div>
          <div class="portal-actions">
            <a routerLink="/notificaciones" class="icono-mini" aria-label="Notificaciones">
              <cd-portal-icon name="bell" [size]="19" />
            </a>
            <span class="avatar" aria-hidden="true">{{ inicial() }}</span>
          </div>
        </header>

        <div class="portal-content">
          <router-outlet />
        </div>
      </div>
    </div>
  `,
  styles: `
    .cuenta-shell {
      display: grid;
      grid-template-columns: 252px minmax(0, 1fr);
      min-height: 100dvh;
      background: #f6f8f5;
    }
    .cuenta-side {
      position: sticky;
      top: 0;
      align-self: start;
      display: flex;
      flex-direction: column;
      gap: 0;
      height: 100dvh;
      min-height: 100dvh;
      padding: 27px 14px 18px;
      background: #121f21;
      color: #ddece5;
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
      background: #c5f56b;
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
    .cuenta-side__nav {
      display: grid;
      gap: 3px;
    }
    .cuenta-side__nav a {
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
    .cuenta-side__nav a:hover {
      background: #233b37;
      color: #fff;
    }
    .cuenta-side__nav a.activo {
      background: #284638;
      color: #ddffd4;
      box-shadow: inset 3px 0 #b4ed7c;
    }
    .cuenta-side__nav a cd-portal-icon {
      opacity: 0.85;
    }
    .cuenta-side__end {
      margin-top: auto;
      display: grid;
      gap: 6px;
    }
    .cuenta-side__help {
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
      background: #c5f56b;
      color: #214d37;
    }
    .cuenta-side__help strong {
      font-size: 12px;
    }
    .cuenta-side__help p {
      margin: 5px 0 0;
      color: #809f90;
      font-size: 10px;
      line-height: 1.6;
    }
    .cuenta-side__volver,
    .cuenta-side__salir {
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
    .cuenta-side__volver:hover {
      background: #233b37;
      color: #fff;
    }
    .cuenta-side__salir:hover {
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
      border-bottom: 1px solid #e5ebe5;
      background: #fff;
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
    .portal-top-left strong {
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
      color: #607c76;
      text-decoration: none;
    }
    .icono-mini:hover {
      background: #eef4ea;
      color: #173a2b;
    }
    .avatar {
      display: grid;
      place-items: center;
      width: 36px;
      height: 36px;
      border-radius: 10px;
      background: #d8f5b9;
      color: #235336;
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
      .cuenta-shell {
        grid-template-columns: 1fr;
      }
      .cuenta-side {
        position: static;
        min-height: 0;
        padding: 10px 14px;
        border-bottom: 1px solid #2b433a;
      }
      .marca-lateral,
      .workspace-label,
      .cuenta-side__end {
        display: none;
      }
      .cuenta-side__nav {
        display: flex;
        gap: 5px;
        overflow-x: auto;
      }
      .cuenta-side__nav a {
        flex: 0 0 auto;
        padding: 9px 12px;
      }
      .portal-top {
        height: 64px;
        padding: 0 18px;
      }
      .portal-content {
        padding-top: 20px;
      }
    }
  `,
})
export class CuentaLayoutComponent {
  protected readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  protected inicial(): string {
    return this.auth.fullName().charAt(0).toUpperCase() || '?';
  }

  protected async salir(): Promise<void> {
    await this.auth.logout();
    await this.router.navigate(['/']);
  }
}
