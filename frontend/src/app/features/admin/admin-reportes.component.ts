import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AdminApi, AdminReporte } from '../../core/api/admin.api';
import { toApiError } from '../../core/http/api-error';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';

@Component({
  selector: 'app-admin-reportes',
  imports: [CurrencyPipe, DatePipe, CdSkeletonComponent, CdErrorStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="cabecera">
      <div>
        <p class="eyebrow">Administración</p>
        <h2 class="intro-title">Reportes de plataforma</h2>
        <p class="cd-small cd-muted">
          Actividad agregada de los últimos {{ dias() }} días: usuarios, reservas, ingresos y
          establecimientos con más movimiento.
        </p>
      </div>
      <div class="periodos" role="group" aria-label="Periodo del reporte">
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
        <cd-skeleton height="260px" radius="16px" />
      </div>
    } @else if (error()) {
      <cd-error-state [message]="error()!" (retry)="cargar()" />
    } @else if (reporte(); as r) {
      <div class="kpis">
        <article class="kpi">
          <span class="kpi__label">Usuarios</span>
          <strong class="kpi__valor">{{ r.resumen.usuarios }}</strong>
          <span class="cd-xs cd-muted">+{{ r.resumen.usuariosNuevos }} en el periodo</span>
        </article>
        <article class="kpi">
          <span class="kpi__label">Establecimientos activos</span>
          <strong class="kpi__valor">{{ r.resumen.establecimientosActivos }}</strong>
          <span class="cd-xs cd-muted">{{ r.resumen.propietariosValidados }} propietarios validados</span>
        </article>
        <article class="kpi">
          <span class="kpi__label">Reservas del periodo</span>
          <strong class="kpi__valor">{{ r.resumen.reservasPeriodo }}</strong>
          <span class="cd-xs cd-muted">{{ r.resumen.reclamosAbiertos }} reclamos abiertos</span>
        </article>
        <article class="kpi">
          <span class="kpi__label">Ingresos aprobados</span>
          <strong class="kpi__valor">
            {{ r.resumen.ingresosAprobados | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}
          </strong>
          <span class="cd-xs cd-muted">{{ r.resumen.pagosAprobados }} pagos aprobados</span>
        </article>
      </div>

      <div class="grid">
        <section class="cd-card">
          <div class="cd-row cd-row--between">
            <h2 class="seccion">Reservas por día</h2>
            <span class="cd-xs cd-muted">{{ r.desde | date: 'd MMM' }} - {{ r.hasta | date: 'd MMM' }}</span>
          </div>
          @if (r.resumen.reservasPeriodo === 0) {
            <p class="cd-small cd-muted">Sin reservas registradas en el periodo.</p>
          } @else {
            <div class="grafico" role="img" [attr.aria-label]="resumenSerie(r)">
              @for (punto of r.serie; track punto.fecha) {
                <div class="grafico__col" [title]="punto.fecha + ': ' + punto.reservas + ' reservas'">
                  <span
                    class="grafico__barra"
                    [style.height.%]="altura(punto.reservas, maxReservas(r))"
                    [class.grafico__barra--cero]="punto.reservas === 0"
                  ></span>
                </div>
              }
            </div>
            <div class="grafico__fechas cd-xs cd-muted" aria-hidden="true">
              <span>{{ r.serie[0].fecha | date: 'd MMM' }}</span>
              <span>{{ r.serie[r.serie.length - 1].fecha | date: 'd MMM' }}</span>
            </div>
          }
        </section>

        <section class="cd-card">
          <h2 class="seccion">Usuarios nuevos</h2>
          @if (r.resumen.usuariosNuevos === 0) {
            <p class="cd-small cd-muted">Sin registros nuevos en el periodo.</p>
          } @else {
            <div class="grafico grafico--usuarios" role="img" [attr.aria-label]="resumenUsuarios(r)">
              @for (punto of r.serie; track punto.fecha) {
                <div class="grafico__col" [title]="punto.fecha + ': ' + punto.usuariosNuevos + ' usuarios'">
                  <span
                    class="grafico__barra grafico__barra--usuarios"
                    [style.height.%]="altura(punto.usuariosNuevos, maxUsuarios(r))"
                    [class.grafico__barra--cero]="punto.usuariosNuevos === 0"
                  ></span>
                </div>
              }
            </div>
          }
        </section>
      </div>

      <div class="grid grid--inferior">
        <section class="cd-card">
          <h2 class="seccion">Reservas por estado</h2>
          @if (r.estados.length === 0) {
            <p class="cd-small cd-muted">Sin reservas en el periodo.</p>
          } @else {
            <ul class="estados">
              @for (estado of r.estados; track estado.estado) {
                <li>
                  <div class="cd-row cd-row--between">
                    <strong>{{ estado.estado }}</strong>
                    <span class="cd-xs cd-muted">{{ estado.total }}</span>
                  </div>
                  <div class="barra" aria-hidden="true">
                    <span [style.width.%]="porcentaje(estado.total, r.resumen.reservasPeriodo)"></span>
                  </div>
                </li>
              }
            </ul>
          }
        </section>

        <section class="cd-card">
          <h2 class="seccion">Establecimientos con más reservas</h2>
          @if (r.establecimientos.length === 0) {
            <p class="cd-small cd-muted">Sin actividad en el periodo.</p>
          } @else {
            <ol class="ranking">
              @for (item of r.establecimientos; track item.id; let i = $index) {
                <li>
                  <span class="ranking__puesto" aria-hidden="true">{{ i + 1 }}</span>
                  <div class="ranking__datos">
                    <strong>{{ item.nombre }}</strong>
                    <span class="cd-xs cd-muted">
                      {{ item.distrito }} · {{ item.reservas }} reservas ·
                      {{ item.ingresos | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}
                    </span>
                  </div>
                </li>
              }
            </ol>
          }
        </section>
      </div>
    }
  `,
  styles: `
    .cabecera {
      display: flex;
      align-items: end;
      justify-content: space-between;
      gap: 18px;
      flex-wrap: wrap;
      margin-bottom: 20px;
    }
    .eyebrow {
      margin: 0 0 8px;
      color: var(--cd-color-teal, #167a70);
      font-size: 10px;
      font-weight: 800;
      letter-spacing: 0.18em;
      text-transform: uppercase;
    }
    .intro-title {
      margin: 0;
      font-size: clamp(26px, 3vw, 38px);
      letter-spacing: -0.045em;
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
      gap: 5px;
      align-content: start;
      padding: 16px;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-lg);
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-1);
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
      font-size: clamp(24px, 2.5vw, 32px);
      letter-spacing: -0.03em;
      line-height: 1.1;
    }
    .grid {
      display: grid;
      grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
      gap: 16px;
    }
    .grid--inferior {
      margin-top: 16px;
    }
    .seccion {
      font-size: var(--cd-text-base);
      margin: 0 0 14px;
    }
    .grafico {
      display: flex;
      align-items: end;
      gap: 3px;
      height: 170px;
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
    .grafico__barra--usuarios {
      background: linear-gradient(180deg, var(--cd-color-accent), #d8b74a);
    }
    .grafico__barra--cero {
      background: var(--cd-color-border);
    }
    .grafico__fechas {
      display: flex;
      justify-content: space-between;
      margin-top: 8px;
    }
    .estados {
      display: grid;
      gap: 12px;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .estados li {
      display: grid;
      gap: 5px;
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
      gap: 10px;
    }
    .ranking__puesto {
      display: grid;
      place-items: center;
      width: 24px;
      height: 24px;
      border-radius: 50%;
      background: var(--cd-color-surface-alt);
      color: var(--cd-color-text-muted);
      font-size: 11px;
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
    @media (max-width: 1000px) {
      .kpis {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
      .grid {
        grid-template-columns: 1fr;
      }
    }
    @media (max-width: 560px) {
      .kpis {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class AdminReportesComponent implements OnInit {
  private readonly api = inject(AdminApi);

  protected readonly periodos = [7, 30, 90];
  protected readonly dias = signal(30);
  protected readonly reporte = signal<AdminReporte | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly maxReservas = (r: AdminReporte) =>
    Math.max(...r.serie.map((punto) => punto.reservas), 1);
  protected readonly maxUsuarios = (r: AdminReporte) =>
    Math.max(...r.serie.map((punto) => punto.usuariosNuevos), 1);

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
    this.cargando.set(true);
    this.error.set(null);
    void firstValueFrom(this.api.reporte(this.dias()))
      .then((reporte) => this.reporte.set(reporte))
      .catch((error) => this.error.set(toApiError(error).message))
      .finally(() => this.cargando.set(false));
  }

  protected altura(valor: number, maximo: number): number {
    return valor === 0 ? 2 : Math.max(4, (valor / maximo) * 100);
  }

  protected porcentaje(valor: number, total: number): number {
    return total === 0 ? 0 : Math.min(100, (valor / total) * 100);
  }

  protected resumenSerie(reporte: AdminReporte): string {
    return reporte.serie
      .filter((punto) => punto.reservas > 0)
      .map((punto) => `${punto.fecha}: ${punto.reservas}`)
      .join(', ');
  }

  protected resumenUsuarios(reporte: AdminReporte): string {
    return reporte.serie
      .filter((punto) => punto.usuariosNuevos > 0)
      .map((punto) => `${punto.fecha}: ${punto.usuariosNuevos}`)
      .join(', ');
  }
}
