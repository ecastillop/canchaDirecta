import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { VenuesApi } from '../../core/api/venues.api';
import { FichaEspacio } from '../../core/api/venues.models';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { CdIconComponent } from '../../shared/ui/icon/cd-icon.component';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';

const FOTO_FALLBACK =
  'data:image/svg+xml;charset=UTF-8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 600"><rect width="800" height="600" fill="#e8edf4"/><path d="M120 420l160-180 120 135 90-105 190 220z" fill="#b7c3d3"/><circle cx="600" cy="150" r="55" fill="#b7c3d3"/><text x="400" y="520" text-anchor="middle" font-family="Segoe UI,Arial" font-size="28" fill="#5b6b80">Foto no disponible</text></svg>',
  );

/** Ficha publica del espacio deportivo: datos, servicios, fotos y sus canchas. */
@Component({
  selector: 'app-espacio-detalle',
  imports: [
    RouterLink,
    CurrencyPipe,
    DecimalPipe,
    CdButtonDirective,
    CdIconComponent,
    CdSkeletonComponent,
    CdEmptyStateComponent,
    CdErrorStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cd-container cd-page">
      @if (cargando()) {
        <div class="cd-stack">
          <cd-skeleton height="220px" radius="16px" />
          <cd-skeleton width="50%" height="28px" />
          <cd-skeleton width="80%" height="16px" />
        </div>
      } @else if (error()) {
        <cd-error-state [message]="error()!" (retry)="cargar()" />
      } @else if (espacio(); as e) {
        <nav class="migas cd-small cd-muted" aria-label="Ruta de navegación">
          <a routerLink="/canchas">Canchas</a> <span aria-hidden="true">/</span> <span>{{ e.nombre }}</span>
        </nav>

        <header class="cabecera">
          <div class="cabecera__texto">
            <div class="cd-row cabecera__meta">
              <span class="cd-badge cd-badge--suave">Espacio deportivo</span>
              <span class="cd-badge" [class.cd-badge--success]="e.estado === 'ACTIVO'">
                {{ e.estado === 'ACTIVO' ? 'Abierto' : 'No disponible' }}
              </span>
              <span class="cd-badge">{{ e.canchas.length }} {{ e.canchas.length === 1 ? 'cancha' : 'canchas' }}</span>
            </div>
            <h1 class="cd-page__title">{{ e.nombre }}</h1>
            <p class="ubicacion">
              <cd-icon name="pin" [size]="15" />
              {{ e.distrito.nombre }}, {{ e.distrito.provincia }}
            </p>
            <p class="cd-small cd-muted">{{ e.direccion }}@if (e.referencia) { · {{ e.referencia }} }</p>
          </div>
          <div class="puntuacion" [attr.aria-label]="'Reputación ' + e.reputacion.promedio + ' de 5'">
            <span class="puntuacion__valor"><cd-icon name="star" [size]="17" /> {{ e.reputacion.promedio | number: '1.1-1' }}</span>
            <span class="cd-xs cd-muted">
              {{ e.reputacion.total ? e.reputacion.total + ' calificaciones' : 'Nuevo en CanchaDirecta' }}
            </span>
          </div>
        </header>

        @if (e.fotos.length) {
          <section
            class="galeria"
            aria-label="Fotos del espacio"
            aria-roledescription="carrusel"
            (keydown.arrowleft)="anteriorFoto()"
            (keydown.arrowright)="siguienteFoto()"
          >
            <div class="galeria__visor">
              @if (fotoActual(); as foto) {
                <img
                  class="galeria__principal"
                  [src]="foto.url"
                  [alt]="'Foto de ' + e.nombre"
                  (error)="usarFallback($event)"
                />
              }
              @if (e.fotos.length > 1) {
                <button
                  type="button"
                  class="galeria__nav galeria__nav--prev"
                  aria-label="Foto anterior"
                  (click)="anteriorFoto()"
                >
                  <cd-icon name="arrow" [size]="20" />
                </button>
                <button
                  type="button"
                  class="galeria__nav galeria__nav--next"
                  aria-label="Foto siguiente"
                  (click)="siguienteFoto()"
                >
                  <cd-icon name="arrow" [size]="20" />
                </button>
                <span class="galeria__contador" aria-live="polite">
                  {{ indiceFoto() + 1 }} / {{ e.fotos.length }}
                </span>
              }
            </div>
            @if (e.fotos.length > 1) {
              <div class="galeria__tira" role="group" aria-label="Miniaturas de fotos">
                @for (foto of e.fotos; track foto.id; let i = $index) {
                  <button
                    type="button"
                    class="galeria__mini"
                    [class.activa]="i === indiceFoto()"
                    [attr.aria-current]="i === indiceFoto() ? 'true' : null"
                    [attr.aria-label]="'Ver foto ' + (i + 1) + ' de ' + e.fotos.length"
                    (click)="irAFoto(i)"
                  >
                    <img [src]="foto.url" alt="" loading="lazy" (error)="usarFallback($event)" />
                  </button>
                }
              </div>
            }
          </section>
        }

        <div class="columnas">
          <article class="contenido">
            <section class="bloque">
              <h2 class="seccion">Sobre el espacio</h2>
              <p class="cd-small cd-muted">
                {{ e.nombre }} reúne {{ e.canchas.length }}
                {{ e.canchas.length === 1 ? 'cancha disponible' : 'canchas disponibles' }} en
                {{ e.distrito.nombre }}. Elige la que mejor se adapte a tu partido.
              </p>
              <dl class="hechos">
                <div>
                  <dt>Distrito</dt>
                  <dd>{{ e.distrito.nombre }}</dd>
                </div>
                <div>
                  <dt>Dirección</dt>
                  <dd>{{ e.direccion }}</dd>
                </div>
                <div>
                  <dt>Canchas</dt>
                  <dd>{{ e.canchas.length }}</dd>
                </div>
                <div>
                  <dt>Desde</dt>
                  <dd>{{ e.precioDesde | currency: 'PEN' : 'symbol-narrow' : '1.0-0' }} / hora</dd>
                </div>
              </dl>
            </section>

            <section class="bloque">
              <h2 class="seccion">Servicios del espacio</h2>
              @if (e.servicios.length) {
                <ul class="servicios">
                  @for (servicio of e.servicios; track servicio.nombre) {
                    <li>
                      <span class="check" aria-hidden="true"><cd-icon name="check" [size]="12" /></span>
                      <strong>{{ servicio.nombre }}</strong>
                    </li>
                  }
                </ul>
              } @else {
                <p class="cd-small cd-muted">El propietario todavía no registró servicios para este espacio.</p>
              }
            </section>

            <section class="bloque" aria-labelledby="canchas-titulo">
              <h2 id="canchas-titulo" class="seccion">Canchas del espacio</h2>
              @if (e.canchas.length === 0) {
                <cd-empty-state
                  title="Sin canchas publicadas"
                  message="Este espacio todavía no tiene canchas disponibles para reservar."
                >
                  <a routerLink="/canchas" cdButton variant="secondary" size="sm">Ver otras canchas</a>
                </cd-empty-state>
              } @else {
                <div class="grid-canchas">
                  @for (cancha of e.canchas; track cancha.id) {
                    <article class="cancha" [class.cancha--inactiva]="cancha.estado !== 'ACTIVA'">
                      <div class="cancha__arte">
                        @if (cancha.fotoPrincipal) {
                          <img
                            [src]="cancha.fotoPrincipal"
                            [alt]="'Foto de ' + cancha.nombre"
                            loading="lazy"
                            (error)="usarFallback($event)"
                          />
                        } @else {
                          <span class="cancha__sin-foto" aria-hidden="true">{{ cancha.nombre.charAt(0) }}</span>
                        }
                        <span class="cancha__deporte">{{ cancha.deportes[0]?.nombre ?? 'Cancha' }}</span>
                      </div>
                      <div class="cancha__cuerpo">
                        <h3>{{ cancha.nombre }}</h3>
                        <p class="cd-small cd-muted">
                          Para {{ cancha.capacidad }} personas
                          @if (cancha.deportes.length > 1) {
                            · {{ cancha.deportes.length }} deportes
                          }
                        </p>
                        <div class="cancha__meta cd-xs cd-muted">
                          <span class="rating">
                            <cd-icon name="star" [size]="12" />
                            {{ cancha.reputacion.total ? (cancha.reputacion.promedio | number: '1.1-1') : 'Nuevo' }}
                          </span>
                          <span>{{ cancha.estado === 'ACTIVA' ? 'Disponible' : cancha.estado }}</span>
                        </div>
                        <div class="cancha__pie">
                          <span class="precio">
                            <strong>{{ cancha.precioBase | currency: 'PEN' : 'symbol-narrow' : '1.0-0' }}</strong>
                            <small>/ hora</small>
                          </span>
                          <a
                            [routerLink]="['/canchas', cancha.id]"
                            cdButton
                            variant="secondary"
                            size="sm"
                            [attr.aria-label]="'Ver detalle y horarios de ' + cancha.nombre"
                          >
                            Ver horarios <span aria-hidden="true">→</span>
                          </a>
                        </div>
                      </div>
                    </article>
                  }
                </div>
              }
            </section>
          </article>

          <aside class="lateral">
            <div class="reserva">
              <p class="cd-xs cd-muted">Precio por hora desde</p>
              <p class="reserva__precio">
                @if (e.precioDesde != null) {
                  {{ e.precioDesde | currency: 'PEN' : 'symbol-narrow' : '1.0-0' }}
                } @else {
                  Consultar
                }
              </p>
              @if (primeraCanchaActiva(); as cancha) {
                <a
                  [routerLink]="['/canchas', cancha.id]"
                  cdButton
                  variant="primary"
                  class="cd-btn--block"
                >
                  Reservar en {{ cancha.nombre }}
                </a>
              } @else {
                <a routerLink="/canchas" cdButton variant="primary" class="cd-btn--block">
                  Buscar otra cancha
                </a>
              }
              <ul class="ventajas cd-small cd-muted">
                <li><cd-icon name="check" [size]="13" /> Disponibilidad semanal en cada cancha</li>
                <li><cd-icon name="check" [size]="13" /> Reserva y pago en línea</li>
                <li><cd-icon name="check" [size]="13" /> Servicios compartidos del espacio</li>
              </ul>
            </div>
          </aside>
        </div>
      }
    </div>
  `,
  styles: `
    .migas {
      display: flex;
      gap: 8px;
      margin-bottom: 18px;
    }
    .migas a {
      color: var(--cd-color-text-muted);
      text-decoration: none;
    }
    .migas a:hover {
      color: var(--cd-color-primary);
    }
    .cabecera {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 24px;
      margin-bottom: 24px;
    }
    .cabecera__meta {
      flex-wrap: wrap;
      margin-bottom: 10px;
    }
    .ubicacion {
      display: flex;
      align-items: center;
      gap: 6px;
      margin: 0 0 4px;
      color: var(--cd-color-text-muted);
      font-size: 14px;
    }
    .puntuacion {
      display: grid;
      justify-items: end;
      gap: 2px;
      text-align: right;
      white-space: nowrap;
    }
    .puntuacion__valor {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      font-size: 18px;
      font-weight: 700;
    }
    .galeria {
      display: grid;
      gap: 10px;
      margin-bottom: 30px;
    }
    .galeria__visor {
      position: relative;
      overflow: hidden;
      border-radius: var(--cd-radius-lg);
      background: var(--cd-color-surface-alt);
    }
    .galeria__principal {
      width: 100%;
      height: clamp(280px, 38vw, 520px);
      object-fit: cover;
      display: block;
    }
    .galeria__nav {
      position: absolute;
      top: 50%;
      transform: translateY(-50%);
      width: 44px;
      height: 44px;
      display: grid;
      place-items: center;
      border: 0;
      border-radius: 50%;
      background: color-mix(in srgb, var(--cd-color-primary) 72%, transparent);
      color: var(--cd-color-on-primary);
      font-size: 26px;
      line-height: 1;
      cursor: pointer;
      transition: background var(--cd-duration-fast);
    }
    .galeria__nav:hover {
      background: var(--cd-color-primary);
    }
    .galeria__nav:focus-visible {
      outline: 2px solid var(--cd-color-teal);
      outline-offset: 2px;
    }
    .galeria__nav--prev {
      left: 14px;
    }
    .galeria__nav--prev cd-icon {
      transform: rotate(180deg);
    }
    .galeria__nav--next {
      right: 14px;
    }
    .galeria__contador {
      position: absolute;
      right: 14px;
      bottom: 14px;
      padding: 4px 11px;
      border-radius: var(--cd-radius-pill);
      background: color-mix(in srgb, var(--cd-color-primary) 72%, transparent);
      color: var(--cd-color-on-primary);
      font-size: 12px;
      font-weight: 700;
    }
    .galeria__tira {
      display: flex;
      gap: 8px;
      overflow-x: auto;
      padding-bottom: 4px;
      scrollbar-width: thin;
    }
    .galeria__mini {
      flex: 0 0 104px;
      padding: 0;
      border: 2px solid transparent;
      border-radius: var(--cd-radius-sm);
      overflow: hidden;
      background: var(--cd-color-surface-alt);
      cursor: pointer;
      opacity: 0.72;
      transition: opacity var(--cd-duration-fast);
    }
    .galeria__mini:hover,
    .galeria__mini.activa {
      opacity: 1;
    }
    .galeria__mini.activa {
      border-color: var(--cd-color-teal);
    }
    .galeria__mini:focus-visible {
      outline: 2px solid var(--cd-color-teal);
      outline-offset: 2px;
    }
    .galeria__mini img {
      width: 100%;
      height: 66px;
      object-fit: cover;
      display: block;
    }
    .cd-badge--suave {
      background: var(--cd-color-surface-alt);
      color: var(--cd-color-teal);
    }
    .columnas {
      display: grid;
      grid-template-columns: minmax(0, 1fr) 320px;
      gap: 28px;
      align-items: start;
    }
    .contenido {
      display: grid;
      gap: 28px;
    }
    .bloque {
      display: grid;
      gap: 14px;
    }
    .seccion {
      margin: 0;
      font-size: 22px;
    }
    .hechos {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 12px;
      margin: 0;
    }
    .hechos div {
      padding: 12px 14px;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-surface);
    }
    .hechos dt {
      color: var(--cd-color-text-muted);
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }
    .hechos dd {
      margin: 4px 0 0;
      font-weight: 600;
    }
    .servicios {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .servicios li {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 8px 12px;
      border: 1px solid var(--cd-color-border);
      border-radius: 999px;
      background: var(--cd-color-surface);
      font-size: 13px;
    }
    .check {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 18px;
      height: 18px;
      border-radius: 50%;
      background: var(--cd-color-accent);
      color: var(--cd-color-primary);
    }
    .grid-canchas {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 16px;
    }
    .cancha {
      display: grid;
      overflow: hidden;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-lg);
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-1);
    }
    .cancha--inactiva {
      opacity: 0.72;
    }
    .cancha__arte {
      position: relative;
      aspect-ratio: 16 / 9;
      background: var(--cd-color-surface-alt);
    }
    .cancha__arte img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .cancha__sin-foto {
      display: grid;
      place-items: center;
      width: 100%;
      height: 100%;
      color: var(--cd-color-primary);
      font-size: 42px;
      font-weight: 800;
      text-transform: uppercase;
    }
    .cancha__deporte {
      position: absolute;
      top: 10px;
      left: 10px;
      padding: 4px 10px;
      border-radius: 999px;
      background: var(--cd-color-inverse-bg);
      color: var(--cd-color-inverse-text);
      font-size: 10px;
      font-weight: 800;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }
    .cancha__cuerpo {
      display: grid;
      gap: 6px;
      padding: 16px;
    }
    .cancha h3 {
      margin: 0;
      font-size: 18px;
    }
    .cancha__meta {
      display: flex;
      justify-content: space-between;
      gap: 10px;
    }
    .rating {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
    .cancha__pie {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      margin-top: 6px;
    }
    .precio strong {
      font-size: 18px;
    }
    .precio small {
      color: var(--cd-color-text-muted);
      font-size: 11px;
    }
    .cancha__pie a {
      text-decoration: none;
    }
    .lateral {
      position: sticky;
      top: 90px;
    }
    .reserva {
      display: grid;
      gap: 10px;
      padding: 20px;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-lg);
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-1);
    }
    .reserva p {
      margin: 0;
    }
    .reserva__precio {
      font-size: 28px;
      font-weight: 800;
      letter-spacing: -0.03em;
    }
    .ventajas {
      display: grid;
      gap: 8px;
      margin: 8px 0 0;
      padding: 0;
      list-style: none;
    }
    .ventajas li {
      display: flex;
      align-items: flex-start;
      gap: 7px;
    }
    @media (max-width: 960px) {
      .columnas {
        grid-template-columns: 1fr;
      }
      .lateral {
        position: static;
      }
    }
    @media (max-width: 640px) {
      .cabecera {
        flex-direction: column;
      }
      .puntuacion {
        justify-items: start;
        text-align: left;
      }
      .grid-canchas,
      .hechos {
        grid-template-columns: 1fr;
      }
      .galeria__nav {
        width: 38px;
        height: 38px;
        font-size: 22px;
      }
      .galeria__mini {
        flex-basis: 84px;
      }
      .galeria__mini img {
        height: 54px;
      }
    }
  `,
})
export class EspacioDetalleComponent implements OnInit {
  private readonly api = inject(VenuesApi);

  readonly id = input.required<string>();

  protected readonly espacio = signal<FichaEspacio | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly primeraCanchaActiva = computed(
    () => this.espacio()?.canchas.find((cancha) => cancha.estado === 'ACTIVA') ?? null,
  );
  protected readonly fotos = computed(() => this.espacio()?.fotos ?? []);
  protected readonly indiceFoto = signal(0);
  protected readonly fotoActual = computed(() => this.fotos()[this.indiceFoto()] ?? null);

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    const id = Number(this.id());
    if (!Number.isFinite(id)) {
      this.error.set('Espacio inválido.');
      this.cargando.set(false);
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    void firstValueFrom(this.api.fichaEspacio(id))
      .then((espacio) => {
        this.espacio.set(espacio);
        this.indiceFoto.set(0);
        this.cargando.set(false);
      })
      .catch((error) => {
        this.error.set(toApiError(error).message);
        this.cargando.set(false);
      });
  }

  protected irAFoto(indice: number): void {
    const total = this.fotos().length;
    if (!total) {
      return;
    }
    this.indiceFoto.set((indice + total) % total);
  }

  protected anteriorFoto(): void {
    this.irAFoto(this.indiceFoto() - 1);
  }

  protected siguienteFoto(): void {
    this.irAFoto(this.indiceFoto() + 1);
  }

  /** Evita imágenes rotas cuando una URL externa deja de estar disponible. */
  protected usarFallback(event: Event): void {
    const img = event.target as HTMLImageElement;
    if (img.dataset['fallback'] === 'true') {
      return;
    }
    img.dataset['fallback'] = 'true';
    img.src = FOTO_FALLBACK;
  }
}
