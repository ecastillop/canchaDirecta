import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-sin-acceso',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cd-container estado">
      <h1>No tienes acceso a esta sección</h1>
      <p class="cd-muted">
        Tu cuenta no tiene el rol necesario. Si crees que es un error, contacta al administrador de
        la plataforma.
      </p>
      <div class="acciones">
        <a routerLink="/" class="cd-btn cd-btn--primary">Ir al inicio</a>
        <a routerLink="/canchas" class="cd-btn cd-btn--secondary">Ver canchas</a>
      </div>
    </div>
  `,
  styles: `
    .estado {
      display: grid;
      gap: var(--cd-space-3);
      padding-block: var(--cd-space-8);
      max-width: 620px;
    }
    .acciones {
      display: flex;
      gap: var(--cd-space-2);
      flex-wrap: wrap;
    }
    .acciones .cd-btn {
      text-decoration: none;
    }
  `,
})
export class SinAccesoComponent {}
