import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { MetricasEstablecimiento } from '../../core/api/metricas.models';
import { OwnerApi } from '../../core/api/owner.api';
import { OwnerStore } from '../../core/api/owner.store';
import { toApiError } from '../../core/http/api-error';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';

@Component({
  selector: 'app-panel-metricas',
  imports: [
    RouterLink,
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    CdSkeletonComponent,
    CdEmptyStateComponent,
    CdErrorStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!store.establecimientoId()) {
      <cd-empty-state title="Falta el establecimiento" message="Crea tu establecimiento desde Resumen para ver tus métricas." />
    } @else {
      <header class="cabecera">
        <div>
          <p class="eyebrow">Panel del propietario</p>
          <h2>Métricas del establecimiento</h2>
          <p class="cd-small cd-muted">
            Reservas, ingresos y ocupación de los últimos {{ dias() }} días. Las próximas reservas se
            muestran aparte.
          </p>
        </div>
        <div class="periodos" role="group" aria-label="Periodo de las métricas">
          @for (opcion of periodos; track opcion) {
            <button
              type="button"
              class="periodo"
              [class.periodo--activo]="dias() === opcion"
              [attr.aria-pressed]="dias() === opcion"
              (click)="cambiarPeriodo(opcion)"
            >
              {{ opcion }} días
            </button>
          }
        </div>
      </header>

      @if (cargando()) {
        <div class="cd-stack">
          <cd-skeleton height="110px" radius="16px" />
          <cd-skeleton height="240px" radius="16px" />
        </div>
      } @else if (error()) {
        <cd-error-state [message]="error()!" (retry)="cargar()" />
      } @else if (metricas(); as m) {
        <div class="kpis">
          <article class="kpi">
            <div class="kpi__top">
              <span class="kpi__label">Reservas recibidas</span>
              <span class="kpi__icono" aria-hidden="true">▤</span>
            </div>
            <strong class="kpi__valor">{{ m.resumen.totalReservas }}</strong>
            <span class="cd-xs cd-muted">
              {{ m.resumen.confirmadas }} confirmadas · {{ m.resumen.pendientes }} pendientes
            </span>
          </article>
          <article class="kpi">
            <div class="kpi__top">
              <span class="kpi__label">Ingresos confirmados</span>
              <span class="kpi__icono" aria-hidden="true">S/</span>
            </div>
            <strong class="kpi__valor">
              {{ m.resumen.ingresos | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}
            </strong>
            <span class="cd-xs cd-muted">Confirmadas y finalizadas</span>
          </article>
          <article class="kpi">
            <div class="kpi__top">
              <span class="kpi__label">Ocupación estimada</span>
              <span class="kpi__icono" aria-hidden="true">◪</span>
            </div>
            <strong class="kpi__valor">{{ m.resumen.ocupacionPorcentaje | number: '1.1-1' }}%</strong>
            <div class="barra" aria-hidden="true">
              <span [style.width.%]="m.resumen.ocupacionPorcentaje"></span>
            </div>
            <span class="cd-xs cd-muted">{{ m.resumen.canchasActivas }} canchas activas · 12 h/día</span>
          </article>
          <article class="kpi">
            <div class="kpi__top">
              <span class="kpi__label">Horas reservadas</span>
              <span class="kpi__icono" aria-hidden="true">◔</span>
            </div>
            <strong class="kpi__valor">{{ m.resumen.horasReservadas | number: '1.1-1' }} h</strong>
            <span class="cd-xs cd-muted">{{ m.resumen.canceladas }} reservas canceladas o expiradas</span>
          </article>
        </div>

        <div class="grid">
          <section class="cd-card">
            <div class="cd-row cd-row--between">
              <h2 class="seccion">Reservas por día</h2>
              <span class="cd-xs cd-muted">Periodo {{ m.desde | date: 'd MMM' }} - {{ m.hasta | date: 'd MMM' }}</span>
            </div>
            @if (m.resumen.totalReservas === 0) {
              <p class="cd-small cd-muted">Todavía no hay reservas en este periodo.</p>
            } @else {
              <div
                class="grafico"
                role="img"
                [attr.aria-label]="'Reservas por día: ' + resumenSerie(m)"
              >
                @for (punto of m.serie; track punto.fecha) {
                  <div class="grafico__col" [title]="punto.fecha + ': ' + punto.reservas + ' reservas'">
                    <span
                      class="grafico__barra"
                      [style.height.%]="altura(punto.reservas, m)"
                      [class.grafico__barra--cero]="punto.reservas === 0"
                    ></span>
                  </div>
                }
              </div>
              <div class="grafico__fechas cd-xs cd-muted" aria-hidden="true">
                <span>{{ m.serie[0].fecha | date: 'd MMM' }}</span>
                <span>{{ m.serie[m.serie.length - 1].fecha | date: 'd MMM' }}</span>
              </div>
            }
          </section>

          <section class="cd-card">
            <h2 class="seccion">Canchas con más reservas</h2>
            @if (m.canchas.length === 0) {
              <p class="cd-small cd-muted">Sin actividad en el periodo.</p>
            } @else {
              <ol class="ranking">
                @for (cancha of m.canchas; track cancha.idCancha; let i = $index) {
                  <li>
                    <span class="ranking__puesto" aria-hidden="true">{{ i + 1 }}</span>
                    <div class="ranking__datos">
                      <strong>{{ cancha.nombre }}</strong>
                      <span class="cd-xs cd-muted">
                        {{ cancha.reservas }} reservas ·
                        {{ cancha.ingresos | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}
                      </span>
                    </div>
                  </li>
                }
              </ol>
            }
          </section>
        </div>

        <section class="cd-card proximas">
          <div class="cd-row cd-row--between">
            <h2 class="seccion">Próximas reservas</h2>
            <a routerLink="/panel/reservas" class="cd-small enlace">Ver todas →</a>
          </div>
          @if (m.proximas.length === 0) {
            <p class="cd-small cd-muted">No hay reservas próximas pendientes ni confirmadas.</p>
          } @else {
            <ul class="lista-proximas">
              @for (reserva of m.proximas; track reserva.idReserva) {
                <li>
                  <div class="fecha">
                    <strong>{{ reserva.inicio | date: 'd' }}</strong>
                    <span>{{ reserva.inicio | date: 'MMM' }}</span>
                  </div>
                  <div class="detalle">
                    <strong>{{ reserva.cancha }}</strong>
                    <span class="cd-xs cd-muted">{{ reserva.cliente }}</span>
                    <span class="cd-xs cd-muted">
                      {{ reserva.inicio | date: 'EEE d MMM, HH:mm' }} -
                      {{ reserva.fin | date: 'HH:mm' }}
                    </span>
                  </div>
                  <span
                    class="cd-badge"
                    [class.cd-badge--success]="reserva.estado === 'CONFIRMADA'"
                  >
                    {{ reserva.estado }}
                  </span>
                  <strong class="monto">
                    {{ reserva.monto | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}
                  </strong>
                </li>
              }
            </ul>
          }
        </section>
      }
    }
  `,
  styles: `
    .cabecera {
      display: flex;
      align-items: end;
      justify-content: space-between;
      gap: 18px;
      flex-wrap: wrap;
      margin-bottom: 22px;
    }
    .eyebrow {
      margin: 0 0 8px;
      color: var(--cd-color-teal, #167a70);
      font-size: 10px;
      font-weight: 800;
      letter-spacing: 0.18em;
      text-transform: uppercase;
    }
    .cabecera h1 {
      margin: 0;
      font-size: clamp(28px, 3.2vw, 42px);
    }
    .cabecera p {
      margin: 6px 0 0;
    }
    .periodos {
      display: flex;
      gap: 4px;
      padding: 4px;
      border: 1px solid var(--cd-color-border);
      border-radius: 999px;
      background: var(--cd-color-surface);
    }
    .periodo {
      padding: 7px 13px;
      border: 0;
      border-radius: 999px;
      background: transparent;
      color: var(--cd-color-text-muted);
      font: inherit;
      font-size: 12px;
      font-weight: 700;
      cursor: pointer;
    }
    .periodo--activo {
      background: var(--cd-color-primary);
      color: var(--cd-color-on-primary);
    }
    .kpis {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 12px;
      margin-bottom: 16px;
    }
    .kpi {
      display: grid;
      gap: 6px;
      align-content: start;
      min-height: 140px;
      padding: 18px;
      border: 1px solid var(--cd-color-border);
      border-radius: 14px;
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-1);
    }
    .kpi__top {
      display: flex;
      align-items: start;
      justify-content: space-between;
      gap: 8px;
    }
    .kpi__icono {
      display: grid;
      place-items: center;
      width: 34px;
      height: 34px;
      border-radius: 10px;
      background: var(--cd-color-success-soft);
      color: var(--cd-color-success);
      font-size: 14px;
      font-weight: 800;
    }
    .kpi__label {
      color: var(--cd-color-text-muted);
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }
    .kpi__valor {
      font-family: var(--cd-font-display);
      font-size: clamp(26px, 2.6vw, 36px);
      letter-spacing: -0.03em;
      line-height: 1.1;
    }
    .barra {
      height: 6px;
      overflow: hidden;
      border-radius: 999px;
      background: var(--cd-color-surface-alt);
    }
    .barra span {
      display: block;
      height: 100%;
      border-radius: inherit;
      background: var(--cd-color-teal, #167a70);
    }
    .grid {
      display: grid;
      grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
      gap: 16px;
    }
    .seccion {
      font-size: var(--cd-text-base);
      margin: 0 0 14px;
    }
    .grafico {
      display: flex;
      align-items: end;
      gap: 3px;
      height: 180px;
      padding-top: 10px;
    }
    .grafico__col {
      display: flex;
      flex: 1;
      align-items: end;
      height: 100%;
    }
    .grafico__barra {
      width: 100%;
      min-height: 2px;
      border-radius: 4px 4px 2px 2px;
      background: linear-gradient(180deg, var(--cd-color-primary), var(--cd-color-teal, #167a70));
    }
    .grafico__barra--cero {
      background: var(--cd-color-border);
    }
    .grafico__fechas {
      display: flex;
      justify-content: space-between;
      margin-top: 8px;
    }
    .ranking {
      display: grid;
      gap: 12px;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .ranking li {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .ranking__puesto {
      display: grid;
      place-items: center;
      width: 26px;
      height: 26px;
      border-radius: 50%;
      background: var(--cd-color-surface-alt);
      color: var(--cd-color-text-muted);
      font-size: 12px;
      font-weight: 800;
    }
    .ranking li:first-child .ranking__puesto {
      background: var(--cd-color-accent);
      color: var(--cd-color-primary);
    }
    .ranking__datos {
      display: grid;
      gap: 2px;
      min-width: 0;
    }
    .proximas {
      margin-top: 16px;
    }
    .enlace {
      color: var(--cd-color-primary);
      font-weight: 700;
      text-decoration: none;
    }
    .enlace:hover {
      text-decoration: underline;
    }
    .lista-proximas {
      display: grid;
      gap: 8px;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .lista-proximas li {
      display: grid;
      grid-template-columns: 52px minmax(0, 1fr) auto auto;
      align-items: center;
      gap: 14px;
      padding: 10px 12px;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-sm);
    }
    .fecha {
      display: grid;
      justify-items: center;
      padding: 6px;
      border-radius: 8px;
      background: var(--cd-color-accent);
      color: var(--cd-color-primary);
    }
    .fecha strong {
      font-size: 20px;
      line-height: 1;
    }
    .fecha span {
      font-size: 9px;
      font-weight: 800;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }
    .detalle {
      display: grid;
      gap: 2px;
      min-width: 0;
    }
    .monto {
      white-space: nowrap;
    }
    @media (max-width: 1050px) {
      .kpis {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
      .grid {
        grid-template-columns: 1fr;
      }
    }
    @media (max-width: 620px) {
      .kpis {
        grid-template-columns: 1fr;
      }
      .lista-proximas li {
        grid-template-columns: 48px minmax(0, 1fr);
      }
      .lista-proximas .cd-badge,
      .lista-proximas .monto {
        grid-column: 2;
        justify-self: start;
      }
    }
  `,
})
export class PanelMetricasComponent implements OnInit {
  private readonly api = inject(OwnerApi);
  protected readonly store = inject(OwnerStore);

  protected readonly periodos = [7, 30, 90];
  protected readonly dias = signal(30);
  protected readonly metricas = signal<MetricasEstablecimiento | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  protected cambiarPeriodo(dias: number): void {
    if (this.dias() === dias) {
      return;
    }
    this.dias.set(dias);
    this.cargar();
  }

  protected cargar(): void {
    const idEstablecimiento = this.store.establecimientoId();
    if (!idEstablecimiento) {
      this.cargando.set(false);
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    void firstValueFrom(this.api.metricas(idEstablecimiento, this.dias()))
      .then((metricas) => this.metricas.set(metricas))
      .catch((error) => this.error.set(toApiError(error).message))
      .finally(() => this.cargando.set(false));
  }

  protected altura(reservas: number, metricas: MetricasEstablecimiento): number {
    const maximo = Math.max(...metricas.serie.map((punto) => punto.reservas), 1);
    return reservas === 0 ? 2 : Math.max(4, (reservas / maximo) * 100);
  }

  protected resumenSerie(metricas: MetricasEstablecimiento): string {
    return metricas.serie
      .filter((punto) => punto.reservas > 0)
      .map((punto) => `${punto.fecha}: ${punto.reservas}`)
      .join(', ');
  }
}
