import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { OwnerStore } from '../../core/api/owner.store';
import { AuthStore } from '../../core/auth/auth.store';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';

@Component({
  selector: 'app-panel-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CdSkeletonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="panel-shell">
      <aside class="panel-side">
        <div class="panel-side__brand">
          <span class="panel-side__logo" aria-hidden="true">A</span>
          <div>
            <strong>Propietario</strong>
            <small>canchadirecta</small>
          </div>
        </div>
        <nav class="panel-side__nav" aria-label="Menú del panel del propietario">
          <a routerLink="/panel" routerLinkActive="activo" [routerLinkActiveOptions]="{ exact: true }">
            <span aria-hidden="true">▦</span> Resumen
          </a>
          <a routerLink="/panel/reservas" routerLinkActive="activo"><span aria-hidden="true">▤</span> Reservas</a>
          <a routerLink="/panel/agenda" routerLinkActive="activo"><span aria-hidden="true">▥</span> Agenda</a>
          <a routerLink="/panel/promociones" routerLinkActive="activo"><span aria-hidden="true">◇</span> Promociones</a>
          <a routerLink="/panel/liquidaciones" routerLinkActive="activo"><span aria-hidden="true">◫</span> Liquidaciones</a>
        </nav>
        <a routerLink="/" class="panel-side__site">← Volver al sitio</a>
      </aside>

      <div class="panel-main">
        <header class="panel-head">
          <div>
            <p class="eyebrow">Panel del propietario</p>
            <h1>Gestiona tus espacios</h1>
            <p class="cd-small cd-muted">Canchas, agenda, promociones y reservas en un solo lugar.</p>
          </div>
          <a routerLink="/canchas" class="cd-btn cd-btn--secondary cd-btn--sm">Ver sitio →</a>
        </header>

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
  `,
  styles: `
    .panel-shell {
      display: grid;
      grid-template-columns: 250px minmax(0, 1fr);
      min-height: calc(100dvh - var(--cd-topbar-height));
      background: var(--cd-color-bg);
    }
    .panel-side {
      position: sticky;
      top: var(--cd-topbar-height);
      align-self: start;
      display: flex;
      flex-direction: column;
      gap: 22px;
      min-height: calc(100dvh - var(--cd-topbar-height));
      padding: 24px 16px;
      border-right: 1px solid var(--cd-color-border);
      background: var(--cd-color-surface);
    }
    .panel-side__brand {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .panel-side__logo {
      display: grid;
      place-items: center;
      width: 38px;
      height: 38px;
      border-radius: 11px;
      background: var(--cd-color-accent);
      color: var(--cd-color-primary);
      font-family: var(--cd-font-display);
      font-size: 19px;
      font-weight: 900;
      transform: rotate(-6deg);
    }
    .panel-side__brand strong,
    .panel-side__brand small {
      display: block;
    }
    .panel-side__brand strong {
      font-family: var(--cd-font-display);
      font-size: 17px;
      letter-spacing: -0.02em;
    }
    .panel-side__brand small {
      color: var(--cd-color-text-muted);
      font-size: 9px;
      font-weight: 700;
      letter-spacing: 0.12em;
      text-transform: uppercase;
    }
    .panel-side__nav {
      display: grid;
      gap: 4px;
    }
    .panel-side__nav a {
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
    .panel-side__nav a:hover {
      background: var(--cd-color-surface-alt);
      color: var(--cd-color-text);
    }
    .panel-side__nav a.activo {
      background: var(--cd-color-primary);
      color: var(--cd-color-on-primary);
    }
    .panel-side__site {
      margin-top: auto;
      padding: 11px 12px;
      color: var(--cd-color-text-muted);
      font-size: 12px;
      text-decoration: none;
    }
    .panel-main {
      min-width: 0;
      padding: 34px clamp(18px, 3vw, 44px) 60px;
    }
    .panel-head {
      display: flex;
      align-items: end;
      justify-content: space-between;
      gap: 18px;
      flex-wrap: wrap;
      margin-bottom: 24px;
    }
    .panel-head h1 {
      margin: 0;
      font-size: clamp(30px, 3.4vw, 44px);
    }
    .eyebrow {
      margin: 0 0 8px;
      color: var(--cd-color-teal, #167a70);
      font-size: 10px;
      font-weight: 800;
      letter-spacing: 0.18em;
      text-transform: uppercase;
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
      .panel-side {
        position: static;
        min-height: 0;
        border-right: 0;
        border-bottom: 1px solid var(--cd-color-border);
        padding: 12px 14px;
      }
      .panel-side__brand,
      .panel-side__site {
        display: none;
      }
      .panel-side__nav {
        display: flex;
        gap: 6px;
        overflow-x: auto;
      }
      .panel-side__nav a {
        flex: 0 0 auto;
        padding: 9px 12px;
      }
      .panel-main {
        padding-top: 22px;
      }
    }
  `,
})
export class PanelLayoutComponent implements OnInit {
  protected readonly store = inject(OwnerStore);
  protected readonly auth = inject(AuthStore);

  ngOnInit(): void {
    void this.store.init();
  }
}
