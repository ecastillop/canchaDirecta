import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { RecepcionApi, ReservaRecepcion, ResumenTurno } from '../../core/api/recepcion.api';
import { RecepcionStore, hoyIso } from '../../core/api/recepcion.store';
import { toApiError } from '../../core/http/api-error';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { RecepcionReservaCardComponent } from './recepcion-reserva-card.component';

/** Turno actual: KPIs del dia y proximas reservas. */
@Component({
  selector: 'app-recepcion-resumen',
  imports: [
    DatePipe,
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
      <p class="cd-small cd-muted">{{ fecha | date: 'EEEE d MMMM yyyy' }}</p>
    </div>

    @if (cargando()) {
      <div class="kpis">
        @for (i of [1, 2, 3, 4]; track i) {
          <cd-skeleton height="104px" radius="14px" />
        }
      </div>
    } @else if (error()) {
      <cd-error-state [message]="error()!" (retry)="cargar()" />
    } @else {
      <div class="kpis">
        <article class="cd-card kpi">
          <span class="cd-small cd-muted">Reservas del día</span>
          <strong>{{ resumen()?.reservasDelDia ?? 0 }}</strong>
        </article>
        <article class="cd-card kpi">
          <span class="cd-small cd-muted">Check-ins</span>
          <strong>{{ resumen()?.checkIns ?? 0 }}</strong>
        </article>
        <article class="cd-card kpi">
          <span class="cd-small cd-muted">Cobros pendientes</span>
          <strong>{{ resumen()?.cobrosPendientes ?? 0 }}</strong>
        </article>
        <article class="cd-card kpi">
          <span class="cd-small cd-muted">Canchas activas</span>
          <strong>{{ resumen()?.canchasDisponibles ?? 0 }}</strong>
        </article>
      </div>

      <h2 class="seccion">Próximas reservas de hoy</h2>
      @if (agenda().length === 0) {
        <cd-empty-state title="Sin reservas hoy" message="Cuando lleguen reservas aparecerán aquí." />
      } @else {
        <div class="cd-stack">
          @for (reserva of agenda(); track reserva.id) {
            <cd-recepcion-reserva [reserva]="reserva" (checkIn)="checkIn($event)" (cobrar)="cobrar($event)" />
          }
        </div>
      }
    }
  `,
  styles: `
    .barra {
      display: flex;
      align-items: end;
      justify-content: space-between;
      gap: var(--cd-space-3);
      flex-wrap: wrap;
      margin-bottom: var(--cd-space-4);
    }
    .kpis {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: var(--cd-space-3);
      margin-bottom: var(--cd-space-5);
    }
    .kpi {
      display: grid;
      gap: 6px;
    }
    .kpi strong {
      font-family: var(--cd-font-display);
      font-size: 30px;
    }
    .seccion {
      margin: 0 0 var(--cd-space-3);
      font-size: var(--cd-text-base);
    }
  `,
})
export class RecepcionResumenComponent implements OnInit {
  private readonly api = inject(RecepcionApi);
  private readonly toast = inject(ToastService);
  protected readonly store = inject(RecepcionStore);

  protected readonly fecha = new Date();
  protected readonly resumen = signal<ResumenTurno | null>(null);
  protected readonly agenda = signal<ReservaRecepcion[]>([]);
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

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      const fecha = hoyIso();
      const [resumen, agenda] = await Promise.all([
        firstValueFrom(this.api.resumen(this.store.seleccionado(), fecha)),
        firstValueFrom(this.api.agenda(this.store.seleccionado(), fecha)),
      ]);
      this.resumen.set(resumen);
      this.agenda.set(agenda);
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
