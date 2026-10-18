import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
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

const MEDIOS = ['EFECTIVO', 'YAPE', 'PLIN', 'TARJETA', 'POS'];

/** Cobros: reservas vigentes sin pago aprobado y su registro de pago. */
@Component({
  selector: 'app-recepcion-cobros',
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
      <label class="cd-field">
        <span class="cd-field__label">Medio de pago por defecto</span>
        <select cdInput [value]="medio()" (change)="cambiarMedio($event)">
          @for (opcion of medios; track opcion) {
            <option [value]="opcion">{{ opcion }}</option>
          }
        </select>
      </label>
    </div>

    @if (cargando()) {
      <div class="cd-stack">
        @for (i of [1, 2, 3]; track i) {
          <cd-skeleton height="118px" radius="12px" />
        }
      </div>
    } @else if (error()) {
      <cd-error-state [message]="error()!" (retry)="cargar()" />
    } @else if (pendientes().length === 0) {
      <cd-empty-state title="Sin cobros pendientes" message="Todas las reservas vigentes están pagadas." />
    } @else {
      <div class="cd-stack">
        @for (reserva of pendientes(); track reserva.id) {
          <cd-recepcion-reserva [reserva]="reserva" (cobrar)="cobrar($event)" />
        }
      </div>
    }
  `,
  styles: `
    .barra {
      display: flex;
      align-items: end;
      gap: var(--cd-space-3);
      flex-wrap: wrap;
      margin-bottom: var(--cd-space-4);
    }
  `,
})
export class RecepcionCobrosComponent implements OnInit {
  private readonly api = inject(RecepcionApi);
  private readonly toast = inject(ToastService);
  protected readonly store = inject(RecepcionStore);

  protected readonly medios = MEDIOS;
  protected readonly medio = signal('EFECTIVO');
  protected readonly reservas = signal<ReservaRecepcion[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly pendientes = computed(() =>
    this.reservas().filter(
      (reserva) =>
        reserva.pagoEstado !== 'APROBADO' &&
        (reserva.estado === 'PENDIENTE' || reserva.estado === 'CONFIRMADA'),
    ),
  );

  async ngOnInit(): Promise<void> {
    await this.store.init();
    await this.cargar();
  }

  protected async cambiarSede(evento: Event): Promise<void> {
    const valor = (evento.target as HTMLSelectElement).value;
    await this.store.seleccionar(valor ? Number(valor) : null);
    await this.cargar();
  }

  protected cambiarMedio(evento: Event): void {
    this.medio.set((evento.target as HTMLSelectElement).value);
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.reservas.set(await firstValueFrom(this.api.reservas(this.store.seleccionado(), '')));
    } catch (error) {
      this.error.set(toApiError(error).message);
    } finally {
      this.cargando.set(false);
    }
  }

  protected cobrar(reserva: ReservaRecepcion): void {
    this.api.cobrar(reserva.id, this.medio()).subscribe({
      next: () => {
        this.toast.success(`Cobro registrado para la reserva #${reserva.id}.`);
        void this.cargar();
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }
}
