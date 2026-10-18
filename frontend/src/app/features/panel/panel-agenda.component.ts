import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { OwnerApi } from '../../core/api/owner.api';
import { OwnerStore } from '../../core/api/owner.store';
import { BloqueoCancha } from '../../core/api/owner.models';
import { HorarioCancha } from '../../core/api/venues.models';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { ConfirmService } from '../../shared/ui/confirm/confirm.service';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { ToastService } from '../../shared/ui/toast/toast.service';

const DIAS = ['', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];

@Component({
  selector: 'app-panel-agenda',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    CdButtonDirective,
    CdInputDirective,
    CdEmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (store.canchas().length === 0) {
      <cd-empty-state title="Sin canchas" message="Crea una cancha en el resumen para gestionar su agenda." />
    } @else {
      <div class="selector">
        <label class="cd-field">
          <span class="cd-field__label">Cancha</span>
          <select cdInput [value]="canchaId()" (change)="cambiarCancha($event)">
            @for (cancha of store.canchas(); track cancha.id) {
              <option [value]="cancha.id">{{ cancha.nombre }}</option>
            }
          </select>
        </label>
      </div>

      @if (canchaId()) {
        <div class="columnas">
          <section class="cd-card">
            <h2 class="seccion">Horario semanal recurrente</h2>
            <p class="cd-xs cd-muted">
              Se repite todas las semanas. Por ejemplo: lunes a viernes de 18:00 a 23:00.
            </p>
            @if (horarios().length === 0) {
              <p class="cd-small cd-muted">Define al menos un horario para abrir franjas reservables.</p>
            } @else {
              <ul class="lista">
                @for (horario of horarios(); track horario.id) {
                  <li>
                    <span>{{ dia(horario.diaSemana) }} · {{ horario.horaInicio.slice(0, 5) }}-{{ horario.horaFin.slice(0, 5) }}</span>
                    <span class="cd-small cd-muted">
                      {{ horario.precio ?? 'Precio base' }}
                    </span>
                    <button cdButton variant="ghost" size="sm" type="button" (click)="eliminarHorario(horario)">
                      Quitar
                    </button>
                  </li>
                }
              </ul>
            }

            @if (errorHorario()) {
              <p class="alerta" role="alert">{{ errorHorario() }}</p>
            }
            <form class="cd-stack formulario" [formGroup]="horarioForm" (ngSubmit)="crearHorario()" novalidate>
              <h3 class="subseccion">Agregar horario</h3>
              <div class="cuatro">
                <label class="cd-field">
                  <span class="cd-field__label">Día</span>
                  <select cdInput formControlName="diaSemana">
                    @for (nombre of dias; track $index) {
                      @if ($index > 0) {
                        <option [ngValue]="$index">{{ nombre }}</option>
                      }
                    }
                  </select>
                </label>
                <label class="cd-field">
                  <span class="cd-field__label">Inicio</span>
                  <input cdInput type="time" formControlName="horaInicio" />
                </label>
                <label class="cd-field">
                  <span class="cd-field__label">Fin</span>
                  <input cdInput type="time" formControlName="horaFin" />
                </label>
                <label class="cd-field">
                  <span class="cd-field__label">Precio <span class="optional">(opcional)</span></span>
                  <input cdInput type="number" min="0" step="1" formControlName="precio" />
                </label>
              </div>
              <button cdButton type="submit" [loading]="guardando()">Agregar horario</button>
            </form>
          </section>

          <section class="cd-card">
            <h2 class="seccion">Bloqueos y excepciones</h2>
            <p class="cd-xs cd-muted">
              Solo afectan fechas concretas: mantenimiento, feriados o cierres especiales. No cambian
              el horario semanal recurrente.
            </p>
            @if (bloqueos().length === 0) {
              <p class="cd-small cd-muted">Sin bloqueos. Agrega mantenimientos o cierres puntuales.</p>
            } @else {
              <ul class="lista">
                @for (bloqueo of bloqueos(); track bloqueo.id) {
                  <li class="bloqueo">
                    <div>
                      <strong>{{ bloqueo.motivo }}</strong>
                      <p class="cd-xs cd-muted">
                        {{ bloqueo.inicio | date: 'd MMM, HH:mm' }} - {{ bloqueo.fin | date: 'd MMM, HH:mm' }}
                      </p>
                    </div>
                    <button cdButton variant="ghost" size="sm" type="button" (click)="eliminarBloqueo(bloqueo)">
                      Quitar
                    </button>
                  </li>
                }
              </ul>
            }

            @if (errorBloqueo()) {
              <p class="alerta" role="alert">{{ errorBloqueo() }}</p>
            }
            <form class="cd-stack formulario" [formGroup]="bloqueoForm" (ngSubmit)="crearBloqueo()" novalidate>
              <h3 class="subseccion">Agregar bloqueo</h3>
              <label class="cd-field">
                <span class="cd-field__label">Motivo</span>
                <input cdInput formControlName="motivo" maxlength="200" />
              </label>
              <label class="cd-field">
                <span class="cd-field__label">Inicio (hora de Lima)</span>
                <input cdInput type="datetime-local" formControlName="inicio" />
              </label>
              <label class="cd-field">
                <span class="cd-field__label">Fin (hora de Lima)</span>
                <input cdInput type="datetime-local" formControlName="fin" />
              </label>
              <button cdButton type="submit" [loading]="guardando()">Agregar bloqueo</button>
            </form>
          </section>
        </div>
      }
    }
  `,
  styles: `
    .selector {
      max-width: 360px;
      margin-bottom: var(--cd-space-4);
    }
    .columnas {
      display: grid;
      gap: var(--cd-space-4);
    }
    .seccion {
      font-size: var(--cd-text-base);
      margin-bottom: var(--cd-space-2);
    }
    .subseccion {
      font-size: var(--cd-text-sm);
      margin-bottom: var(--cd-space-1);
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
    .bloqueo {
      align-items: start;
    }
    .formulario {
      margin-top: var(--cd-space-4);
      padding-top: var(--cd-space-4);
      border-top: 1px solid var(--cd-color-border);
    }
    .cuatro {
      display: grid;
      gap: var(--cd-space-3);
    }
    .alerta {
      margin-block: var(--cd-space-2);
      padding: var(--cd-space-3);
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-danger-soft);
      color: var(--cd-color-danger);
      font-size: var(--cd-text-sm);
      font-weight: 600;
    }
    @media (min-width: 640px) {
      .cuatro {
        grid-template-columns: 1.2fr 1fr 1fr 1fr;
      }
    }
    @media (min-width: 1024px) {
      .columnas {
        grid-template-columns: 1fr 1fr;
        align-items: start;
      }
    }
  `,
})
export class PanelAgendaComponent implements OnInit {
  private readonly api = inject(OwnerApi);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  protected readonly store = inject(OwnerStore);

  protected readonly dias = DIAS;
  protected readonly horarios = signal<HorarioCancha[]>([]);
  protected readonly bloqueos = signal<BloqueoCancha[]>([]);
  protected readonly canchaId = signal<number | null>(null);
  protected readonly guardando = signal(false);
  protected readonly errorHorario = signal<string | null>(null);
  protected readonly errorBloqueo = signal<string | null>(null);

  protected readonly horarioForm = this.fb.nonNullable.group({
    diaSemana: [1, [Validators.required, Validators.min(1), Validators.max(7)]],
    horaInicio: ['18:00', [Validators.required]],
    horaFin: ['23:00', [Validators.required]],
    precio: [null as number | null],
  });

  protected readonly bloqueoForm = this.fb.nonNullable.group({
    motivo: ['', [Validators.required, Validators.maxLength(200)]],
    inicio: ['', [Validators.required]],
    fin: ['', [Validators.required]],
  });

  ngOnInit(): void {
    const query = Number(this.route.snapshot.queryParamMap.get('cancha'));
    const primera = this.store.canchas()[0]?.id ?? null;
    this.canchaId.set(Number.isFinite(query) && query > 0 ? query : primera);
    this.cargar();
  }

  protected dia(numero: number): string {
    return DIAS[numero] ?? `Día ${numero}`;
  }

  protected cambiarCancha(event: Event): void {
    this.canchaId.set(Number((event.target as HTMLSelectElement).value));
    this.cargar();
  }

  protected crearHorario(): void {
    const idCancha = this.canchaId();
    if (!idCancha) {
      return;
    }
    if (this.horarioForm.invalid) {
      this.horarioForm.markAllAsTouched();
      return;
    }
    const value = this.horarioForm.getRawValue();
    this.guardando.set(true);
    this.errorHorario.set(null);
    this.api
      .crearHorario(idCancha, {
        diaSemana: value.diaSemana,
        horaInicio: `${value.horaInicio}:00`,
        horaFin: `${value.horaFin}:00`,
        precio: value.precio,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.success('Horario agregado.');
          void this.cargar();
        },
        error: (error) => {
          this.errorHorario.set(toApiError(error).message);
          this.guardando.set(false);
        },
      });
  }

  protected async eliminarHorario(horario: HorarioCancha): Promise<void> {
    const idCancha = this.canchaId();
    if (!idCancha) {
      return;
    }
    const ok = await this.confirm.confirm({
      title: 'Quitar horario',
      message: 'Las franjas futuras de ese horario dejarán de ofrecerse.',
      confirmText: 'Quitar',
      danger: true,
    });
    if (!ok) {
      return;
    }
    this.api.eliminarHorario(idCancha, horario.id).subscribe({
      next: () => {
        this.toast.success('Horario eliminado.');
        void this.cargar();
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  protected crearBloqueo(): void {
    const idCancha = this.canchaId();
    if (!idCancha) {
      return;
    }
    if (this.bloqueoForm.invalid) {
      this.bloqueoForm.markAllAsTouched();
      return;
    }
    const value = this.bloqueoForm.getRawValue();
    const inicio = new Date(`${value.inicio}:00-05:00`);
    const fin = new Date(`${value.fin}:00-05:00`);
    if (!(fin > inicio)) {
      this.errorBloqueo.set('El fin debe ser posterior al inicio.');
      return;
    }
    this.guardando.set(true);
    this.errorBloqueo.set(null);
    this.api
      .crearBloqueo(idCancha, {
        inicio: inicio.toISOString(),
        fin: fin.toISOString(),
        motivo: value.motivo.trim(),
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.success('Bloqueo agregado.');
          void this.cargar();
        },
        error: (error) => {
          this.errorBloqueo.set(toApiError(error).message);
          this.guardando.set(false);
        },
      });
  }

  protected async eliminarBloqueo(bloqueo: BloqueoCancha): Promise<void> {
    const idCancha = this.canchaId();
    if (!idCancha) {
      return;
    }
    const ok = await this.confirm.confirm({
      title: 'Quitar bloqueo',
      message: `Se liberara la franja bloqueada por "${bloqueo.motivo}".`,
      confirmText: 'Quitar',
      danger: true,
    });
    if (!ok) {
      return;
    }
    this.api.eliminarBloqueo(idCancha, bloqueo.id).subscribe({
      next: () => {
        this.toast.success('Bloqueo eliminado.');
        void this.cargar();
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  private async cargar(): Promise<void> {
    const idCancha = this.canchaId();
    if (!idCancha) {
      return;
    }
    try {
      const [horarios, bloqueos] = await Promise.all([
        firstValueFrom(this.api.horarios(idCancha)),
        firstValueFrom(this.api.bloqueos(idCancha)),
      ]);
      this.horarios.set(horarios);
      this.bloqueos.set(bloqueos);
    } catch (error) {
      this.errorHorario.set(toApiError(error).message);
    }
  }
}
