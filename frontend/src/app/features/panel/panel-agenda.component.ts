import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { OwnerApi } from '../../core/api/owner.api';
import { OwnerStore } from '../../core/api/owner.store';
import { BloqueoCancha } from '../../core/api/owner.models';
import { ReservasApi } from '../../core/api/reservas.api';
import { Reserva } from '../../core/api/reservas.models';
import { HorarioCancha } from '../../core/api/venues.models';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { ConfirmService } from '../../shared/ui/confirm/confirm.service';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { ToastService } from '../../shared/ui/toast/toast.service';
import {
  DESFASE_LIMA,
  DIAS,
  DIAS_CORTOS,
  MESES_LARGOS,
  etiquetaDeSemana,
  horaDeMinutos,
  hoyLima,
  lunesDe,
  minutosDe,
  partesDeFecha,
  sumarDias,
} from '../../shared/util/fecha-lima';

/** Estado de una hora del horario semanal en la vista de ocupacion del propietario. */
type EstadoCelda = 'libre' | 'reservada' | 'bloqueada' | 'pasado' | 'fuera' | 'cerrado';

interface FranjaSemana {
  hora: string;
  estado: EstadoCelda;
}

interface DiaSemana {
  fecha: string;
  corta: string;
  larga: string;
  numero: number;
  esHoy: boolean;
  /** La cancha tiene agenda configurada y ese dia de la semana no esta en ella. */
  cerrado: boolean;
  franjas: FranjaSemana[];
}

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
        <section class="cd-card semana">
          <div class="semana__cabecera">
            <div>
              <h2 class="seccion">Ocupación de la semana</h2>
              <p class="cd-xs cd-muted">
                Cada hora de tu horario semanal con lo que ya está tomado: reservas de clientes y
                bloqueos propios.
              </p>
            </div>
            <div class="semana__nav" role="group" aria-label="Cambiar de semana">
              <button
                cdButton
                variant="secondary"
                size="sm"
                type="button"
                aria-label="Semana anterior"
                (click)="semanaAnterior()"
              >
                ‹
              </button>
              <span class="semana__titulo">{{ etiquetaSemana() }}</span>
              <button
                cdButton
                variant="secondary"
                size="sm"
                type="button"
                aria-label="Semana siguiente"
                (click)="semanaSiguiente()"
              >
                ›
              </button>
              @if (!esSemanaActual()) {
                <button cdButton variant="ghost" size="sm" type="button" (click)="semanaActual()">
                  Hoy
                </button>
              }
            </div>
          </div>

          @if (errorSemana()) {
            <p class="alerta" role="alert">{{ errorSemana() }}</p>
          } @else if (horas().length === 0) {
            <p class="cd-small cd-muted">
              Agrega un horario de al menos una hora para ver la ocupación de la semana.
            </p>
          } @else {
            <div class="calendario__scroll">
              <table class="calendario__tabla">
                <caption class="cd-visually-hidden">
                  Ocupación por hora y día de la semana de la cancha seleccionada
                </caption>
                <thead>
                  <tr>
                    <th scope="col" class="col-hora">Hora</th>
                    @for (dia of diasSemana(); track dia.fecha) {
                      <th scope="col" [class.col-hoy]="dia.esHoy">
                        <span class="dia__corta">{{ dia.corta }}</span>
                        <span class="dia__numero">{{ dia.numero }}</span>
                      </th>
                    }
                  </tr>
                </thead>
                <tbody>
                  @for (hora of horas(); track hora) {
                    <tr>
                      <th scope="row" class="col-hora">{{ hora }}</th>
                      @for (dia of diasSemana(); track dia.fecha) {
                        @if (dia.cerrado) {
                          @if (hora === horas()[0]) {
                            <td
                              [attr.rowspan]="horas().length"
                              class="celda celda--cerrado"
                              [attr.aria-label]="dia.larga + ': cerrado'"
                            >
                              Cerrado
                            </td>
                          }
                        } @else {
                          <td
                            [class]="'celda celda--' + estadoEn(dia, hora)"
                            [attr.aria-label]="etiquetaCelda(dia, hora)"
                          >
                            {{ textoEstado(estadoEn(dia, hora)) }}
                          </td>
                        }
                      }
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <ul class="leyenda">
              <li><span class="punto punto--libre" aria-hidden="true"></span>Libre</li>
              <li><span class="punto punto--reservada" aria-hidden="true"></span>Reservada</li>
              <li><span class="punto punto--bloqueada" aria-hidden="true"></span>Bloqueada</li>
              <li><span class="punto punto--ocupada" aria-hidden="true"></span>Ya pasó o fuera de horario</li>
            </ul>
          }
        </section>

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
    .semana {
      margin-bottom: var(--cd-space-4);
    }
    .semana__cabecera {
      display: flex;
      flex-wrap: wrap;
      align-items: end;
      justify-content: space-between;
      gap: var(--cd-space-3);
    }
    .semana__nav {
      display: flex;
      align-items: center;
      gap: var(--cd-space-2);
    }
    .semana__titulo {
      min-width: 9rem;
      text-align: center;
      font-size: var(--cd-text-sm);
      font-weight: 600;
      text-transform: capitalize;
    }
    .calendario__scroll {
      overflow-x: auto;
      margin-top: var(--cd-space-3);
    }
    .calendario__tabla {
      width: 100%;
      border-collapse: collapse;
      font-size: var(--cd-text-xs);
    }
    .calendario__tabla th,
    .calendario__tabla td {
      padding: var(--cd-space-1) var(--cd-space-2);
      border: 1px solid var(--cd-color-border);
      text-align: center;
      white-space: nowrap;
    }
    .calendario__tabla thead th {
      background: var(--cd-color-surface-alt);
    }
    .col-hora {
      text-align: left;
      font-variant-numeric: tabular-nums;
    }
    .dia__corta,
    .dia__numero {
      display: block;
    }
    .dia__numero {
      font-size: var(--cd-text-sm);
      font-weight: 700;
    }
    .col-hoy {
      background: var(--cd-color-accent-soft, var(--cd-color-accent));
    }
    .celda--libre {
      color: var(--cd-color-text-muted);
    }
    .celda--reservada {
      background: var(--cd-color-info-soft);
      color: var(--cd-color-info);
      font-weight: 600;
    }
    .celda--bloqueada {
      background: var(--cd-color-warning-soft);
      color: var(--cd-color-warning);
      font-weight: 600;
    }
    .celda--pasado,
    .celda--fuera,
    .celda--cerrado {
      background: var(--cd-color-surface-alt);
      color: var(--cd-color-text-muted);
    }
    .leyenda {
      display: flex;
      flex-wrap: wrap;
      gap: var(--cd-space-3);
      margin-top: var(--cd-space-2);
      padding: 0;
      list-style: none;
      font-size: var(--cd-text-xs);
      color: var(--cd-color-text-muted);
    }
    .leyenda li {
      display: flex;
      align-items: center;
      gap: var(--cd-space-1);
    }
    .punto {
      width: 0.75rem;
      height: 0.75rem;
      border: 1px solid var(--cd-color-border);
      border-radius: 3px;
    }
    .punto--libre {
      background: var(--cd-color-surface);
    }
    .punto--reservada {
      background: var(--cd-color-info-soft);
      border-color: var(--cd-color-info);
    }
    .punto--bloqueada {
      background: var(--cd-color-warning-soft);
      border-color: var(--cd-color-warning);
    }
    .punto--ocupada {
      background: var(--cd-color-surface-alt);
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
  private readonly reservasApi = inject(ReservasApi);
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

  /** Ocupacion semanal: la semana que se muestra y los datos de esa semana. */
  protected readonly inicioSemana = signal(lunesDe(hoyLima()));
  protected readonly bloqueosSemana = signal<BloqueoCancha[]>([]);
  protected readonly reservasSemana = signal<Reserva[]>([]);
  protected readonly errorSemana = signal<string | null>(null);

  protected readonly etiquetaSemana = computed(() => etiquetaDeSemana(this.inicioSemana()));
  protected readonly esSemanaActual = computed(() => this.inicioSemana() === lunesDe(hoyLima()));
  protected readonly diasSemana = computed<DiaSemana[]>(() => this.construirDias());
  /** Horas (filas) de la tabla: la union de las franjas de los siete dias. */
  protected readonly horas = computed(() =>
    [...new Set(this.diasSemana().flatMap((dia) => dia.franjas.map((franja) => franja.hora)))].sort(),
  );

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

  protected semanaAnterior(): void {
    this.inicioSemana.set(sumarDias(this.inicioSemana(), -7));
    void this.cargarSemana();
  }

  protected semanaSiguiente(): void {
    this.inicioSemana.set(sumarDias(this.inicioSemana(), 7));
    void this.cargarSemana();
  }

  protected semanaActual(): void {
    this.inicioSemana.set(lunesDe(hoyLima()));
    void this.cargarSemana();
  }

  /** Estado de una hora concreta de un dia de la rejilla. */
  protected estadoEn(dia: DiaSemana, hora: string): EstadoCelda {
    if (dia.cerrado) {
      return 'cerrado';
    }
    return dia.franjas.find((franja) => franja.hora === hora)?.estado ?? 'fuera';
  }

  protected etiquetaCelda(dia: DiaSemana, hora: string): string {
    return `${dia.larga}, ${hora}: ${this.textoEstado(this.estadoEn(dia, hora)).toLowerCase()}`;
  }

  protected textoEstado(estado: EstadoCelda): string {
    switch (estado) {
      case 'reservada':
        return 'Reservada';
      case 'bloqueada':
        return 'Bloqueada';
      case 'libre':
        return 'Libre';
      case 'pasado':
        return 'Ya pasó';
      case 'cerrado':
        return 'Cerrado';
      default:
        return 'Fuera de horario';
    }
  }

  /**
   * Ocupacion de los siete dias de `inicioSemana`: por cada hora del horario semanal se marca
   * si esta reservada, bloqueada, libre o si ya paso. Los datos salen de la misma fuente que ve
   * el cliente (reservas `PENDIENTE`/`CONFIRMADA` y bloqueos retienen cupo; ADR-0043).
   */
  private construirDias(): DiaSemana[] {
    const inicio = this.inicioSemana();
    const hoy = hoyLima();
    const agenda = this.horarios();
    const bloqueos = this.bloqueosSemana();
    const reservas = this.reservasSemana();
    const ahora = new Date();
    return Array.from({ length: 7 }, (_, indice) => {
      const fecha = sumarDias(inicio, indice);
      const partes = partesDeFecha(fecha);
      const delDia = agenda.filter((horario) => horario.diaSemana === partes.indice + 1);
      const franjas: FranjaSemana[] = [];
      for (const horario of delDia) {
        let minuto = minutosDe(horario.horaInicio);
        const fin = minutosDe(horario.horaFin);
        while (minuto + 60 <= fin) {
          franjas.push({
            hora: horaDeMinutos(minuto),
            estado: this.estadoDeSlot(fecha, minuto, bloqueos, reservas, ahora),
          });
          minuto += 60;
        }
      }
      return {
        fecha,
        corta: DIAS_CORTOS[partes.indice],
        larga: `${DIAS[partes.indice + 1]} ${partes.numero} de ${MESES_LARGOS[partes.mes]}`,
        numero: partes.numero,
        esHoy: fecha === hoy,
        cerrado: agenda.length > 0 && delDia.length === 0,
        franjas,
      };
    });
  }

  private estadoDeSlot(
    fecha: string,
    minuto: number,
    bloqueos: BloqueoCancha[],
    reservas: Reserva[],
    ahora: Date,
  ): EstadoCelda {
    const inicio = new Date(`${fecha}T${horaDeMinutos(minuto)}:00${DESFASE_LIMA}`);
    const fin = new Date(inicio.getTime() + 60 * 60 * 1000);
    const solapa = (franja: { inicio: string; fin: string }): boolean =>
      new Date(franja.inicio) < fin && new Date(franja.fin) > inicio;
    if (bloqueos.some(solapa)) {
      return 'bloqueada';
    }
    if (reservas.some(solapa)) {
      return 'reservada';
    }
    return fin <= ahora ? 'pasado' : 'libre';
  }

  /** Bloqueos y reservas de la semana mostrada, para la rejilla de ocupacion. */
  private async cargarSemana(): Promise<void> {
    const idCancha = this.canchaId();
    if (!idCancha) {
      return;
    }
    const inicio = this.inicioSemana();
    const desde = new Date(`${inicio}T00:00:00${DESFASE_LIMA}`).toISOString();
    const hasta = new Date(`${sumarDias(inicio, 7)}T00:00:00${DESFASE_LIMA}`).toISOString();
    this.errorSemana.set(null);
    try {
      const [bloqueos, reservas] = await Promise.all([
        firstValueFrom(this.api.bloqueos(idCancha, desde, hasta)),
        firstValueFrom(this.reservasApi.recibidas(null, 100, { idCancha, desde, hasta })),
      ]);
      this.bloqueosSemana.set(bloqueos);
      this.reservasSemana.set(reservas);
    } catch (error) {
      this.errorSemana.set(toApiError(error).message);
    }
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
    await this.cargarSemana();
  }
}
