import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Actividad } from '../../core/api/actividades.models';
import { AdminApi } from '../../core/api/admin.api';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { ConfirmService } from '../../shared/ui/confirm/confirm.service';
import { PortalIconComponent } from '../../shared/ui/portal-icon/portal-icon.component';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { ToastService } from '../../shared/ui/toast/toast.service';

@Component({
  selector: 'app-admin-actividades',
  imports: [
    CurrencyPipe,
    DatePipe,
    CdButtonDirective,
    PortalIconComponent,
    CdSkeletonComponent,
    CdEmptyStateComponent,
    CdErrorStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dashboard-intro">
      <div>
        <p class="eyebrow">ADMINISTRADOR · CANCHADIRECTA</p>
        <h2 class="intro-title">Actividades de la plataforma</h2>
        <p>Supervisa torneos, clases y partidos abiertos publicados por las sedes.</p>
      </div>
    </div>

    @if (cargando()) {
      <div class="cd-stack">
        <cd-skeleton height="130px" radius="16px" />
        <cd-skeleton height="260px" radius="16px" />
      </div>
    } @else if (error()) {
      <cd-error-state [message]="error()!" (retry)="cargar()" />
    } @else {
      <div class="stats-grid">
        <article class="stat-card">
          <div class="stat-top">
            <span>Actividades</span>
            <span class="stat-icon"><cd-portal-icon name="spark" [size]="19" /></span>
          </div>
          <strong>{{ actividades().length }}</strong>
          <small>{{ activas() }} activas</small>
        </article>
        <article class="stat-card">
          <div class="stat-top">
            <span>Inscripciones</span>
            <span class="stat-icon"><cd-portal-icon name="users" [size]="19" /></span>
          </div>
          <strong>{{ totalInscritos() }}</strong>
          <small>Cupos ocupados</small>
        </article>
        <article class="stat-card">
          <div class="stat-top">
            <span>Canceladas</span>
            <span class="stat-icon"><cd-portal-icon name="alert" [size]="19" /></span>
          </div>
          <strong>{{ actividades().length - activas() }}</strong>
          <small>Actividades dadas de baja</small>
        </article>
      </div>

      <div class="filtros" role="group" aria-label="Filtrar actividades por estado">
        @for (estado of estados; track estado) {
          <button
            type="button"
            class="chip"
            [class.chip--activo]="filtro() === estado"
            (click)="filtro.set(estado)"
          >
            {{ estado || 'Todas' }}
          </button>
        }
      </div>

      @if (actividadesFiltradas().length === 0) {
        <cd-empty-state title="Sin actividades" message="No hay actividades con ese filtro." />
      } @else {
        <section class="panel">
          <div class="tabla-scroll">
            <table class="tabla">
              <caption class="cd-visually-hidden">Actividades registradas</caption>
              <thead>
                <tr>
                  <th scope="col">Actividad</th>
                  <th scope="col">Establecimiento</th>
                  <th scope="col">Fecha</th>
                  <th scope="col">Cupos</th>
                  <th scope="col">Precio</th>
                  <th scope="col">Estado</th>
                  <th scope="col">Acción</th>
                </tr>
              </thead>
              <tbody>
                @for (actividad of actividadesFiltradas(); track actividad.id) {
                  <tr>
                    <td>
                      <strong>{{ actividad.nombre }}</strong>
                      <small class="cd-xs cd-muted">{{ actividad.distrito }}</small>
                    </td>
                    <td>{{ actividad.establecimiento }}</td>
                    <td>{{ actividad.inicio | date: 'd MMM yyyy, HH:mm' }}</td>
                    <td>{{ actividad.inscritos }} / {{ actividad.cupos }}</td>
                    <td>{{ actividad.precio | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</td>
                    <td><span class="cd-badge">{{ actividad.estado }}</span></td>
                    <td>
                      @if (actividad.estado === 'ACTIVA') {
                        <button
                          cdButton
                          variant="danger"
                          size="sm"
                          type="button"
                          (click)="cancelar(actividad)"
                        >
                          Cancelar
                        </button>
                      } @else {
                        <span class="cd-xs cd-muted">—</span>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </section>
      }
    }
  `,
  styles: `
    .dashboard-intro {
      margin-bottom: 24px;
    }
    .eyebrow {
      margin: 0 0 8px;
      color: var(--cd-color-teal);
      font-size: 9px;
      font-weight: 900;
      letter-spacing: 0.19em;
      text-transform: uppercase;
    }
    .intro-title {
      margin: 0 0 8px;
      font-size: clamp(27px, 2.8vw, 39px);
      letter-spacing: -0.045em;
    }
    .dashboard-intro p {
      margin: 0;
      color: var(--cd-color-text-muted);
      font-size: 12px;
    }
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 15px;
      margin-bottom: 18px;
    }
    .stat-card {
      min-height: 140px;
      padding: 19px;
      border: 1px solid var(--cd-color-border);
      border-radius: 14px;
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-1);
    }
    .stat-top {
      display: flex;
      align-items: start;
      justify-content: space-between;
      gap: 8px;
      color: var(--cd-color-text-muted);
      font-size: 11px;
      font-weight: 700;
    }
    .stat-icon {
      display: grid;
      place-items: center;
      width: 35px;
      height: 35px;
      border-radius: 10px;
      background: var(--cd-color-success-soft);
      color: var(--cd-color-success);
    }
    .stat-card > strong {
      display: block;
      margin: 13px 0 5px;
      font-family: var(--cd-font-display);
      font-size: clamp(22px, 2.1vw, 32px);
      font-weight: 900;
      letter-spacing: -0.04em;
    }
    .stat-card > small {
      color: var(--cd-color-text-muted);
      font-size: 11px;
    }
    .filtros {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      margin-bottom: 14px;
    }
    .chip {
      padding: 7px 12px;
      border: 1px solid var(--cd-color-border);
      border-radius: 999px;
      background: var(--cd-color-surface);
      color: var(--cd-color-text-muted);
      font: inherit;
      font-size: 11px;
      font-weight: 700;
      cursor: pointer;
    }
    .chip--activo {
      border-color: var(--cd-color-teal);
      background: var(--cd-color-success-soft);
      color: var(--cd-color-teal);
    }
    .panel {
      padding: 20px;
      border: 1px solid var(--cd-color-border);
      border-radius: 16px;
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-1);
    }
    .tabla-scroll {
      overflow-x: auto;
    }
    .tabla {
      width: 100%;
      border-collapse: collapse;
      font-size: 12px;
    }
    .tabla th {
      padding: 8px 10px;
      border-bottom: 1px solid var(--cd-color-border);
      color: var(--cd-color-text-muted);
      text-align: left;
      font-size: 10px;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }
    .tabla td {
      padding: 10px;
      border-bottom: 1px solid var(--cd-color-border);
      vertical-align: top;
    }
    .tabla small {
      display: block;
    }
    @media (max-width: 900px) {
      .stats-grid {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class AdminActividadesComponent implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  protected readonly estados = ['', 'ACTIVA', 'CANCELADA'];
  protected readonly actividades = signal<Actividad[]>([]);
  protected readonly filtro = signal('');
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly activas = computed(
    () => this.actividades().filter((actividad) => actividad.estado === 'ACTIVA').length,
  );
  protected readonly totalInscritos = computed(() =>
    this.actividades().reduce((total, actividad) => total + actividad.inscritos, 0),
  );
  protected readonly actividadesFiltradas = computed(() =>
    this.filtro()
      ? this.actividades().filter((actividad) => actividad.estado === this.filtro())
      : this.actividades(),
  );

  ngOnInit(): void {
    this.cargar();
  }

  protected async cancelar(actividad: Actividad): Promise<void> {
    const ok = await this.confirm.confirm({
      title: 'Cancelar actividad',
      message: `Se cancelará "${actividad.nombre}" y sus ${actividad.inscritos} inscripciones.`,
      confirmText: 'Cancelar actividad',
      danger: true,
    });
    if (!ok) {
      return;
    }
    try {
      await firstValueFrom(this.api.cancelarActividad(actividad.id));
      this.toast.success('Actividad cancelada.');
      this.cargar();
    } catch (error) {
      this.toast.error(toApiError(error).message);
    }
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    void firstValueFrom(this.api.actividades())
      .then((actividades) => this.actividades.set(actividades))
      .catch((error) => this.error.set(toApiError(error).message))
      .finally(() => this.cargando.set(false));
  }
}
