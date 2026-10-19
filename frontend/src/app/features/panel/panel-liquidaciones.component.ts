import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { OwnerApi } from '../../core/api/owner.api';
import { Liquidacion } from '../../core/api/owner.models';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { CdPaginatorComponent } from '../../shared/ui/pagination/cd-paginator.component';
import { ToastService } from '../../shared/ui/toast/toast.service';

@Component({
  selector: 'app-panel-liquidaciones',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    DatePipe,
    CdButtonDirective,
    CdInputDirective,
    CdEmptyStateComponent,
    CdPaginatorComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="cd-card">
      <h2 class="seccion">Generar liquidación</h2>
      <p class="cd-small cd-muted">
        Agrupa las reservas pagadas y confirmadas del periodo. Comisión y neto se calculan en el servidor.
      </p>
      @if (error()) {
        <p class="alerta" role="alert">{{ error() }}</p>
      }
      <form class="cd-stack" [formGroup]="form" (ngSubmit)="generar()" novalidate>
        <div class="dos">
          <label class="cd-field">
            <span class="cd-field__label">Desde</span>
            <input cdInput type="date" formControlName="periodoInicio" />
          </label>
          <label class="cd-field">
            <span class="cd-field__label">Hasta</span>
            <input cdInput type="date" formControlName="periodoFin" />
          </label>
        </div>
        <button cdButton type="submit" [loading]="generando()">Generar</button>
      </form>
    </section>

    <section class="cd-card listado">
      <h2 class="seccion">Historial</h2>
      @if (cargando()) {
        <p class="cd-small cd-muted" role="status">Cargando liquidaciones...</p>
      } @else if (liquidaciones().length === 0) {
        <cd-empty-state title="Sin liquidaciones" message="Genera la primera liquidacion de un periodo con reservas pagadas." />
      } @else {
        <ul class="lista">
          @for (liquidacion of liquidacionesPagina(); track liquidacion.id) {
            <li>
              <div>
                <strong>
                  {{ liquidacion.periodoInicio | date: 'd MMM' }} - {{ liquidacion.periodoFin | date: 'd MMM yyyy' }}
                </strong>
                <span class="cd-badge" [class]="liquidacion.estado === 'PAGADA' ? 'cd-badge--success' : 'cd-badge--info'">
                  {{ liquidacion.estado }}
                </span>
                <p class="cd-xs cd-muted">
                  Bruto {{ liquidacion.montoBruto | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }} · comisión
                  {{ liquidacion.montoComision | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }} · neto
                  <strong>{{ liquidacion.montoNeto | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</strong>
                </p>
              </div>
              <button cdButton variant="secondary" size="sm" type="button" (click)="verDetalle(liquidacion.id)">
                Ver detalle
              </button>
            </li>
          }
        </ul>
        <cd-paginator
          [pagina]="pagina()"
          [total]="liquidaciones().length"
          [tamano]="tamanoPagina"
          etiqueta="Paginación de liquidaciones"
          (paginaChange)="pagina.set($event)"
        />
      }

      @if (detalle(); as d) {
        <div class="detalle" aria-live="polite">
          <h3 class="subseccion">Detalle de liquidación #{{ d.id }}</h3>
          <table class="tabla">
            <caption class="cd-visually-hidden">Reservas incluidas en la liquidación</caption>
            <thead>
              <tr>
                <th scope="col">Reserva</th>
                <th scope="col">Monto</th>
                <th scope="col">Comisión</th>
                <th scope="col">Neto</th>
              </tr>
            </thead>
            <tbody>
              @for (fila of d.detalles; track fila.reservaId) {
                <tr>
                  <td>#{{ fila.reservaId }}</td>
                  <td>{{ fila.montoReserva | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</td>
                  <td>{{ fila.montoComision | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</td>
                  <td>{{ fila.montoNeto | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
  styles: `
    .seccion {
      font-size: var(--cd-text-base);
      margin-bottom: var(--cd-space-2);
    }
    .subseccion {
      font-size: var(--cd-text-sm);
      margin-bottom: var(--cd-space-2);
    }
    .listado {
      margin-top: var(--cd-space-4);
    }
    .dos {
      display: grid;
      gap: var(--cd-space-3);
      max-width: 480px;
    }
    .lista {
      list-style: none;
      padding: 0;
      display: grid;
      gap: var(--cd-space-2);
      margin-top: var(--cd-space-3);
    }
    .lista li {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--cd-space-3);
      padding: var(--cd-space-3);
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-sm);
      flex-wrap: wrap;
    }
    .lista .cd-badge {
      margin-left: var(--cd-space-2);
    }
    .detalle {
      margin-top: var(--cd-space-4);
    }
    .tabla {
      width: 100%;
      border-collapse: collapse;
      font-size: var(--cd-text-sm);
    }
    .tabla th,
    .tabla td {
      padding: var(--cd-space-2);
      border-bottom: 1px solid var(--cd-color-border);
      text-align: left;
    }
    .alerta {
      margin-bottom: var(--cd-space-3);
      padding: var(--cd-space-3);
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-danger-soft);
      color: var(--cd-color-danger);
      font-size: var(--cd-text-sm);
      font-weight: 600;
    }
    @media (min-width: 640px) {
      .dos {
        grid-template-columns: 1fr 1fr;
      }
    }
  `,
})
export class PanelLiquidacionesComponent implements OnInit {
  private readonly api = inject(OwnerApi);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);

  protected readonly liquidaciones = signal<Liquidacion[]>([]);
  protected readonly detalle = signal<Liquidacion | null>(null);
  protected readonly cargando = signal(true);
  protected readonly generando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly pagina = signal(1);
  protected readonly tamanoPagina = 10;
  protected readonly liquidacionesPagina = computed(() => {
    const desde = (this.pagina() - 1) * this.tamanoPagina;
    return this.liquidaciones().slice(desde, desde + this.tamanoPagina);
  });

  protected readonly form = this.fb.nonNullable.group({
    periodoInicio: ['', [Validators.required]],
    periodoFin: ['', [Validators.required]],
  });

  ngOnInit(): void {
    void this.cargar();
  }

  protected generar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { periodoInicio, periodoFin } = this.form.getRawValue();
    this.generando.set(true);
    this.error.set(null);
    this.api.generarLiquidacion(periodoInicio, periodoFin).subscribe({
      next: (liquidacion) => {
        this.generando.set(false);
        this.toast.success('Liquidacion generada.');
        this.liquidaciones.update((lista) => [liquidacion, ...lista]);
        this.detalle.set(liquidacion);
      },
      error: (error) => {
        this.error.set(toApiError(error).message);
        this.generando.set(false);
      },
    });
  }

  protected verDetalle(id: number): void {
    this.api.liquidacion(id).subscribe({
      next: (liquidacion) => this.detalle.set(liquidacion),
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    try {
      this.liquidaciones.set(await firstValueFrom(this.api.liquidaciones()));
    } catch (error) {
      this.error.set(toApiError(error).message);
    } finally {
      this.cargando.set(false);
    }
  }
}
