import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { RecepcionApi, ReservaRecepcion } from '../../core/api/recepcion.api';
import { RecepcionStore, hoyIso } from '../../core/api/recepcion.store';
import { toApiError } from '../../core/http/api-error';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { RecepcionReservaCardComponent } from './recepcion-reserva-card.component';

/** Agenda del dia: reservas de la sede en la fecha elegida. */
@Component({
  selector: 'app-recepcion-agenda',
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
        <span class="cd-field__label">Fecha</span>
        <input cdInput type="date" [value]="fecha()" (change)="cambiarFecha($event)" />
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
    } @else if (reservas().length === 0) {
      <cd-empty-state title="Sin reservas" message="No hay reservas para la fecha y sede seleccionadas." />
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
      display: flex;
      align-items: end;
      gap: var(--cd-space-3);
      flex-wrap: wrap;
      margin-bottom: var(--cd-space-4);
    }
  `,
})
export class RecepcionAgendaComponent implements OnInit {
  private readonly api = inject(RecepcionApi);
  private readonly toast = inject(ToastService);
  protected readonly store = inject(RecepcionStore);

  protected readonly fecha = signal(hoyIso());
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

  protected async cambiarFecha(evento: Event): Promise<void> {
    this.fecha.set((evento.target as HTMLInputElement).value);
    await this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.reservas.set(
        await firstValueFrom(this.api.agenda(this.store.seleccionado(), this.fecha())),
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
