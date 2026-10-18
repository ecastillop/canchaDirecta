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
    <div class="cd-container cd-page">
      <header class="cd-page__header">
        <h1 class="cd-page__title">Panel del propietario</h1>
        <p class="cd-muted">Gestiona tus canchas, agenda, promociones y reservas recibidas.</p>
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
              Aún no tienes solicitud de propietario. Envia tu documento desde tu perfil.
            }
          </p>
          <a routerLink="/perfil" class="cd-btn cd-btn--primary">Ir a mi perfil</a>
        </div>
      } @else {
        <nav class="tabs" aria-label="Secciones del panel">
          <a routerLink="/panel" routerLinkActive="activo" [routerLinkActiveOptions]="{ exact: true }">Resumen</a>
          <a routerLink="/panel/reservas" routerLinkActive="activo">Reservas</a>
          <a routerLink="/panel/agenda" routerLinkActive="activo">Agenda</a>
          <a routerLink="/panel/promociones" routerLinkActive="activo">Promociones</a>
          <a routerLink="/panel/liquidaciones" routerLinkActive="activo">Liquidaciones</a>
        </nav>
        <router-outlet />
      }
    </div>
  `,
  styles: `
    .tabs {
      display: flex;
      flex-wrap: wrap;
      gap: var(--cd-space-1);
      border-bottom: 1px solid var(--cd-color-border);
      margin-bottom: var(--cd-space-4);
    }
    .tabs a {
      padding: 10px 14px;
      text-decoration: none;
      color: var(--cd-color-text-muted);
      font-weight: 600;
      font-size: var(--cd-text-sm);
      border-bottom: 3px solid transparent;
    }
    .tabs a.activo {
      color: var(--cd-color-primary);
      border-bottom-color: var(--cd-color-accent);
    }
    .tabs a:hover {
      color: var(--cd-color-text);
    }
    .pendiente {
      display: grid;
      gap: var(--cd-space-3);
      justify-items: start;
    }
    .pendiente h2 {
      font-size: var(--cd-text-lg);
    }
    .pendiente .cd-btn {
      text-decoration: none;
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
