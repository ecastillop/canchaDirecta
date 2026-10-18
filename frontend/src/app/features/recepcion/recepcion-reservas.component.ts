import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { RecepcionApi, ReservaRecepcion } from '../../core/api/recepcion.api';
import { RecepcionStore } from '../../core/api/recepcion.store';
import { toApiError } from '../../core/http/api-error';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { RecepcionReservaCardComponent } from './recepcion-reserva-card.component';

const ESTADOS = [
  { valor: '', etiqueta: 'Todas' },
  { valor: 'PENDIENTE', etiqueta: 'Pendientes' },
  { valor: 'CONFIRMADA', etiqueta: 'Confirmadas' },
  { valor: 'FINALIZADA', etiqueta: 'Finalizadas' },
  { valor: 'CANCELADA', etiqueta: 'Canceladas' },
];

/** Reservas de las sedes accesibles, con filtro por estado. */
@Component({
  selector: 'app-recepcion-reservas',
  imports: [
    CdInputDirective,
    CdSkeletonComponent,
    CdErrorStateComponent,
    CdEmptyStateComponent,
    RecepcionReservaCardComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="barra">
      <label class="cd-field">
        <span class="cd-field__label">Sede</span>
        <select cdInput [value]="store.seleccionado()" (change)="cambiarSede($event)">
          @for (sede of store.establecimientos(); track sede.id) {
            <option [value]="sede.id">{{ sede.nombre }}</option>
          }
        </select>
      </label>
    </div>

    <div class="filtros" role="group" aria-label="Filtrar reservas">
      @for (estado of estados; track estado.valor) {
        <button
          type="button"
          class="chip"
          [class.chip--activo]="filtro() === estado.valor"
          [attr.aria-pressed]="filtro() === estado.valor"
          (click)="cambiarFiltro(estado.valor)"
        >
          {{ estado.etiqueta }}
        </button>
      }
    </div>

    @if (cargando()) {
      <div class="cd-stack">
        @for (i of [1, 2, 3]; track i) {
          <cd-skeleton height="118px" radius="12px" />
        }
      </div>
    } @else if (error()) {
      <cd-error-state [message]="error()!" (retry)="cargar()" />
    } @else if (reservas().length === 0) {
      <cd-empty-state title="Sin reservas" message="No hay reservas con este estado en tus sedes." />
    } @else {
      <div class="cd-stack">
        @for (reserva of reservas(); track reserva.id) {
          <cd-recepcion-reserva [reserva]="reserva" (checkIn)="checkIn($event)" (cobrar)="cobrar($event)" />
        }
      </div>
    }
  `,
  styles: `
    .barra {
      margin-bottom: var(--cd-space-3);
    }
    .filtros {
      display: flex;
      gap: var(--cd-space-2);
      flex-wrap: wrap;
      margin-bottom: var(--cd-space-4);
    }
    .chip {
      padding: 8px 14px;
      border-radius: var(--cd-radius-pill);
      border: 1px solid var(--cd-color-border);
      background: var(--cd-color-surface);
      cursor: pointer;
      font-size: var(--cd-text-sm);
      font-weight: 600;
      color: var(--cd-color-text-muted);
    }
    .chip--activo {
      background: var(--cd-color-primary);
      border-color: var(--cd-color-primary);
      color: var(--cd-color-on-primary);
    }
  `,
})
export class RecepcionReservasComponent implements OnInit {
  private readonly api = inject(RecepcionApi);
  private readonly toast = inject(ToastService);
  protected readonly store = inject(RecepcionStore);

  protected readonly estados = ESTADOS;
  protected readonly filtro = signal('');
  protected readonly reservas = signal<ReservaRecepcion[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    await this.store.init();
    await this.cargar();
  }

  protected async cambiarSede(evento: Event): Promise<void> {
    const valor = (evento.target as HTMLSelectElement).value;
    await this.store.seleccionar(valor ? Number(valor) : null);
    await this.cargar();
  }

  protected async cambiarFiltro(estado: string): Promise<void> {
    this.filtro.set(estado);
    await this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.reservas.set(
        await firstValueFrom(this.api.reservas(this.store.seleccionado(), this.filtro())),
      );
    } catch (error) {
      this.error.set(toApiError(error).message);
    } finally {
      this.cargando.set(false);
    }
  }

  protected checkIn(reserva: ReservaRecepcion): void {
    this.api.checkIn(reserva.id).subscribe({
      next: () => {
        this.toast.success(`Check-in registrado para la reserva #${reserva.id}.`);
        void this.cargar();
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  protected cobrar(reserva: ReservaRecepcion): void {
    this.api.cobrar(reserva.id, 'EFECTIVO').subscribe({
      next: () => {
        this.toast.success(`Cobro registrado para la reserva #${reserva.id}.`);
        void this.cargar();
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }
}
