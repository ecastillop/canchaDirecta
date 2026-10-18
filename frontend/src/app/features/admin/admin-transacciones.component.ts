import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AdminApi, AdminPago } from '../../core/api/admin.api';
import { toApiError } from '../../core/http/api-error';
import { PortalIconComponent } from '../../shared/ui/portal-icon/portal-icon.component';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';

const COMISION = 0.1;

@Component({
  selector: 'app-admin-transacciones',
  imports: [
    CurrencyPipe,
    DatePipe,
    PortalIconComponent,
    CdSkeletonComponent,
    CdEmptyStateComponent,
    CdErrorStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dashboard-intro">
      <div>
        <p class="eyebrow">ADMINISTRADOR · CANCHADIRECTA</p>
        <h2 class="intro-title">Transacciones y comisiones</h2>
        <p>Vista demostrativa de conciliación y pagos registrados en la plataforma.</p>
      </div>
    </div>

    @if (cargando()) {
      <div class="cd-stack">
        <cd-skeleton height="130px" radius="16px" />
        <cd-skeleton height="260px" radius="16px" />
      </div>
    } @else if (error()) {
      <cd-error-state [message]="error()!" (retry)="cargar()" />
    } @else {
      <div class="stats-grid">
        <article class="stat-card">
          <div class="stat-top">
            <span>Total procesado</span>
            <span class="stat-icon"><cd-portal-icon name="wallet" [size]="19" /></span>
          </div>
          <strong>{{ totalProcesado() | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</strong>
          <small>{{ pagos().length }} operaciones registradas</small>
        </article>
        <article class="stat-card">
          <div class="stat-top">
            <span>Comisión plataforma</span>
            <span class="stat-icon"><cd-portal-icon name="chart" [size]="19" /></span>
          </div>
          <strong>{{ comisionTotal() | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</strong>
          <small>10% estimado sobre pagos aprobados</small>
        </article>
        <article class="stat-card">
          <div class="stat-top">
            <span>Neto estimado</span>
            <span class="stat-icon"><cd-portal-icon name="receipt" [size]="19" /></span>
          </div>
          <strong>{{ netoTotal() | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</strong>
          <small>Monto para propietarios</small>
        </article>
      </div>

      <div class="filtros" role="group" aria-label="Filtrar transacciones por estado">
        @for (estado of estados; track estado) {
          <button
            type="button"
            class="chip"
            [class.chip--activo]="filtro() === estado"
            (click)="filtro.set(estado)"
          >
            {{ estado || 'Todas' }}
          </button>
        }
      </div>

      @if (pagosFiltrados().length === 0) {
        <cd-empty-state title="Sin transacciones" message="No hay pagos con ese filtro." />
      } @else {
        <section class="panel">
          <div class="tabla-scroll">
            <table class="tabla">
              <thead>
                <tr>
                  <th scope="col">Reserva</th>
                  <th scope="col">Método</th>
                  <th scope="col">Fecha</th>
                  <th scope="col">Bruto</th>
                  <th scope="col">Comisión</th>
                  <th scope="col">Neto</th>
                  <th scope="col">Estado</th>
                </tr>
              </thead>
              <tbody>
                @for (pago of pagosFiltrados(); track pago.id) {
                  <tr>
                    <td class="code">#{{ pago.idReserva }}</td>
                    <td>{{ pago.medioPago }}</td>
                    <td>{{ pago.fechaPago | date: 'd MMM yyyy, HH:mm' }}</td>
                    <td>{{ pago.monto | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</td>
                    <td>{{ comision(pago) | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</td>
                    <td>{{ neto(pago) | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</td>
                    <td><span class="cd-badge">{{ pago.estado }}</span></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </section>
      }
    }
  `,
  styles: `
    .dashboard-intro {
      margin-bottom: 24px;
    }
    .eyebrow {
      margin: 0 0 8px;
      color: #2d8773;
      font-size: 9px;
      font-weight: 900;
      letter-spacing: 0.19em;
      text-transform: uppercase;
    }
    .intro-title {
      margin: 0 0 8px;
      font-size: clamp(27px, 2.8vw, 39px);
      letter-spacing: -0.045em;
    }
    .dashboard-intro p {
      margin: 0;
      color: #829085;
      font-size: 12px;
    }
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 15px;
      margin-bottom: 18px;
    }
    .stat-card {
      min-height: 140px;
      padding: 19px;
      border: 1px solid #e7ede4;
      border-radius: 14px;
      background: #fff;
      box-shadow: var(--cd-shadow-1);
    }
    .stat-top {
      display: flex;
      align-items: start;
      justify-content: space-between;
      gap: 8px;
      color: #708579;
      font-size: 11px;
      font-weight: 700;
    }
    .stat-icon {
      display: grid;
      place-items: center;
      width: 35px;
      height: 35px;
      border-radius: 10px;
      background: #eaf4e6;
      color: #3a8462;
    }
    .stat-card > strong {
      display: block;
      margin: 13px 0 5px;
      font-family: var(--cd-font-display);
      font-size: clamp(22px, 2.1vw, 32px);
      font-weight: 900;
      letter-spacing: -0.04em;
    }
    .stat-card > small {
      color: #829085;
      font-size: 11px;
    }
    .filtros {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      margin-bottom: 14px;
    }
    .chip {
      padding: 7px 12px;
      border: 1px solid #dde6dc;
      border-radius: 999px;
      background: #fff;
      color: #6d7e72;
      font: inherit;
      font-size: 11px;
      font-weight: 700;
      cursor: pointer;
    }
    .chip--activo {
      border-color: #2d8773;
      background: #e8f6ee;
      color: #1e6a57;
    }
    .panel {
      padding: 20px;
      border: 1px solid #e7ede4;
      border-radius: 16px;
      background: #fff;
      box-shadow: var(--cd-shadow-1);
    }
    .tabla-scroll {
      overflow-x: auto;
    }
    .tabla {
      width: 100%;
      border-collapse: collapse;
      font-size: 12px;
    }
    .tabla th {
      padding: 8px 10px;
      border-bottom: 1px solid #e7ede4;
      color: #708579;
      text-align: left;
      font-size: 10px;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }
    .tabla td {
      padding: 10px;
      border-bottom: 1px solid #f0f3ef;
    }
    .code {
      color: #2d8773;
      font-weight: 800;
    }
    @media (max-width: 900px) {
      .stats-grid {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class AdminTransaccionesComponent implements OnInit {
  private readonly api = inject(AdminApi);

  protected readonly estados = ['', 'APROBADO', 'PENDIENTE', 'REEMBOLSADO', 'RECHAZADO'];
  protected readonly pagos = signal<AdminPago[]>([]);
  protected readonly filtro = signal('');
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly pagosFiltrados = computed(() =>
    this.filtro() ? this.pagos().filter((pago) => pago.estado === this.filtro()) : this.pagos(),
  );
  protected readonly totalProcesado = computed(() =>
    this.pagos().filter((pago) => pago.estado === 'APROBADO').reduce((total, pago) => total + pago.monto, 0),
  );
  protected readonly comisionTotal = computed(() => this.totalProcesado() * COMISION);
  protected readonly netoTotal = computed(() => this.totalProcesado() - this.comisionTotal());

  ngOnInit(): void {
    this.cargar();
  }

  protected comision(pago: AdminPago): number {
    return pago.monto * COMISION;
  }

  protected neto(pago: AdminPago): number {
    return pago.monto - this.comision(pago);
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    void firstValueFrom(this.api.pagos())
      .then((pagos) => this.pagos.set(pagos))
      .catch((error) => this.error.set(toApiError(error).message))
      .finally(() => this.cargando.set(false));
  }
}
