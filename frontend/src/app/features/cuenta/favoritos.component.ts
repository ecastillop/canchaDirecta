import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { FavoritosService } from '../../core/api/favoritos.service';
import { VenuesApi } from '../../core/api/venues.api';
import { CanchaDetalle } from '../../core/api/venues.models';
import { CdIconComponent } from '../../shared/ui/icon/cd-icon.component';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';

@Component({
  selector: 'app-favoritos',
  imports: [RouterLink, CurrencyPipe, DecimalPipe, CdEmptyStateComponent, CdIconComponent, CdSkeletonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hero-favoritos">
      <div class="cd-container">
        <p class="eyebrow">Tu selección</p>
        <h1>Canchas que <em>te gustan.</em></h1>
        <p>Guarda tus espacios favoritos y vuelve a ellos cuando quieras.</p>
      </div>
    </section>

    <div class="cd-container contenido">
      @if (cargando()) {
        <div class="grid">
          @for (i of [1, 2, 3]; track i) {
            <div class="tarjeta">
              <cd-skeleton height="190px" radius="0" />
              <div class="tarjeta__cuerpo">
                <cd-skeleton width="70%" height="24px" />
                <cd-skeleton width="45%" height="16px" />
              </div>
            </div>
          }
        </div>
      } @else if (!canchas().length) {
        <cd-empty-state
          title="Aún no tienes favoritos"
          message="Toca el corazón en una cancha para guardarla aquí."
        >
          <a routerLink="/canchas" class="cd-btn cd-btn--primary cd-btn--sm">Explorar espacios</a>
        </cd-empty-state>
      } @else {
        <div class="grid">
          @for (cancha of canchas(); track cancha.id) {
            <article class="tarjeta">
              <div class="tarjeta__media">
                @if (cancha.fotos.length) {
                  <img [src]="cancha.fotos[0].url" [alt]="'Foto de ' + cancha.nombre" />
                }
                <button
                  type="button"
                  class="quitar"
                  (click)="quitar(cancha.id)"
                  aria-label="Quitar de favoritos"
                >
                  ♥
                </button>
              </div>
              <a class="tarjeta__cuerpo" [routerLink]="['/canchas', cancha.id]">
                <div class="meta">
                  <span><cd-icon name="pin" [size]="13" /> {{ cancha.distrito.nombre }}</span>
                  <span class="rating">
                    <cd-icon name="star" [size]="13" />
                    {{ cancha.reputacion.total ? (cancha.reputacion.promedio | number: '1.1-1') : 'Nuevo' }}
                    @if (cancha.reputacion.total) {
                      <small>({{ cancha.reputacion.total }})</small>
                    }
                  </span>
                </div>
                <h2>{{ cancha.nombre }}</h2>
                <p class="cd-small cd-muted">{{ cancha.establecimiento }}</p>
                <div class="pie">
                  <span>Desde <strong>{{ cancha.precioBase | currency: 'PEN' : 'symbol-narrow' : '1.0-0' }}</strong> / hora</span>
                  <span class="flecha" aria-hidden="true">→</span>
                </div>
              </a>
            </article>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .hero-favoritos {
      padding: clamp(48px, 7vw, 80px) 0 clamp(40px, 5vw, 60px);
      background: var(--cd-color-inverse-bg);
      color: var(--cd-color-inverse-text);
    }
    .eyebrow {
      margin: 0 0 12px;
      color: var(--cd-color-accent);
      font-size: 11px;
      font-weight: 800;
      letter-spacing: 0.18em;
      text-transform: uppercase;
    }
    h1 {
      margin: 0;
      font-size: clamp(38px, 4.6vw, 60px);
      letter-spacing: -0.05em;
    }
    h1 em {
      color: var(--cd-color-accent);
      font-style: normal;
    }
    .hero-favoritos p:last-child {
      margin: 16px 0 0;
      color: var(--cd-color-inverse-muted);
    }
    .contenido {
      padding-block: 36px 64px;
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 22px;
    }
    .tarjeta {
      overflow: hidden;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-lg);
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-1);
    }
    .tarjeta__media {
      position: relative;
      height: 190px;
      background: linear-gradient(145deg, #1b6b54, #104737);
    }
    .tarjeta__media img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .quitar {
      position: absolute;
      top: 12px;
      right: 12px;
      display: grid;
      place-items: center;
      width: 34px;
      height: 34px;
      border: 0;
      border-radius: 9px;
      background: var(--cd-color-danger);
      color: #fff;
      font-size: 18px;
      cursor: pointer;
    }
    .tarjeta__cuerpo {
      display: grid;
      gap: 6px;
      padding: 16px 18px 18px;
      color: inherit;
      text-decoration: none;
    }
    .meta {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      color: var(--cd-color-text-muted);
      font-size: 11px;
    }
    .meta span {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
    .rating {
      color: var(--cd-color-warning);
      font-weight: 800;
    }
    .rating small {
      font-size: 9px;
    }
    h2 {
      margin: 0;
      font-size: 22px;
      letter-spacing: -0.03em;
    }
    .pie {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-top: 8px;
      padding-top: 10px;
      border-top: 1px solid var(--cd-color-border);
      color: var(--cd-color-text-muted);
      font-size: 12px;
    }
    .pie strong {
      color: var(--cd-color-text);
      font-size: 19px;
    }
    .flecha {
      display: grid;
      place-items: center;
      width: 30px;
      height: 30px;
      border-radius: 50%;
      background: var(--cd-color-accent);
      color: var(--cd-color-primary);
    }
    @media (max-width: 900px) {
      .grid {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class FavoritosComponent {
  private readonly venues = inject(VenuesApi);
  protected readonly favoritos = inject(FavoritosService);

  protected readonly canchas = signal<CanchaDetalle[]>([]);
  protected readonly cargando = signal(true);
  private cargandoIds: string | null = null;

  constructor() {
    effect(() => {
      const ids = [...this.favoritos.ids()].sort((a, b) => a - b).join(',');
      if (ids === this.cargandoIds) {
        return;
      }
      this.cargandoIds = ids;
      void this.cargar();
    });
  }

  protected async quitar(idCancha: number): Promise<void> {
    await this.favoritos.alternar(idCancha);
  }

  private async cargar(): Promise<void> {
    const ids = [...this.favoritos.ids()];
    if (!ids.length) {
      this.canchas.set([]);
      this.cargando.set(false);
      return;
    }
    this.cargando.set(true);
    const detalles = await Promise.all(
      ids.map((id) => firstValueFrom(this.venues.detalle(id)).catch(() => null)),
    );
    this.canchas.set(detalles.filter((detalle): detalle is CanchaDetalle => detalle !== null));
    this.cargando.set(false);
  }
}
