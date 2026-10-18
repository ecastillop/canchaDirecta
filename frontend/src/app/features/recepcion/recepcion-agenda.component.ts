import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { RecepcionApi, ReservaRecepcion } from '../../core/api/recepcion.api';
import { RecepcionStore, hoyIso } from '../../core/api/recepcion.store';
import { BloqueoCancha } from '../../core/api/owner.models';
import { OwnerApi } from '../../core/api/owner.api';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { ConfirmService } from '../../shared/ui/confirm/confirm.service';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { DESFASE_LIMA, horaLima } from '../../shared/util/fecha-lima';
import { RecepcionReservaCardComponent } from './recepcion-reserva-card.component';

/**
 * Agenda del dia: reservas de la sede en la fecha elegida y bloqueos operativos (RN-03).
 *
 * <p>El personal de recepcion puede bloquear franjas de las canchas de su sede (mantenimiento,
 * cierre puntual) sin depender del propietario: la autorizacion del backend acepta al personal
 * asignado a la sede (ADR-0042). La pantalla reutiliza los endpoints de la agenda de la cancha.</p>
 */
@Component({
  selector: 'app-recepcion-agenda',
  imports: [
    ReactiveFormsModule,
    CdButtonDirective,
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

    <h2 class="seccion">Reservas de la fecha</h2>
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

    <section class="cd-card bloqueos">
      <h2 class="seccion">Bloqueos de la fecha</h2>
      <p class="cd-xs cd-muted">
        Mantenimiento o cierre puntual de una franja: deja de ofrecerse en la ficha de la cancha al
        instante.
      </p>
      @if (bloqueos().length === 0) {
        <p class="cd-small cd-muted">Sin bloqueos para esta fecha.</p>
      } @else {
        <ul class="lista">
          @for (bloqueo of bloqueos(); track bloqueo.id) {
            <li>
              <div>
                <strong>{{ nombreCancha(bloqueo.idCancha) }}</strong>
                <p class="cd-xs cd-muted">
                  {{ hora(bloqueo.inicio) }} - {{ hora(bloqueo.fin) }} · {{ bloqueo.motivo }}
                </p>
              </div>
              <button cdButton variant="ghost" size="sm" type="button" (click)="levantar(bloqueo)">
                Levantar
              </button>
            </li>
          }
        </ul>
      }

      @if (errorBloqueo()) {
        <p class="alerta" role="alert">{{ errorBloqueo() }}</p>
      }

      @if (store.canchas().length === 0) {
        <p class="cd-small cd-muted">Esta sede no tiene canchas activas.</p>
      } @else {
        <form class="cd-stack formulario" [formGroup]="bloqueoForm" (ngSubmit)="bloquear()" novalidate>
          <h3 class="subseccion">Bloquear una franja del {{ fechaLegible() }}</h3>
          <div class="cuatro">
            <label class="cd-field">
              <span class="cd-field__label">Cancha</span>
              <select cdInput formControlName="idCancha">
                @for (cancha of store.canchas(); track cancha.id) {
                  <option [ngValue]="cancha.id">{{ cancha.nombre }}</option>
                }
              </select>
            </label>
            <label class="cd-field">
              <span class="cd-field__label">Desde</span>
              <input cdInput type="time" formControlName="desde" />
            </label>
            <label class="cd-field">
              <span class="cd-field__label">Hasta</span>
              <input cdInput type="time" formControlName="hasta" />
            </label>
            <label class="cd-field">
              <span class="cd-field__label">Motivo</span>
              <input cdInput formControlName="motivo" maxlength="200" placeholder="Mantenimiento" />
            </label>
          </div>
          <button cdButton type="submit" [loading]="guardandoBloqueo()">Bloquear franja</button>
        </form>
      }
    </section>
  `,
  styles: `
    .barra {
      display: flex;
      align-items: end;
      gap: var(--cd-space-3);
      flex-wrap: wrap;
      margin-bottom: var(--cd-space-4);
    }
    .seccion {
      font-size: var(--cd-text-base);
      margin-bottom: var(--cd-space-2);
    }
    .subseccion {
      font-size: var(--cd-text-sm);
      margin-bottom: var(--cd-space-1);
    }
    .bloqueos {
      margin-top: var(--cd-space-4);
    }
    .lista {
      list-style: none;
      padding: 0;
      display: grid;
      gap: var(--cd-space-1);
    }
    .lista li {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--cd-space-2);
      padding: var(--cd-space-2);
      border-bottom: 1px solid var(--cd-color-border);
      font-size: var(--cd-text-sm);
      flex-wrap: wrap;
    }
    .formulario {
      margin-top: var(--cd-space-4);
    }
    .formulario button[type='submit'] {
      justify-self: start;
    }
    .cuatro {
      display: grid;
      gap: var(--cd-space-3);
      grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
    }
    .alerta {
      margin-block: var(--cd-space-2);
      padding: var(--cd-space-3);
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-danger-soft);
      color: var(--cd-color-danger);
      font-size: var(--cd-text-sm);
    }
  `,
})
export class RecepcionAgendaComponent implements OnInit {
  private readonly api = inject(RecepcionApi);
  // Los bloqueos son endpoints de la agenda de la cancha; el backend autoriza al personal de
  // la sede asignada, no solo al propietario (ADR-0042).
  private readonly agendaCancha = inject(OwnerApi);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly formBuilder = inject(FormBuilder);
  protected readonly store = inject(RecepcionStore);

  protected readonly fecha = signal(hoyIso());
  protected readonly reservas = signal<ReservaRecepcion[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly bloqueos = signal<BloqueoCancha[]>([]);
  protected readonly guardandoBloqueo = signal(false);
  protected readonly errorBloqueo = signal<string | null>(null);

  protected readonly bloqueoForm = this.formBuilder.nonNullable.group({
    idCancha: 0,
    desde: ['08:00', Validators.required],
    hasta: ['09:00', Validators.required],
    motivo: ['', [Validators.required, Validators.maxLength(200)]],
  });

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
    await this.cargarBloqueos();
  }

  protected hora(instante: string): string {
    return horaLima(instante);
  }

  /** Fecha elegida en formato local (`dd/MM/yyyy`) para los textos de la seccion. */
  protected fechaLegible(): string {
    const [anio, mes, dia] = this.fecha().split('-');
    return anio && mes && dia ? `${dia}/${mes}/${anio}` : this.fecha();
  }

  protected nombreCancha(idCancha: number): string {
    const cancha = this.store.canchas().find((c) => c.id === idCancha);
    return cancha?.nombre ?? `Cancha ${idCancha}`;
  }

  protected bloquear(): void {
    const idCancha = this.bloqueoForm.controls.idCancha.value;
    if (!idCancha || this.bloqueoForm.invalid) {
      this.bloqueoForm.markAllAsTouched();
      return;
    }
    const value = this.bloqueoForm.getRawValue();
    const inicio = new Date(`${this.fecha()}T${value.desde}:00${DESFASE_LIMA}`);
    const fin = new Date(`${this.fecha()}T${value.hasta}:00${DESFASE_LIMA}`);
    if (fin <= inicio) {
      this.errorBloqueo.set('La hora de fin debe ser posterior a la de inicio.');
      return;
    }
    this.guardandoBloqueo.set(true);
    this.errorBloqueo.set(null);
    this.agendaCancha
      .crearBloqueo(idCancha, {
        inicio: inicio.toISOString(),
        fin: fin.toISOString(),
        motivo: value.motivo.trim(),
      })
      .subscribe({
        next: () => {
          this.guardandoBloqueo.set(false);
          this.toast.success('Franja bloqueada.');
          void this.cargarBloqueos();
        },
        error: (error) => {
          this.guardandoBloqueo.set(false);
          this.errorBloqueo.set(toApiError(error).message);
        },
      });
  }

  protected async levantar(bloqueo: BloqueoCancha): Promise<void> {
    const confirmado = await this.confirm.confirm({
      title: 'Levantar bloqueo',
      message: `La franja de "${bloqueo.motivo}" volvera a ofrecerse en la ficha de la cancha.`,
      confirmText: 'Levantar',
      danger: true,
    });
    if (!confirmado) {
      return;
    }
    this.agendaCancha.eliminarBloqueo(bloqueo.idCancha, bloqueo.id).subscribe({
      next: () => {
        this.toast.success('Bloqueo levantado.');
        void this.cargarBloqueos();
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
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

  /** Bloqueos de todas las canchas de la sede que tocan la fecha elegida. */
  private async cargarBloqueos(): Promise<void> {
    this.errorBloqueo.set(null);
    this.sincronizarCancha();
    const canchas = this.store.canchas();
    if (canchas.length === 0) {
      this.bloqueos.set([]);
      return;
    }
    const desde = new Date(`${this.fecha()}T00:00:00${DESFASE_LIMA}`);
    const hasta = new Date(desde.getTime() + 24 * 60 * 60 * 1000);
    try {
      const listas = await Promise.all(
        canchas.map((cancha) =>
          firstValueFrom(this.agendaCancha.bloqueos(cancha.id, desde.toISOString(), hasta.toISOString())),
        ),
      );
      this.bloqueos.set(
        listas.flat().sort((a, b) => a.inicio.localeCompare(b.inicio)),
      );
    } catch (error) {
      this.bloqueos.set([]);
      this.errorBloqueo.set(toApiError(error).message);
    }
  }

  /** Mantiene el selector de cancha alineado con las canchas de la sede elegida. */
  private sincronizarCancha(): void {
    const canchas = this.store.canchas();
    const actual = this.bloqueoForm.controls.idCancha.value;
    if (canchas.length === 0) {
      this.bloqueoForm.controls.idCancha.setValue(0);
      return;
    }
    if (!canchas.some((cancha) => cancha.id === actual)) {
      this.bloqueoForm.controls.idCancha.setValue(canchas[0].id);
    }
  }
}
