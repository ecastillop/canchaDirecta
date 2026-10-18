import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ActividadesApi } from '../../core/api/actividades.api';
import { Inscripcion } from '../../core/api/actividades.models';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { ToastService } from '../../shared/ui/toast/toast.service';

@Component({
  selector: 'app-mis-inscripciones',
  imports: [RouterLink, DatePipe, CdButtonDirective, CdEmptyStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="cabecera">
      <div>
        <p class="eyebrow">Mi cuenta</p>
        <h1>Mis inscripciones</h1>
        <p class="cd-small cd-muted">Actividades en las que te inscribiste y su estado actual.</p>
      </div>
      <a routerLink="/actividades" cdButton variant="secondary" size="sm">Ver actividades</a>
    </header>

    @if (cargando()) {
      <p class="cd-small cd-muted" role="status">Cargando inscripciones...</p>
    } @else if (error()) {
      <p class="alerta" role="alert">{{ error() }}</p>
    } @else if (inscripciones().length === 0) {
      <cd-empty-state
        title="Aún no tienes inscripciones"
        message="Explora los torneos, clases y partidos abiertos publicados por los espacios deportivos."
      >
        <a routerLink="/actividades" cdButton variant="secondary" size="sm">Ver actividades</a>
      </cd-empty-state>
    } @else {
      <ul class="lista">
        @for (inscripcion of inscripciones(); track inscripcion.id) {
          <li class="inscripcion" [class.inscripcion--cancelada]="inscripcion.estado === 'CANCELADA'">
            <div class="inscripcion__fecha" aria-hidden="true">
              <strong>{{ inscripcion.inicio | date: 'd' }}</strong>
              <span>{{ inscripcion.inicio | date: 'MMM' }}</span>
            </div>
            <div class="inscripcion__datos">
              <div class="cd-row">
                <strong>{{ inscripcion.actividad }}</strong>
                <span
                  class="cd-badge"
                  [class.cd-badge--success]="inscripcion.estado === 'CONFIRMADA'"
                  [class.cd-badge--danger]="inscripcion.estado === 'CANCELADA'"
                >
                  {{ inscripcion.estado }}
                </span>
              </div>
              <p class="cd-xs cd-muted">{{ inscripcion.establecimiento }}</p>
              <p class="cd-xs cd-muted">
                {{ inscripcion.inicio | date: 'EEE d MMM, HH:mm' }} -
                {{ inscripcion.fin | date: 'HH:mm' }}
              </p>
              <p class="cd-xs cd-muted">Inscrito el {{ inscripcion.fechaRegistro | date: 'd MMM yyyy, HH:mm' }}</p>
            </div>
            @if (inscripcion.estado === 'CONFIRMADA') {
              <button
                cdButton
                variant="secondary"
                size="sm"
                type="button"
                [loading]="cancelando() === inscripcion.idActividad"
                (click)="cancelar(inscripcion)"
              >
                Cancelar
              </button>
            }
          </li>
        }
      </ul>
    }
  `,
  styles: `
    .cabecera {
      display: flex;
      align-items: end;
      justify-content: space-between;
      gap: 18px;
      flex-wrap: wrap;
      margin-bottom: 22px;
    }
    .eyebrow {
      margin: 0 0 8px;
      color: var(--cd-color-teal, #167a70);
      font-size: 10px;
      font-weight: 800;
      letter-spacing: 0.18em;
      text-transform: uppercase;
    }
    .cabecera h1 {
      margin: 0;
      font-size: clamp(30px, 3.4vw, 44px);
    }
    .lista {
      display: grid;
      gap: 12px;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .inscripcion {
      display: grid;
      grid-template-columns: 64px minmax(0, 1fr) auto;
      align-items: center;
      gap: 16px;
      padding: 16px;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-lg);
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-1);
    }
    .inscripcion--cancelada {
      opacity: 0.7;
    }
    .inscripcion__fecha {
      display: grid;
      justify-items: center;
      gap: 2px;
      padding: 10px 6px;
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-accent);
      color: var(--cd-color-primary);
    }
    .inscripcion__fecha strong {
      font-family: var(--cd-font-display);
      font-size: 24px;
      line-height: 1;
    }
    .inscripcion__fecha span {
      font-size: 10px;
      font-weight: 800;
      letter-spacing: 0.1em;
      text-transform: uppercase;
    }
    .inscripcion__datos {
      display: grid;
      gap: 4px;
    }
    .inscripcion__datos p {
      margin: 0;
    }
    .alerta {
      padding: var(--cd-space-3);
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-danger-soft);
      color: var(--cd-color-danger);
      font-size: var(--cd-text-sm);
      font-weight: 600;
    }
    @media (max-width: 640px) {
      .inscripcion {
        grid-template-columns: 56px minmax(0, 1fr);
      }
      .inscripcion > button {
        grid-column: 2;
        justify-self: start;
      }
    }
  `,
})
export class MisInscripcionesComponent implements OnInit {
  private readonly api = inject(ActividadesApi);
  private readonly toast = inject(ToastService);

  protected readonly inscripciones = signal<Inscripcion[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly cancelando = signal<number | null>(null);

  ngOnInit(): void {
    void this.cargar();
  }

  protected async cancelar(inscripcion: Inscripcion): Promise<void> {
    this.cancelando.set(inscripcion.idActividad);
    try {
      await firstValueFrom(this.api.cancelarInscripcion(inscripcion.idActividad));
      this.toast.success('Inscripción cancelada.');
      await this.cargar();
    } catch (error) {
      this.toast.error(toApiError(error).message);
    } finally {
      this.cancelando.set(null);
    }
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.inscripciones.set(await firstValueFrom(this.api.misInscripciones()));
    } catch (error) {
      this.error.set(toApiError(error).message);
    } finally {
      this.cargando.set(false);
    }
  }
}
