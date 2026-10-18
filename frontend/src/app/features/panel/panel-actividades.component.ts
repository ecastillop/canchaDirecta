import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ActividadesApi } from '../../core/api/actividades.api';
import { Actividad, Inscripcion } from '../../core/api/actividades.models';
import { OwnerStore } from '../../core/api/owner.store';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { ConfirmService } from '../../shared/ui/confirm/confirm.service';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { ToastService } from '../../shared/ui/toast/toast.service';

@Component({
  selector: 'app-panel-actividades',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    DatePipe,
    CdButtonDirective,
    CdInputDirective,
    CdEmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!store.establecimientoId()) {
      <cd-empty-state title="Falta el establecimiento" message="Configura tu establecimiento en la sección Resumen." />
    } @else {
      <section class="cd-card formulario">
        <div class="titulo-fila">
          <div>
            <h2 class="seccion">{{ editando() ? 'Editar actividad' : 'Nueva actividad' }}</h2>
            <p class="cd-small cd-muted">
              Torneos, clases o partidos abiertos con cupos limitados. Los clientes se inscriben desde la
              página de Actividades.
            </p>
          </div>
          @if (editando()) {
            <button cdButton variant="secondary" size="sm" type="button" (click)="cancelarEdicion()">
              Cancelar edición
            </button>
          }
        </div>

        @if (error()) {
          <p class="alerta" role="alert">{{ error() }}</p>
        }

        <form class="cd-stack" [formGroup]="form" (ngSubmit)="guardar()" novalidate>
          <label class="cd-field">
            <span class="cd-field__label">Nombre</span>
            <input cdInput formControlName="nombre" maxlength="150" placeholder="Torneo relámpago de fútbol 7" />
          </label>
          <label class="cd-field">
            <span class="cd-field__label">Descripción</span>
            <textarea
              cdInput
              formControlName="descripcion"
              maxlength="800"
              rows="3"
              placeholder="Categorías, premios, requisitos..."
            ></textarea>
          </label>
          <div class="tres">
            <label class="cd-field">
              <span class="cd-field__label">Inicio</span>
              <input cdInput type="datetime-local" formControlName="inicio" />
            </label>
            <label class="cd-field">
              <span class="cd-field__label">Fin</span>
              <input cdInput type="datetime-local" formControlName="fin" />
            </label>
            <label class="cd-field">
              <span class="cd-field__label">Cupos</span>
              <input cdInput type="number" min="1" max="500" formControlName="cupos" />
            </label>
            <label class="cd-field">
              <span class="cd-field__label">Precio por persona (S/)</span>
              <input cdInput type="number" min="0" step="0.01" formControlName="precio" />
            </label>
          </div>
          <button cdButton type="submit" [loading]="guardando()">
            {{ editando() ? 'Guardar cambios' : 'Publicar actividad' }}
          </button>
        </form>
      </section>

      <section class="cd-card lista-card">
        <div class="titulo-fila">
          <h2 class="seccion">Mis actividades</h2>
          <button cdButton variant="secondary" size="sm" type="button" (click)="cargar()">
            Actualizar
          </button>
        </div>
        @if (cargando()) {
          <p class="cd-small cd-muted" role="status">Cargando actividades...</p>
        } @else if (actividades().length === 0) {
          <p class="cd-small cd-muted">Aún no publicaste actividades en este establecimiento.</p>
        } @else {
          <ul class="lista">
            @for (actividad of actividades(); track actividad.id) {
              <li class="actividad">
                <div class="actividad__datos">
                  <div class="cd-row actividad__titulo">
                    <strong>{{ actividad.nombre }}</strong>
                    <span
                      class="cd-badge"
                      [class.cd-badge--success]="actividad.estado === 'ACTIVA'"
                      [class.cd-badge--danger]="actividad.estado === 'CANCELADA'"
                    >
                      {{ actividad.estado }}
                    </span>
                  </div>
                  <p class="cd-xs cd-muted">
                    {{ actividad.inicio | date: 'EEE d MMM yyyy, HH:mm' }} -
                    {{ actividad.fin | date: 'HH:mm' }} · {{ actividad.distrito }}
                  </p>
                  <p class="cd-xs cd-muted">
                    {{ actividad.inscritos }} / {{ actividad.cupos }} inscritos ·
                    {{ actividad.precio | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }} por persona
                  </p>
                </div>
                <div class="acciones">
                  <button cdButton variant="secondary" size="sm" type="button" (click)="verInscritos(actividad)">
                    {{ viendo() === actividad.id ? 'Ocultar inscritos' : 'Ver inscritos' }}
                  </button>
                  @if (actividad.estado === 'ACTIVA') {
                    <button cdButton variant="secondary" size="sm" type="button" (click)="editar(actividad)">
                      Editar
                    </button>
                    <button cdButton variant="danger" size="sm" type="button" (click)="cancelarActividad(actividad)">
                      Cancelar
                    </button>
                  }
                </div>

                @if (viendo() === actividad.id) {
                  <div class="inscritos">
                    @if (cargandoInscritos()) {
                      <p class="cd-xs cd-muted" role="status">Cargando inscritos...</p>
                    } @else if (inscritos().length === 0) {
                      <p class="cd-xs cd-muted">Todavía no hay inscritos confirmados.</p>
                    } @else {
                      <ul>
                        @for (inscripcion of inscritos(); track inscripcion.id) {
                          <li>
                            <span>{{ inscripcion.nombreUsuario }}</span>
                            <span class="cd-xs cd-muted">{{ inscripcion.correoUsuario }}</span>
                          </li>
                        }
                      </ul>
                    }
                  </div>
                }
              </li>
            }
          </ul>
        }
      </section>
    }
  `,
  styles: `
    .seccion {
      font-size: var(--cd-text-base);
      margin: 0;
    }
    .titulo-fila {
      display: flex;
      align-items: start;
      justify-content: space-between;
      gap: var(--cd-space-3);
      flex-wrap: wrap;
      margin-bottom: var(--cd-space-3);
    }
    .titulo-fila p {
      margin: 4px 0 0;
    }
    .tres {
      display: grid;
      gap: var(--cd-space-3);
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
    .lista-card {
      margin-top: var(--cd-space-4);
    }
    .lista {
      list-style: none;
      padding: 0;
      display: grid;
      gap: var(--cd-space-2);
      margin: 0;
    }
    .actividad {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--cd-space-3);
      padding: var(--cd-space-3);
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-sm);
    }
    .actividad__datos {
      display: grid;
      gap: 4px;
      min-width: 240px;
    }
    .actividad__titulo {
      flex-wrap: wrap;
    }
    .actividad__datos p {
      margin: 0;
    }
    .acciones {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
    .inscritos {
      flex-basis: 100%;
      padding: 12px;
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-surface-alt);
    }
    .inscritos p {
      margin: 0;
    }
    .inscritos ul {
      display: grid;
      gap: 6px;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .inscritos li {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
      font-size: var(--cd-text-sm);
    }
    @media (min-width: 720px) {
      .tres {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }
  `,
})
export class PanelActividadesComponent implements OnInit {
  private readonly api = inject(ActividadesApi);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  protected readonly store = inject(OwnerStore);

  protected readonly actividades = signal<Actividad[]>([]);
  protected readonly cargando = signal(false);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly editando = signal<number | null>(null);
  protected readonly viendo = signal<number | null>(null);
  protected readonly inscritos = signal<Inscripcion[]>([]);
  protected readonly cargandoInscritos = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(150)]],
    descripcion: ['', [Validators.maxLength(800)]],
    inicio: ['', [Validators.required]],
    fin: ['', [Validators.required]],
    cupos: [16, [Validators.required, Validators.min(1), Validators.max(500)]],
    precio: [0, [Validators.required, Validators.min(0)]],
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    const idEstablecimiento = this.store.establecimientoId();
    if (!idEstablecimiento) {
      return;
    }
    this.cargando.set(true);
    void firstValueFrom(this.api.delEstablecimiento(idEstablecimiento))
      .then((actividades) => this.actividades.set(actividades))
      .catch((error) => this.error.set(toApiError(error).message))
      .finally(() => this.cargando.set(false));
  }

  protected async guardar(): Promise<void> {
    const idEstablecimiento = this.store.establecimientoId();
    if (!idEstablecimiento || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const inicio = new Date(value.inicio);
    const fin = new Date(value.fin);
    if (Number.isNaN(inicio.getTime()) || Number.isNaN(fin.getTime())) {
      this.error.set('Indica una fecha de inicio y fin válidas.');
      return;
    }
    if (fin <= inicio) {
      this.error.set('La fecha de fin debe ser posterior a la de inicio.');
      return;
    }
    const payload = {
      nombre: value.nombre.trim(),
      descripcion: value.descripcion.trim() || null,
      inicio: inicio.toISOString(),
      fin: fin.toISOString(),
      cupos: value.cupos,
      precio: value.precio,
    };
    this.guardando.set(true);
    this.error.set(null);
    try {
      if (this.editando()) {
        await firstValueFrom(this.api.actualizar(this.editando()!, payload));
        this.toast.success('Actividad actualizada.');
      } else {
        await firstValueFrom(this.api.crear(idEstablecimiento, payload));
        this.toast.success('Actividad publicada.');
      }
      this.cancelarEdicion();
      this.cargar();
    } catch (error) {
      this.error.set(toApiError(error).message);
    } finally {
      this.guardando.set(false);
    }
  }

  protected editar(actividad: Actividad): void {
    this.editando.set(actividad.id);
    this.error.set(null);
    this.form.reset({
      nombre: actividad.nombre,
      descripcion: actividad.descripcion ?? '',
      inicio: this.aValorLocal(actividad.inicio),
      fin: this.aValorLocal(actividad.fin),
      cupos: actividad.cupos,
      precio: actividad.precio,
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected cancelarEdicion(): void {
    this.editando.set(null);
    this.error.set(null);
    this.form.reset({
      nombre: '',
      descripcion: '',
      inicio: '',
      fin: '',
      cupos: 16,
      precio: 0,
    });
  }

  protected async cancelarActividad(actividad: Actividad): Promise<void> {
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
      await firstValueFrom(this.api.cancelar(actividad.id));
      this.toast.success('Actividad cancelada.');
      if (this.viendo() === actividad.id) {
        this.viendo.set(null);
        this.inscritos.set([]);
      }
      this.cargar();
    } catch (error) {
      this.toast.error(toApiError(error).message);
    }
  }

  protected verInscritos(actividad: Actividad): void {
    if (this.viendo() === actividad.id) {
      this.viendo.set(null);
      this.inscritos.set([]);
      return;
    }
    this.viendo.set(actividad.id);
    this.inscritos.set([]);
    this.cargandoInscritos.set(true);
    void firstValueFrom(this.api.inscritos(actividad.id))
      .then((inscritos) => this.inscritos.set(inscritos))
      .catch((error) => this.toast.error(toApiError(error).message))
      .finally(() => this.cargandoInscritos.set(false));
  }

  private aValorLocal(iso: string): string {
    const fecha = new Date(iso);
    const pad = (valor: number) => String(valor).padStart(2, '0');
    return `${fecha.getFullYear()}-${pad(fecha.getMonth() + 1)}-${pad(fecha.getDate())}T${pad(
      fecha.getHours(),
    )}:${pad(fecha.getMinutes())}`;
  }
}
