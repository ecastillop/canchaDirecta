import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthStore } from '../../core/auth/auth.store';

@Component({
  selector: 'app-cuenta-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cuenta-shell">
      <aside class="cuenta-side">
        <div class="cuenta-side__perfil">
          <span class="cuenta-side__avatar" aria-hidden="true">{{ inicial() }}</span>
          <div>
            <strong>{{ auth.fullName() || 'Mi cuenta' }}</strong>
            <small>{{ auth.user()?.correo }}</small>
          </div>
        </div>
        <nav class="cuenta-side__nav" aria-label="Menú de mi cuenta">
          <a routerLink="/mis-reservas" routerLinkActive="activo"><span aria-hidden="true">▤</span> Mis reservas</a>
          <a routerLink="/favoritos" routerLinkActive="activo"><span aria-hidden="true">♡</span> Favoritos</a>
          <a routerLink="/notificaciones" routerLinkActive="activo"><span aria-hidden="true">◔</span> Notificaciones</a>
          <a routerLink="/reclamos" routerLinkActive="activo"><span aria-hidden="true">◇</span> Reclamos</a>
          <a routerLink="/perfil" routerLinkActive="activo"><span aria-hidden="true">◎</span> Mi perfil</a>
        </nav>
        <button type="button" class="cuenta-side__salir" (click)="salir()">Cerrar sesión</button>
      </aside>
      <div class="cuenta-main">
        <router-outlet />
      </div>
    </div>
  `,
  styles: `
    .cuenta-shell {
      display: grid;
      grid-template-columns: 240px minmax(0, 1fr);
      min-height: calc(100dvh - var(--cd-topbar-height));
      background: var(--cd-color-bg);
    }
    .cuenta-side {
      position: sticky;
      top: var(--cd-topbar-height);
      align-self: start;
      display: flex;
      flex-direction: column;
      gap: 20px;
      min-height: calc(100dvh - var(--cd-topbar-height));
      padding: 24px 14px;
      border-right: 1px solid var(--cd-color-border);
      background: var(--cd-color-surface);
    }
    .cuenta-side__perfil {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 0 6px;
    }
    .cuenta-side__avatar {
      display: grid;
      place-items: center;
      width: 38px;
      height: 38px;
      border-radius: 50%;
      background: var(--cd-color-accent);
      color: var(--cd-color-primary);
      font-weight: 900;
    }
    .cuenta-side__perfil strong,
    .cuenta-side__perfil small {
      display: block;
    }
    .cuenta-side__perfil strong {
      font-family: var(--cd-font-display);
      font-size: 15px;
    }
    .cuenta-side__perfil small {
      color: var(--cd-color-text-muted);
      font-size: 10px;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 150px;
    }
    .cuenta-side__nav {
      display: grid;
      gap: 4px;
    }
    .cuenta-side__nav a {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 11px 12px;
      border-radius: 10px;
      color: var(--cd-color-text-muted);
      font-size: 13px;
      font-weight: 600;
      text-decoration: none;
    }
    .cuenta-side__nav a:hover {
      background: var(--cd-color-surface-alt);
      color: var(--cd-color-text);
    }
    .cuenta-side__nav a.activo {
      background: var(--cd-color-primary);
      color: var(--cd-color-on-primary);
    }
    .cuenta-side__salir {
      margin-top: auto;
      padding: 10px 12px;
      border: 0;
      border-radius: 10px;
      background: transparent;
      color: var(--cd-color-text-muted);
      font-size: 12px;
      text-align: left;
      cursor: pointer;
    }
    .cuenta-side__salir:hover {
      background: var(--cd-color-danger-soft);
      color: var(--cd-color-danger);
    }
    .cuenta-main {
      min-width: 0;
      padding: 28px clamp(18px, 3vw, 44px) 60px;
    }
    @media (max-width: 900px) {
      .cuenta-shell {
        grid-template-columns: 1fr;
      }
      .cuenta-side {
        position: static;
        min-height: 0;
        padding: 12px 14px;
        border-right: 0;
        border-bottom: 1px solid var(--cd-color-border);
      }
      .cuenta-side__perfil,
      .cuenta-side__salir {
        display: none;
      }
      .cuenta-side__nav {
        display: flex;
        gap: 6px;
        overflow-x: auto;
      }
      .cuenta-side__nav a {
        flex: 0 0 auto;
        padding: 9px 12px;
      }
      .cuenta-main {
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
