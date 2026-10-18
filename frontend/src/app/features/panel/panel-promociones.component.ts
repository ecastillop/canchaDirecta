import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { OwnerApi } from '../../core/api/owner.api';
import { OwnerStore } from '../../core/api/owner.store';
import { Promocion } from '../../core/api/owner.models';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { CdPaginatorComponent } from '../../shared/ui/pagination/cd-paginator.component';
import { ToastService } from '../../shared/ui/toast/toast.service';

@Component({
  selector: 'app-panel-promociones',
  imports: [ReactiveFormsModule, CurrencyPipe, DatePipe, CdButtonDirective, CdInputDirective, CdEmptyStateComponent, CdPaginatorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!store.establecimientoId()) {
      <cd-empty-state title="Falta el establecimiento" message="Configura tu establecimiento en la sección Resumen." />
    } @else {
      <section class="cd-card">
        <h2 class="seccion">Promociones del establecimiento</h2>
        <p class="cd-small cd-muted">
          La promoción se asocia a todas tus canchas y se aplica al usar el código al reservar.
        </p>
        @if (cargando()) {
          <p class="cd-small cd-muted" role="status">Cargando promociones...</p>
        } @else if (promociones().length === 0) {
          <p class="cd-small cd-muted">Aún no hay promociones registradas.</p>
        } @else {
          <ul class="lista">
            @for (promocion of promocionesPagina(); track promocion.id) {
              <li>
                <div>
                  <strong>{{ promocion.codigo }}</strong>
                  <span class="cd-badge" [class]="promocion.estado === 'ACTIVA' ? 'cd-badge--success' : 'cd-badge'">
                    {{ promocion.estado }}
                  </span>
                  <p class="cd-xs cd-muted">{{ promocion.descripcion }}</p>
                  <p class="cd-xs cd-muted">
                    @if (promocion.tipoDescuento === 'PORCENTAJE') {
                      {{ promocion.valor }}% de descuento
                    } @else {
                      {{ promocion.valor | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }} de descuento
                    }
                    · {{ promocion.fechaInicio | date: 'd MMM' }} - {{ promocion.fechaFin | date: 'd MMM yyyy' }}
                  </p>
                  <p class="cd-xs cd-muted">
                    {{ promocion.usos }} canjes
                    @if (promocion.limiteUsos) {
                      de {{ promocion.limiteUsos }}
                    }
                    @if (promocion.limitePorUsuario) {
                      · máximo {{ promocion.limitePorUsuario }} por cliente
                    }
                  </p>
                </div>
                <button cdButton variant="secondary" size="sm" type="button" (click)="cambiarEstado(promocion)">
                  {{ promocion.estado === 'ACTIVA' ? 'Desactivar' : 'Activar' }}
                </button>
              </li>
            }
          </ul>
          <cd-paginator
            [pagina]="pagina()"
            [total]="promociones().length"
            [tamano]="tamanoPagina"
            etiqueta="Paginación de promociones"
            (paginaChange)="pagina.set($event)"
          />
        }
      </section>

      <section class="cd-card formulario">
        <h2 class="seccion">Nueva promoción</h2>
        @if (error()) {
          <p class="alerta" role="alert">{{ error() }}</p>
        }
        <form class="cd-stack" [formGroup]="form" (ngSubmit)="crear()" novalidate>
          <div class="dos">
            <label class="cd-field">
              <span class="cd-field__label">Código</span>
              <input cdInput formControlName="codigo" maxlength="30" />
            </label>
            <label class="cd-field">
              <span class="cd-field__label">Descripción</span>
              <input cdInput formControlName="descripcion" maxlength="200" />
            </label>
          </div>
          <div class="tres">
            <label class="cd-field">
              <span class="cd-field__label">Tipo</span>
              <select cdInput formControlName="tipoDescuento">
                <option value="PORCENTAJE">Porcentaje</option>
                <option value="MONTO">Monto fijo</option>
              </select>
            </label>
            <label class="cd-field">
              <span class="cd-field__label">Valor</span>
              <input cdInput type="number" min="1" step="0.01" formControlName="valor" />
            </label>
            <label class="cd-field">
              <span class="cd-field__label">Desde</span>
              <input cdInput type="date" formControlName="fechaInicio" />
            </label>
            <label class="cd-field">
              <span class="cd-field__label">Hasta</span>
              <input cdInput type="date" formControlName="fechaFin" />
            </label>
            <label class="cd-field">
              <span class="cd-field__label">Límite total de canjes (0 = sin límite)</span>
              <input cdInput type="number" min="0" formControlName="limiteUsos" />
            </label>
            <label class="cd-field">
              <span class="cd-field__label">Límite por cliente (0 = sin límite)</span>
              <input cdInput type="number" min="0" formControlName="limitePorUsuario" />
            </label>
          </div>
          <button cdButton type="submit" [loading]="guardando()">Crear promoción</button>
        </form>
      </section>
    }
  `,
  styles: `
    .seccion {
      font-size: var(--cd-text-base);
      margin-bottom: var(--cd-space-2);
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
    .formulario {
      margin-top: var(--cd-space-4);
    }
    .dos,
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
    @media (min-width: 640px) {
      .dos {
        grid-template-columns: 1fr 2fr;
      }
      .tres {
        grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
      }
    }
  `,
})
export class PanelPromocionesComponent implements OnInit {
  private readonly api = inject(OwnerApi);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);
  protected readonly store = inject(OwnerStore);

  protected readonly promociones = signal<Promocion[]>([]);
  protected readonly cargando = signal(false);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly pagina = signal(1);
  protected readonly tamanoPagina = 10;
  protected readonly promocionesPagina = computed(() => {
    const desde = (this.pagina() - 1) * this.tamanoPagina;
    return this.promociones().slice(desde, desde + this.tamanoPagina);
  });

  protected readonly form = this.fb.nonNullable.group({
    codigo: ['', [Validators.required, Validators.maxLength(30)]],
    descripcion: ['', [Validators.required, Validators.maxLength(200)]],
    tipoDescuento: ['PORCENTAJE' as 'PORCENTAJE' | 'MONTO', [Validators.required]],
    valor: [10, [Validators.required, Validators.min(0.01)]],
    fechaInicio: ['', [Validators.required]],
    fechaFin: ['', [Validators.required]],
    limiteUsos: [0, [Validators.min(0)]],
    limitePorUsuario: [0, [Validators.min(0)]],
  });

  ngOnInit(): void {
    void this.cargar();
  }

  protected crear(): void {
    const idEstablecimiento = this.store.establecimientoId();
    if (!idEstablecimiento || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.guardando.set(true);
    this.error.set(null);
    this.api
      .crearPromocion(idEstablecimiento, {
        codigo: value.codigo.trim().toUpperCase(),
        descripcion: value.descripcion.trim(),
        tipoDescuento: value.tipoDescuento,
        valor: value.valor,
        fechaInicio: value.fechaInicio,
        fechaFin: value.fechaFin,
        limiteUsos: value.limiteUsos > 0 ? value.limiteUsos : null,
        limitePorUsuario: value.limitePorUsuario > 0 ? value.limitePorUsuario : null,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.success('Promocion creada.');
          this.form.reset({
            codigo: '',
            descripcion: '',
            tipoDescuento: 'PORCENTAJE',
            valor: 10,
            fechaInicio: '',
            fechaFin: '',
            limiteUsos: 0,
            limitePorUsuario: 0,
          });
          void this.cargar();
        },
        error: (error) => {
          this.error.set(toApiError(error).message);
          this.guardando.set(false);
        },
      });
  }

  protected cambiarEstado(promocion: Promocion): void {
    const idEstablecimiento = this.store.establecimientoId();
    if (!idEstablecimiento) {
      return;
    }
    const estado = promocion.estado === 'ACTIVA' ? 'INACTIVA' : 'ACTIVA';
    this.api.cambiarEstadoPromocion(idEstablecimiento, promocion.id, estado).subscribe({
      next: (actualizada) => {
        this.promociones.update((lista) => lista.map((p) => (p.id === actualizada.id ? actualizada : p)));
        this.toast.success(`Promocion ${estado.toLowerCase()}.`);
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  private async cargar(): Promise<void> {
    const idEstablecimiento = this.store.establecimientoId();
    if (!idEstablecimiento) {
      return;
    }
    this.cargando.set(true);
    try {
      this.promociones.set(await firstValueFrom(this.api.promociones(idEstablecimiento)));
    } catch (error) {
      this.error.set(toApiError(error).message);
    } finally {
      this.cargando.set(false);
    }
  }
}
