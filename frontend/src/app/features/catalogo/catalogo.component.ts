import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ComparacionService } from '../../core/api/comparacion.service';
import { FavoritosService } from '../../core/api/favoritos.service';
import { VenuesApi } from '../../core/api/venues.api';
import { AuthStore } from '../../core/auth/auth.store';
import { Cancha, Distrito, Item } from '../../core/api/venues.models';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { CdIconComponent } from '../../shared/ui/icon/cd-icon.component';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { CdPaginatorComponent } from '../../shared/ui/pagination/cd-paginator.component';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';

@Component({
  selector: 'app-catalogo',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    CurrencyPipe,
    DecimalPipe,
    CdButtonDirective,
    CdInputDirective,
    CdSkeletonComponent,
    CdEmptyStateComponent,
    CdErrorStateComponent,
    CdIconComponent,
    CdPaginatorComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hero-catalogo">
      <div class="cd-container">
        <p class="eyebrow">Explora · Reserva · Juega</p>
        <h1>Tu próxima cancha<br /><em>te está esperando.</em></h1>
        <p class="hero-catalogo__bajada">
          Compara espacios verificados, encuentra tu horario ideal y sal a jugar.
        </p>
      </div>
    </section>

    <div class="cd-container catalogo">
      <aside class="panel-filtros" aria-label="Filtros de búsqueda">
        <div class="panel-filtros__cabecera">
          <h2>Filtros</h2>
          <button cdButton variant="ghost" size="sm" type="button" (click)="limpiar()">Limpiar</button>
        </div>
        <form class="cd-stack" [formGroup]="filtros" (ngSubmit)="aplicar()" aria-label="Filtros de canchas">
          <label class="cd-field">
            <span class="cd-field__label">Buscar por nombre</span>
            <input cdInput formControlName="q" type="search" placeholder="Nombre o zona" />
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
            <span class="cd-field__label">Precio máximo por hora (S/)</span>
            <input cdInput type="number" min="0" step="5" formControlName="precioMax" placeholder="Sin límite" />
          </label>
          <button cdButton type="submit" class="cd-btn--block">Aplicar filtros</button>
        </form>
        <div class="panel-filtros__tip">
          <strong>Espacios verificados</strong>
          <p>Consulta los detalles y reserva con un proceso guiado.</p>
        </div>
      </aside>

      <section class="resultados" aria-label="Resultados de canchas">
        <div class="resultados__top">
          <div>
            <small>Resultados disponibles</small>
            <h2>{{ total() }} espacios encontrados</h2>
          </div>
          <p class="cd-small cd-muted" role="status">
            @if (total() > 0) {
              Mostrando {{ desde() }}–{{ hasta() }} de {{ total() }}
            } @else {
              Sin resultados
            }
          </p>
        </div>

        @if (cargando()) {
          <div class="grid-canchas" aria-busy="true" aria-label="Cargando canchas">
            @for (i of [1, 2, 3, 4]; track i) {
              <div class="tarjeta-cancha tarjeta-cancha--cargando" aria-hidden="true">
                <cd-skeleton height="190px" radius="0" />
                <div class="tarjeta-cancha__cuerpo">
                  <cd-skeleton width="70%" height="22px" />
                  <cd-skeleton width="45%" height="16px" />
                </div>
              </div>
            }
          </div>
        } @else if (error()) {
          <cd-error-state [message]="error()!" (retry)="cargar()" />
        } @else if (canchas().length === 0) {
          <cd-empty-state
            title="No encontramos canchas"
            message="Prueba con otro distrito, deporte o sin límite de precio."
          >
            <button cdButton variant="secondary" type="button" (click)="limpiar()">Quitar filtros</button>
          </cd-empty-state>
        } @else {
          <div class="grid-canchas">
            @for (cancha of canchas(); track cancha.id) {
              <article class="tarjeta-cancha">
                <div [class]="'tarjeta-cancha__arte tarjeta-cancha__arte--' + claseColor(cancha)">
                  @if (cancha.fotoPrincipal) {
                    <img class="tarjeta-cancha__foto" [src]="cancha.fotoPrincipal" [alt]="'Foto de ' + cancha.nombre" loading="lazy" />
                  }
                  <span class="tarjeta-cancha__deporte">{{ cancha.deportes[0] ?? 'Cancha' }}</span>
                  <span class="tarjeta-cancha__iniciales" aria-hidden="true">{{ iniciales(cancha.nombre) }}</span>
                  <span class="tarjeta-cancha__linea" aria-hidden="true"></span>
                  <button
                    type="button"
                    class="tarjeta-cancha__corazon"
                    [class.activo]="favoritos.tiene(cancha.id)"
                    (click)="alternarFavorito(cancha, $event)"
                    [attr.aria-label]="favoritos.tiene(cancha.id) ? 'Quitar de favoritos' : 'Agregar a favoritos'"
                    [attr.aria-pressed]="favoritos.tiene(cancha.id)"
                  >
                    {{ favoritos.tiene(cancha.id) ? '♥' : '♡' }}
                  </button>
                </div>
                <div class="tarjeta-cancha__cuerpo">
                  <div class="tarjeta-cancha__meta">
                    <span><cd-icon name="pin" [size]="13" /> {{ cancha.distrito }}</span>
                    <span class="rating">
                      <cd-icon name="star" [size]="13" />
                      {{ cancha.totalCalificaciones ? (cancha.promedioCalificacion | number: '1.1-1') : 'Nuevo' }}
                      @if (cancha.totalCalificaciones) {
                        <small>({{ cancha.totalCalificaciones }})</small>
                      }
                    </span>
                  </div>
                  <h3>{{ cancha.nombre }}</h3>
                  <p class="cd-small cd-muted">Capacidad {{ cancha.capacidad }} · {{ cancha.establecimiento }}</p>
                  <div class="tarjeta-cancha__pie">
                    <span class="precio">
                      <small>Desde</small>
                      <strong>{{ cancha.precioBase | currency: 'PEN' : 'symbol-narrow' : '1.0-0' }}</strong>
                      <small>/ hora</small>
                    </span>
                    <div class="tarjeta-cancha__acciones">
                      <button
                        type="button"
                        class="comparar-btn"
                        [class.activo]="comparacion.tiene(cancha.id)"
                        (click)="comparar(cancha.id)"
                        [attr.aria-pressed]="comparacion.tiene(cancha.id)"
                      >
                        {{ comparacion.tiene(cancha.id) ? 'Comparando' : 'Comparar' }}
                      </button>
                      <a
                        [routerLink]="['/canchas', cancha.id]"
                        class="cd-btn cd-btn--secondary cd-btn--sm"
                        [attr.aria-label]="'Ver detalle y horarios de ' + cancha.nombre"
                      >
                        Ver horarios <span aria-hidden="true">→</span>
                      </a>
                    </div>
                  </div>
                  <small class="cd-muted">4 horas: {{ cancha.precioBase * 4 | currency: 'PEN' : 'symbol-narrow' : '1.0-0' }}</small>
                </div>
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
      </section>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    .hero-catalogo {
      position: relative;
      overflow: hidden;
      padding: clamp(64px, 8vw, 100px) 0 clamp(58px, 7vw, 86px);
      background: var(--cd-color-inverse-bg);
      color: var(--cd-color-inverse-text);
    }
    .hero-catalogo::after {
      content: '';
      position: absolute;
      right: -6%;
      top: -30%;
      width: 52vw;
      height: 52vw;
      border: 1px solid color-mix(in srgb, var(--cd-color-inverse-text) 12%, transparent);
      border-radius: 50%;
      box-shadow:
        0 0 0 80px color-mix(in srgb, var(--cd-color-inverse-text) 4%, transparent),
        0 0 0 160px color-mix(in srgb, var(--cd-color-inverse-text) 3%, transparent);
      pointer-events: none;
    }
    .hero-catalogo .cd-container {
      position: relative;
      z-index: 1;
    }
    .eyebrow {
      margin: 0 0 16px;
      color: var(--cd-color-accent);
      font-size: 11px;
      font-weight: 800;
      letter-spacing: 0.2em;
      text-transform: uppercase;
    }
    .hero-catalogo h1 {
      margin: 0;
      font-size: clamp(44px, 5.6vw, 74px);
      line-height: 1;
      letter-spacing: -0.05em;
    }
    .hero-catalogo h1 em {
      color: var(--cd-color-accent);
      font-style: normal;
    }
    .hero-catalogo__bajada {
      max-width: 44ch;
      margin: 22px 0 0;
      color: var(--cd-color-inverse-muted);
      font-size: clamp(15px, 1.4vw, 18px);
    }
    .catalogo {
      display: grid;
      grid-template-columns: 280px minmax(0, 1fr);
      gap: clamp(24px, 3vw, 38px);
      padding-block: clamp(36px, 5vw, 54px) var(--cd-space-8);
    }
    .panel-filtros {
      position: sticky;
      top: calc(var(--cd-topbar-height) + 20px);
      align-self: start;
      padding: 22px;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-lg);
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-1);
    }
    .panel-filtros__cabecera {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      margin-bottom: 18px;
    }
    .panel-filtros h2 {
      margin: 0;
      font-size: 22px;
    }
    .panel-filtros__tip {
      margin-top: 22px;
      padding: 16px;
      border-radius: var(--cd-radius-md);
      background: var(--cd-color-surface-alt);
    }
    .panel-filtros__tip strong {
      display: block;
      font-size: 13px;
    }
    .panel-filtros__tip p {
      margin: 6px 0 0;
      color: var(--cd-color-text-muted);
      font-size: 12px;
      line-height: 1.5;
    }
    .resultados {
      min-width: 0;
    }
    .resultados__top {
      display: flex;
      align-items: end;
      justify-content: space-between;
      gap: 16px;
      flex-wrap: wrap;
      margin-bottom: 22px;
    }
    .resultados__top small {
      color: var(--cd-color-text-muted);
      font-size: 10px;
      font-weight: 800;
      letter-spacing: 0.16em;
      text-transform: uppercase;
    }
    .resultados__top h2 {
      margin: 4px 0 0;
      font-size: clamp(26px, 2.8vw, 36px);
    }
    .grid-canchas {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 22px;
    }
    .tarjeta-cancha {
      display: flex;
      flex-direction: column;
      overflow: hidden;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-lg);
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-1);
      transition:
        transform var(--cd-duration-base),
        box-shadow var(--cd-duration-base);
    }
    .tarjeta-cancha:hover {
      transform: translateY(-4px);
      box-shadow: var(--cd-shadow-2);
    }
    .tarjeta-cancha--cargando {
      min-height: 340px;
    }
    .tarjeta-cancha__arte {
      position: relative;
      height: 190px;
      overflow: hidden;
      background:
        repeating-linear-gradient(120deg, rgba(255, 255, 255, 0.08) 0 2px, transparent 2px 16px),
        linear-gradient(145deg, #1b6b54, #0d3d30);
    }
    .tarjeta-cancha__arte--azul {
      background:
        repeating-linear-gradient(120deg, rgba(255, 255, 255, 0.08) 0 2px, transparent 2px 16px),
        linear-gradient(145deg, #2c5f9e, #163a63);
    }
    .tarjeta-cancha__arte--naranja {
      background:
        repeating-linear-gradient(120deg, rgba(255, 255, 255, 0.08) 0 2px, transparent 2px 16px),
        linear-gradient(145deg, #b66a2e, #6d3a15);
    }
    .tarjeta-cancha__arte--rosa {
      background:
        repeating-linear-gradient(120deg, rgba(255, 255, 255, 0.08) 0 2px, transparent 2px 16px),
        linear-gradient(145deg, #9b5064, #5c2938);
    }
    .tarjeta-cancha__deporte {
      position: absolute;
      left: 14px;
      bottom: 14px;
      z-index: 2;
      padding: 5px 9px;
      border-radius: 7px;
      background: var(--cd-color-surface);
      color: var(--cd-color-text);
      font-size: 10px;
      font-weight: 800;
    }
    .tarjeta-cancha__iniciales {
      position: absolute;
      right: 16px;
      bottom: -14px;
      color: rgba(255, 255, 255, 0.16);
      font-family: var(--cd-font-display);
      font-size: 108px;
      font-weight: 900;
      line-height: 1;
      letter-spacing: -0.08em;
    }
    .tarjeta-cancha__foto {
      position: absolute;
      inset: 0;
      z-index: 1;
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .tarjeta-cancha__linea {
      position: absolute;
      inset: 14% 12%;
      border: 2px solid rgba(255, 255, 255, 0.42);
      border-radius: 50%;
      transform: rotate(-8deg);
    }
    .tarjeta-cancha__corazon {
      position: absolute;
      top: 12px;
      right: 12px;
      z-index: 3;
      display: grid;
      place-items: center;
      width: 34px;
      height: 34px;
      border: 0;
      border-radius: 9px;
      background: color-mix(in srgb, var(--cd-color-surface) 92%, transparent);
      color: var(--cd-color-danger);
      font-size: 18px;
      cursor: pointer;
    }
    .tarjeta-cancha__corazon.activo {
      background: var(--cd-color-danger);
      color: #fff;
    }
    .tarjeta-cancha__acciones {
      display: flex;
      align-items: center;
      gap: 6px;
      flex-wrap: wrap;
    }
    .comparar-btn {
      padding: 6px 9px;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-pill);
      background: var(--cd-color-surface);
      color: var(--cd-color-text-muted);
      font-size: 10px;
      font-weight: 800;
      cursor: pointer;
    }
    .comparar-btn.activo {
      border-color: var(--cd-color-teal, #167a70);
      color: var(--cd-color-teal, #167a70);
      background: color-mix(in srgb, var(--cd-color-teal, #167a70) 10%, transparent);
    }
    .rating small {
      font-size: 9px;
    }
    .tarjeta-cancha__cuerpo {
      display: grid;
      gap: 8px;
      padding: 18px;
    }
    .tarjeta-cancha__meta {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      color: var(--cd-color-text-muted);
      font-size: 11px;
    }
    .tarjeta-cancha__meta span {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
    .tarjeta-cancha h3 {
      margin: 0;
      font-size: 23px;
      letter-spacing: -0.035em;
    }
    .tarjeta-cancha__pie {
      display: flex;
      align-items: end;
      justify-content: space-between;
      gap: 12px;
      padding-top: 12px;
      border-top: 1px solid var(--cd-color-border);
    }
    .precio {
      display: inline-flex;
      align-items: baseline;
      gap: 4px;
      color: var(--cd-color-text-muted);
    }
    .precio strong {
      color: var(--cd-color-text);
      font-family: var(--cd-font-display);
      font-size: 25px;
      letter-spacing: -0.04em;
    }
    .precio small {
      font-size: 10px;
    }
    .barra-comparar {
      position: sticky;
      bottom: 0;
      z-index: 50;
      border-top: 1px solid var(--cd-color-border);
      background: color-mix(in srgb, var(--cd-color-surface) 96%, transparent);
      backdrop-filter: blur(12px);
      box-shadow: 0 -10px 40px rgba(18, 37, 37, 0.08);
      padding-block: 12px;
    }
    .barra-comparar__inner {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      flex-wrap: wrap;
    }
    @media (max-width: 1100px) {
      .catalogo {
        grid-template-columns: 250px minmax(0, 1fr);
      }
    }
    @media (max-width: 900px) {
      .catalogo {
        grid-template-columns: 1fr;
      }
      .panel-filtros {
        position: static;
      }
      .grid-canchas {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }
    @media (max-width: 620px) {
      .grid-canchas {
        grid-template-columns: 1fr;
      }
      .hero-catalogo h1 {
        font-size: clamp(38px, 12vw, 52px);
      }
      .tarjeta-cancha__pie {
        align-items: start;
        flex-direction: column;
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
  protected readonly favoritos = inject(FavoritosService);
  protected readonly auth = inject(AuthStore);

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
      idDistrito: this.numero(params.get('idDistrito')) ?? this.numero(params.get('distrito')),
      idDeporte: this.numero(params.get('idDeporte')) ?? this.numero(params.get('deporte')),
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

  protected async alternarFavorito(cancha: Cancha, event: Event): Promise<void> {
    event.preventDefault();
    event.stopPropagation();
    if (!this.auth.isAuthenticated()) {
      void this.router.navigate(['/auth/login'], { queryParams: { redirect: this.router.url } });
      return;
    }
    await this.favoritos.alternar(cancha.id);
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

  protected claseColor(cancha: { id: number; nombre: string }): string {
    const colores = ['verde', 'azul', 'naranja', 'rosa'];
    const base = cancha.nombre.split('').reduce((suma, letra) => suma + letra.charCodeAt(0), cancha.id);
    return colores[base % colores.length];
  }

  protected iniciales(nombre: string): string {
    return nombre
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((parte) => parte.charAt(0).toUpperCase())
      .join('');
  }

  private numero(valor: string | null): number | null {
    const n = Number(valor);
    return valor && Number.isFinite(n) ? n : null;
  }
}
