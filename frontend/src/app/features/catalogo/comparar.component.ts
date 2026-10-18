import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ComparacionService } from '../../core/api/comparacion.service';
import { VenuesApi } from '../../core/api/venues.api';
import { CanchaDetalle } from '../../core/api/venues.models';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';

@Component({
  selector: 'app-comparar',
  imports: [
    RouterLink,
    CurrencyPipe,
    CdButtonDirective,
    CdSkeletonComponent,
    CdEmptyStateComponent,
    CdErrorStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cd-container cd-page">
      <header class="cd-page__header">
        <h1 class="cd-page__title">Comparar canchas</h1>
        <p class="cd-muted">Compara precio, capacidad, distrito y características de hasta 4 canchas.</p>
      </header>

      @if (cargando()) {
        <cd-skeleton height="260px" radius="12px" />
      } @else if (error()) {
        <cd-error-state [message]="error()!" (retry)="cargar()" />
      } @else if (canchas().length < 2) {
        <cd-empty-state
          title="Selecciona al menos 2 canchas"
          message="Marca canchas desde el catálogo o desde el detalle de cada cancha."
        >
          <a routerLink="/canchas" class="cd-btn cd-btn--primary">Ir al catálogo</a>
        </cd-empty-state>
      } @else {
        <div class="tabla-scroll">
          <table class="tabla">
            <caption class="cd-visually-hidden">Comparacion de canchas seleccionadas</caption>
            <thead>
              <tr>
                <th scope="col">Atributo</th>
                @for (cancha of canchas(); track cancha.id) {
                  <th scope="col">
                    <a [routerLink]="['/canchas', cancha.id]">{{ cancha.nombre }}</a>
                    <button
                      type="button"
                      class="quitar"
                      [attr.aria-label]="'Quitar ' + cancha.nombre + ' de la comparacion'"
                      (click)="quitar(cancha.id)"
                    >
                      ×
                    </button>
                  </th>
                }
              </tr>
            </thead>
            <tbody>
              <tr>
                <th scope="row">Precio por hora</th>
                @for (cancha of canchas(); track cancha.id) {
                  <td class="precio">{{ cancha.precioBase | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</td>
                }
              </tr>
              <tr>
                <th scope="row">Capacidad</th>
                @for (cancha of canchas(); track cancha.id) {
                  <td>{{ cancha.capacidad }} personas</td>
                }
              </tr>
              <tr>
                <th scope="row">Distrito</th>
                @for (cancha of canchas(); track cancha.id) {
                  <td>{{ cancha.distrito.nombre }}</td>
                }
              </tr>
              <tr>
                <th scope="row">Establecimiento</th>
                @for (cancha of canchas(); track cancha.id) {
                  <td>{{ cancha.establecimiento }}</td>
                }
              </tr>
              <tr>
                <th scope="row">Deportes</th>
                @for (cancha of canchas(); track cancha.id) {
                  <td>{{ deportes(cancha) }}</td>
                }
              </tr>
              <tr>
                <th scope="row">Características</th>
                @for (cancha of canchas(); track cancha.id) {
                  <td>{{ caracteristicas(cancha) }}</td>
                }
              </tr>
              <tr>
                <th scope="row">Reputacion</th>
                @for (cancha of canchas(); track cancha.id) {
                  <td>★ {{ cancha.reputacion.promedio }} ({{ cancha.reputacion.total }})</td>
                }
              </tr>
              <tr>
                <th scope="row">Acción</th>
                @for (cancha of canchas(); track cancha.id) {
                  <td>
                    <a [routerLink]="['/canchas', cancha.id]" class="cd-btn cd-btn--secondary cd-btn--sm">Ver detalle</a>
                  </td>
                }
              </tr>
            </tbody>
          </table>
        </div>
      }

      <div class="acciones">
        <a routerLink="/canchas" class="cd-btn cd-btn--ghost">Seguir buscando</a>
        <button cdButton variant="ghost" type="button" (click)="limpiar()">Limpiar selección</button>
      </div>
    </div>
  `,
  styles: `
    .tabla-scroll {
      overflow-x: auto;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-md);
      background: var(--cd-color-surface);
    }
    .tabla {
      width: 100%;
      border-collapse: collapse;
      min-width: 720px;
      font-size: var(--cd-text-sm);
    }
    th,
    td {
      padding: var(--cd-space-3);
      text-align: left;
      border-bottom: 1px solid var(--cd-color-border);
      vertical-align: top;
    }
    thead th {
      background: var(--cd-color-surface-alt);
      font-size: var(--cd-text-xs);
      text-transform: uppercase;
      letter-spacing: 0.02em;
      color: var(--cd-color-text-muted);
      position: relative;
    }
    thead th a {
      color: var(--cd-color-primary);
      font-weight: 800;
      text-transform: none;
      font-size: var(--cd-text-sm);
      letter-spacing: 0;
    }
    tbody th {
      font-weight: 700;
      width: 180px;
      background: var(--cd-color-surface);
    }
    .precio {
      font-weight: 800;
      color: var(--cd-color-primary);
    }
    .quitar {
      border: 0;
      background: transparent;
      cursor: pointer;
      font-size: 18px;
      color: var(--cd-color-text-muted);
      position: absolute;
      top: 6px;
      right: 6px;
    }
    .acciones {
      display: flex;
      gap: var(--cd-space-2);
      flex-wrap: wrap;
      margin-top: var(--cd-space-5);
    }
    .acciones .cd-btn {
      text-decoration: none;
    }
  `,
})
export class CompararComponent implements OnInit {
  private readonly api = inject(VenuesApi);
  private readonly route = inject(ActivatedRoute);
  protected readonly comparacion = inject(ComparacionService);

  protected readonly canchas = signal<CanchaDetalle[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    if (!this.route.snapshot.queryParamMap.get('ids')) {
      const guardadas = this.comparacion.ids();
      if (guardadas.length >= 2) {
        this.cargarCon(guardadas);
      } else {
        this.cargando.set(false);
      }
    } else {
      this.cargar();
    }
  }

  protected cargar(): void {
    const ids = (this.route.snapshot.queryParamMap.get('ids') ?? '')
      .split(',')
      .map((valor) => Number(valor))
      .filter((valor) => Number.isFinite(valor) && valor > 0)
      .slice(0, 4);
    this.cargarCon(ids);
  }

  protected quitar(id: number): void {
    this.comparacion.quitar(id);
    this.canchas.update((canchas) => canchas.filter((cancha) => cancha.id !== id));
  }

  protected limpiar(): void {
    this.comparacion.limpiar();
    this.canchas.set([]);
  }

  protected deportes(cancha: CanchaDetalle): string {
    return cancha.deportes.map((d) => d.nombre).join(', ') || '—';
  }

  protected caracteristicas(cancha: CanchaDetalle): string {
    return cancha.caracteristicas.map((c) => `${c.nombre}: ${c.valor}`).join(' · ') || '—';
  }

  private cargarCon(ids: number[]): void {
    if (ids.length < 2) {
      this.cargando.set(false);
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    firstValueFrom(this.api.comparar(ids))
      .then((canchas) => {
        this.canchas.set(canchas);
        this.cargando.set(false);
      })
      .catch((error) => {
        this.error.set(toApiError(error).message);
        this.cargando.set(false);
      });
  }
}
