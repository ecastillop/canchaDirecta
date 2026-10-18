import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ComparacionService } from '../../core/api/comparacion.service';
import { VenuesApi } from '../../core/api/venues.api';
import { Cancha, Distrito, Item } from '../../core/api/venues.models';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { CdPaginatorComponent } from '../../shared/ui/pagination/cd-paginator.component';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';

@Component({
  selector: 'app-catalogo',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    CurrencyPipe,
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
        <h1 class="cd-page__title">Canchas disponibles</h1>
        <p class="cd-muted">Filtra por distrito, deporte y precio. Compara hasta 4 canchas.</p>
      </header>

      <form class="filtros" [formGroup]="filtros" (ngSubmit)="aplicar()" aria-label="Filtros de busqueda">
        <label class="cd-field filtros__texto">
          <span class="cd-field__label">Buscar</span>
          <input cdInput formControlName="q" type="search" placeholder="Nombre de cancha o establecimiento" />
        </label>
        <label class="cd-field">
          <span class="cd-field__label">Distrito</span>
          <select cdInput formControlName="idDistrito">
            <option [ngValue]="null">Todos</option>
            @for (distrito of distritos(); track distrito.id) {
              <option [ngValue]="distrito.id">{{ distrito.nombre }}</option>
            }
          </select>
        </label>
        <label class="cd-field">
          <span class="cd-field__label">Deporte</span>
          <select cdInput formControlName="idDeporte">
            <option [ngValue]="null">Todos</option>
            @for (deporte of deportes(); track deporte.id) {
              <option [ngValue]="deporte.id">{{ deporte.nombre }}</option>
            }
          </select>
        </label>
        <label class="cd-field filtros__precio">
          <span class="cd-field__label">Precio max. por hora (S/)</span>
          <input cdInput type="number" min="0" step="5" formControlName="precioMax" placeholder="Sin limite" />
        </label>
        <div class="filtros__acciones">
          <button cdButton type="submit">Aplicar filtros</button>
          <button cdButton type="button" variant="ghost" (click)="limpiar()">Limpiar</button>
        </div>
      </form>

      <div class="resultado__barra">
        <p class="cd-small cd-muted" role="status">
          @if (total() > 0) {
            Mostrando {{ desde() }}–{{ hasta() }} de {{ total() }} canchas
          } @else {
            Sin resultados
          }
        </p>
      </div>

      @if (cargando()) {
        <div class="grid" aria-busy="true" aria-label="Cargando canchas">
          @for (i of [1, 2, 3, 4, 5, 6]; track i) {
            <div class="cd-card">
              <cd-skeleton height="120px" radius="12px" />
              <cd-skeleton width="70%" height="20px" />
              <cd-skeleton width="40%" height="16px" />
            </div>
          }
        </div>
      } @else if (error()) {
        <cd-error-state [message]="error()!" (retry)="cargar()" />
      } @else if (canchas().length === 0) {
        <cd-empty-state
          title="No encontramos canchas"
          message="Prueba con otro distrito, deporte o sin limite de precio."
        >
          <button cdButton variant="secondary" type="button" (click)="limpiar()">Quitar filtros</button>
        </cd-empty-state>
      } @else {
        <div class="grid">
          @for (cancha of canchas(); track cancha.id) {
            <article class="cd-card cancha">
              <div class="cancha__cabecera">
                <span class="cd-badge" [class.cd-badge--success]="cancha.estado === 'ACTIVA'">
                  {{ cancha.estado === 'ACTIVA' ? 'Disponible' : cancha.estado }}
                </span>
                <label class="comparar">
                  <input
                    type="checkbox"
                    [checked]="comparacion.tiene(cancha.id)"
                    (change)="comparar(cancha.id)"
                    [attr.aria-label]="'Comparar ' + cancha.nombre"
                  />
                  <span class="cd-xs">Comparar</span>
                </label>
              </div>
              <h2 class="cancha__nombre">{{ cancha.nombre }}</h2>
              <p class="cd-small cd-muted">Capacidad para {{ cancha.capacidad }} personas</p>
              <p class="precio">
                {{ cancha.precioBase | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}
                <span class="cd-small cd-muted">/ hora</span>
              </p>
              <p class="cd-xs cd-muted">4 horas: {{ cancha.precioBase * 4 | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</p>
              <a
                [routerLink]="['/canchas', cancha.id]"
                class="cd-btn cd-btn--primary cd-btn--block"
                [attr.aria-label]="'Ver detalle y horarios de ' + cancha.nombre"
              >
                Ver detalle
              </a>
            </article>
          }
        </div>
      }

      <cd-paginator
        [pagina]="pagina()"
        [total]="total()"
        [tamano]="tamanoPagina"
        etiqueta="Paginación de canchas"
        (paginaChange)="irPagina($event)"
      />
    </div>

    @if (comparacion.cantidad() > 0) {
      <div class="barra-comparar" role="region" aria-label="Canchas seleccionadas para comparar">
        <div class="cd-container barra-comparar__inner">
          <span class="cd-small">{{ comparacion.cantidad() }} de 4 seleccionada(s)</span>
          <div class="cd-row">
            <button cdButton variant="ghost" size="sm" type="button" (click)="comparacion.limpiar()">
              Limpiar
            </button>
            <button
              cdButton
              variant="accent"
              size="sm"
              type="button"
              [disabled]="!comparacion.puedeComparar()"
              (click)="irAComparar()"
            >
              Comparar
            </button>
          </div>
        </div>
      </div>
    }
  `,
  styles: `
    .filtros {
      display: grid;
      gap: var(--cd-space-3);
      padding: var(--cd-space-4);
      background: var(--cd-color-surface);
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-md);
      margin-bottom: var(--cd-space-4);
    }
    .filtros__acciones {
      display: flex;
      gap: var(--cd-space-2);
      align-items: end;
      flex-wrap: wrap;
    }
    .resultado__barra {
      display: flex;
      justify-content: space-between;
      align-items: end;
      gap: var(--cd-space-3);
      margin-bottom: var(--cd-space-3);
      flex-wrap: wrap;
    }
    .paginacion {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: var(--cd-space-2);
      flex-wrap: wrap;
      margin-top: var(--cd-space-5);
    }
    .paginas {
      display: flex;
      align-items: center;
      gap: 4px;
      flex-wrap: wrap;
    }
    .pagina {
      min-width: 40px;
      min-height: 40px;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-surface);
      color: var(--cd-color-text);
      font-weight: 600;
      cursor: pointer;
    }
    .pagina:hover {
      border-color: var(--cd-color-accent);
    }
    .pagina--activa {
      background: var(--cd-color-primary);
      border-color: var(--cd-color-primary);
      color: var(--cd-color-on-primary);
    }
    .puntos {
      padding-inline: 4px;
      color: var(--cd-color-text-muted);
    }
    .grid {
      display: grid;
      gap: var(--cd-space-4);
    }
    .cancha {
      display: grid;
      gap: var(--cd-space-2);
      align-content: start;
    }
    .cancha__cabecera {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--cd-space-2);
    }
    .comparar {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      color: var(--cd-color-text-muted);
    }
    .comparar input {
      width: 18px;
      height: 18px;
      accent-color: var(--cd-color-accent-strong);
    }
    .cancha__nombre {
      font-size: var(--cd-text-lg);
    }
    .precio {
      font-size: var(--cd-text-lg);
      font-weight: 800;
      color: var(--cd-color-primary);
    }
    .cancha .cd-btn {
      margin-top: var(--cd-space-2);
      text-decoration: none;
    }
    .barra-comparar {
      position: sticky;
      bottom: 0;
      z-index: 50;
      background: var(--cd-color-surface);
      border-top: 1px solid var(--cd-color-border);
      box-shadow: var(--cd-shadow-2);
      padding-block: var(--cd-space-2);
    }
    .barra-comparar__inner {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--cd-space-3);
      flex-wrap: wrap;
    }
    @media (min-width: 640px) {
      .grid {
        grid-template-columns: repeat(2, 1fr);
      }
      .filtros {
        grid-template-columns: 1.4fr 1fr 1fr 1fr auto;
        align-items: end;
      }
      .filtros__acciones {
        grid-column: 1 / -1;
      }
    }
    @media (min-width: 1024px) {
      .grid {
        grid-template-columns: repeat(3, 1fr);
      }
      .filtros {
        grid-template-columns: 1.6fr 1fr 1fr 1fr auto;
      }
      .filtros__acciones {
        grid-column: auto;
      }
    }
  `,
})
export class CatalogoComponent implements OnInit {
  private readonly api = inject(VenuesApi);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly comparacion = inject(ComparacionService);

  protected readonly canchas = signal<Cancha[]>([]);
  protected readonly distritos = signal<Distrito[]>([]);
  protected readonly deportes = signal<Item[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly total = signal(0);
  protected readonly pagina = signal(1);
  protected readonly tamanoPagina = 12;

  protected readonly totalPaginas = computed(() => Math.max(1, Math.ceil(this.total() / this.tamanoPagina)));
  protected readonly desde = computed(() =>
    this.total() === 0 ? 0 : (this.pagina() - 1) * this.tamanoPagina + 1,
  );
  protected readonly hasta = computed(() => Math.min(this.pagina() * this.tamanoPagina, this.total()));

  protected readonly filtros = this.fb.group({
    q: [''],
    idDistrito: [null as number | null],
    idDeporte: [null as number | null],
    precioMax: [null as number | null],
  });

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    this.filtros.patchValue({
      q: params.get('q') ?? '',
      idDistrito: this.numero(params.get('distrito')),
      idDeporte: this.numero(params.get('deporte')),
      precioMax: this.numero(params.get('precioMax')),
    });

    void this.cargarCatalogos();
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    const { q, idDistrito, idDeporte, precioMax } = this.filtros.getRawValue();
    this.api
      .canchasPaginadas({
        q: q?.trim() || null,
        idDistrito,
        idDeporte,
        precioMax,
        limite: this.tamanoPagina,
        desplazamiento: (this.pagina() - 1) * this.tamanoPagina,
      })
      .subscribe({
        next: ({ items, total }) => {
          this.canchas.set(items);
          this.total.set(total);
          this.cargando.set(false);
        },
        error: (error) => {
          this.error.set(toApiError(error).message);
          this.cargando.set(false);
        },
      });
  }

  protected irPagina(numero: number): void {
    if (numero < 1 || numero > this.totalPaginas() || numero === this.pagina()) {
      return;
    }
    this.pagina.set(numero);
    this.cargar();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected aplicar(): void {
    this.pagina.set(1);
    const { q, idDistrito, idDeporte, precioMax } = this.filtros.getRawValue();
    void this.router.navigate(['/canchas'], {
      queryParams: {
        q: q?.trim() || null,
        distrito: idDistrito,
        deporte: idDeporte,
        precioMax,
      },
    });
    this.cargar();
  }

  protected limpiar(): void {
    this.pagina.set(1);
    this.filtros.reset({ q: '', idDistrito: null, idDeporte: null, precioMax: null });
    void this.router.navigate(['/canchas']);
    this.cargar();
  }

  protected comparar(id: number): void {
    if (!this.comparacion.tiene(id) && this.comparacion.cantidad() >= 4) {
      return;
    }
    this.comparacion.toggle(id);
  }

  protected irAComparar(): void {
    void this.router.navigate(['/canchas/comparar'], {
      queryParams: { ids: this.comparacion.ids().join(',') },
    });
  }

  private async cargarCatalogos(): Promise<void> {
    try {
      const [distritos, deportes] = await Promise.all([
        firstValueFrom(this.api.distritos()),
        firstValueFrom(this.api.deportes()),
      ]);
      this.distritos.set(distritos);
      this.deportes.set(deportes);
    } catch {
      // los filtros quedan solo con texto/precio si falla el catalogo auxiliar
    }
  }

  private numero(valor: string | null): number | null {
    const n = Number(valor);
    return valor && Number.isFinite(n) ? n : null;
  }
}
