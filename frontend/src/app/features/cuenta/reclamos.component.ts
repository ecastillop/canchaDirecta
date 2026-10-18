import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EngagementApi, Reclamo } from '../../core/api/engagement.api';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdPaginatorComponent } from '../../shared/ui/pagination/cd-paginator.component';
import { ToastService } from '../../shared/ui/toast/toast.service';

@Component({
  selector: 'app-reclamos',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    CdButtonDirective,
    CdInputDirective,
    CdSkeletonComponent,
    CdEmptyStateComponent,
    CdErrorStateComponent,
    CdPaginatorComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cd-container cd-page">
      <header class="cd-page__header">
        <div class="cd-row cd-row--between">
          <div>
            <h1 class="cd-page__title">Reclamos</h1>
            <p class="cd-muted">Registra un reclamo y sigue su atención.</p>
          </div>
          <button cdButton type="button" (click)="abrir.set(!abrir())">
            {{ abrir() ? 'Cerrar formulario' : 'Nuevo reclamo' }}
          </button>
        </div>
      </header>

      @if (abrir()) {
        <section class="cd-card formulario" aria-labelledby="nuevo-reclamo">
          <h2 id="nuevo-reclamo" class="seccion">Nuevo reclamo</h2>
          @if (errorForm()) {
            <p class="alerta" role="alert">{{ errorForm() }}</p>
          }
          <form class="cd-stack" [formGroup]="form" (ngSubmit)="crear()" novalidate>
            <label class="cd-field">
              <span class="cd-field__label">Reserva relacionada <span class="optional">(opcional)</span></span>
              <input cdInput type="number" min="1" formControlName="idReserva" placeholder="Número de reserva" />
            </label>
            <label class="cd-field">
              <span class="cd-field__label">Asunto</span>
              <input cdInput formControlName="asunto" maxlength="150" />
              @if (form.controls.asunto.invalid && form.controls.asunto.touched) {
                <span class="cd-field__error">Ingresa un asunto (máximo 150 caracteres).</span>
              }
            </label>
            <label class="cd-field">
              <span class="cd-field__label">Descripción</span>
              <textarea cdInput formControlName="descripcion" maxlength="800" rows="5"></textarea>
              @if (form.controls.descripcion.invalid && form.controls.descripcion.touched) {
                <span class="cd-field__error">Describe el problema (máximo 800 caracteres).</span>
              }
            </label>
            <button cdButton type="submit" [loading]="enviando()">Registrar reclamo</button>
          </form>
        </section>
      }

      @if (cargando()) {
        <div class="cd-stack">
          @for (i of [1, 2, 3]; track i) {
            <cd-skeleton height="100px" radius="12px" />
          }
        </div>
      } @else if (error()) {
        <cd-error-state [message]="error()!" (retry)="cargar()" />
      } @else if (reclamos().length === 0) {
        <cd-empty-state title="Sin reclamos" message="Si tuviste un problema con una reserva, registra un reclamo." />
      } @else {
        <ul class="lista">
          @for (reclamo of reclamosPagina(); track reclamo.id) {
            <li>
              <a [routerLink]="['/reclamos', reclamo.id]" class="item">
                <div>
                  <h2 class="item__titulo">{{ reclamo.asunto }}</h2>
                  <p class="cd-xs cd-muted">
                    #{{ reclamo.id }} · {{ reclamo.fechaRegistro | date: 'd MMM yyyy, HH:mm' }}
                    @if (reclamo.idReserva) {
                      · reserva #{{ reclamo.idReserva }}
                    }
                  </p>
                </div>
                <span class="cd-badge" [class]="claseEstado(reclamo.estado)">{{ etiqueta(reclamo.estado) }}</span>
              </a>
            </li>
          }
        </ul>
        <cd-paginator
          [pagina]="pagina()"
          [total]="reclamos().length"
          [tamano]="tamanoPagina"
          etiqueta="Paginación de reclamos"
          (paginaChange)="pagina.set($event)"
        />
      }
    </div>
  `,
  styles: `
    .formulario {
      margin-bottom: var(--cd-space-4);
    }
    .seccion {
      font-size: var(--cd-text-base);
      margin-bottom: var(--cd-space-3);
    }
    .lista {
      list-style: none;
      padding: 0;
      display: grid;
      gap: var(--cd-space-2);
    }
    .item {
      display: flex;
      justify-content: space-between;
      align-items: start;
      gap: var(--cd-space-3);
      padding: var(--cd-space-4);
      background: var(--cd-color-surface);
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-md);
      text-decoration: none;
      color: var(--cd-color-text);
    }
    .item:hover {
      border-color: var(--cd-color-accent);
    }
    .item__titulo {
      font-size: var(--cd-text-base);
      margin-bottom: var(--cd-space-1);
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
  `,
})
export class ReclamosComponent implements OnInit {
  private readonly api = inject(EngagementApi);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);

  protected readonly reclamos = signal<Reclamo[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly abrir = signal(false);
  protected readonly enviando = signal(false);
  protected readonly errorForm = signal<string | null>(null);
  protected readonly pagina = signal(1);
  protected readonly tamanoPagina = 10;
  protected readonly reclamosPagina = computed(() => {
    const desde = (this.pagina() - 1) * this.tamanoPagina;
    return this.reclamos().slice(desde, desde + this.tamanoPagina);
  });

  protected readonly form = this.fb.nonNullable.group({
    idReserva: [null as number | null],
    asunto: ['', [Validators.required, Validators.maxLength(150)]],
    descripcion: ['', [Validators.required, Validators.maxLength(800)]],
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.misReclamos().subscribe({
      next: (reclamos) => {
        this.reclamos.set(reclamos);
        this.cargando.set(false);
      },
      error: (error) => {
        this.error.set(toApiError(error).message);
        this.cargando.set(false);
      },
    });
  }

  protected crear(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    this.errorForm.set(null);
    const value = this.form.getRawValue();
    this.api
      .crearReclamo({
        idReserva: value.idReserva || null,
        asunto: value.asunto.trim(),
        descripcion: value.descripcion.trim(),
      })
      .subscribe({
        next: () => {
          this.enviando.set(false);
          this.abrir.set(false);
          this.form.reset({ idReserva: null, asunto: '', descripcion: '' });
          this.toast.success('Reclamo registrado.');
          this.cargar();
        },
        error: (error) => {
          this.errorForm.set(toApiError(error).message);
          this.enviando.set(false);
        },
      });
  }

  protected claseEstado(estado: Reclamo['estado']): string {
    const mapa: Record<Reclamo['estado'], string> = {
      ABIERTO: 'cd-badge cd-badge--warning',
      EN_REVISION: 'cd-badge cd-badge--info',
      RESUELTO: 'cd-badge cd-badge--success',
      CERRADO: 'cd-badge',
    };
    return mapa[estado];
  }

  protected etiqueta(estado: Reclamo['estado']): string {
    return estado.replace('_', ' ').toLowerCase();
  }
}
