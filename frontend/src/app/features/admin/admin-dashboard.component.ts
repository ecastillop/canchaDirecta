import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom, forkJoin } from 'rxjs';
import {
  AdminApi,
  AdminPropietario,
  AdminReporte,
  AdminReserva,
} from '../../core/api/admin.api';
import { toApiError } from '../../core/http/api-error';
import { PortalIconComponent } from '../../shared/ui/portal-icon/portal-icon.component';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';

interface DatosAdmin {
  reporte: AdminReporte;
  pendientes: AdminPropietario[];
  reservas: AdminReserva[];
}

@Component({
  selector: 'app-admin-dashboard',
  imports: [
    RouterLink,
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
        <h2 class="intro-title">Visión global de la plataforma</h2>
        <p>Indicadores de operación de todas las sedes, propietarios y usuarios.</p>
      </div>
      <a routerLink="/admin/propietarios" class="cd-btn cd-btn--primary cd-btn--sm">
        Revisar solicitudes →
      </a>
    </div>

    @if (cargando()) {
      <div class="cd-stack">
        <cd-skeleton height="140px" radius="16px" />
        <cd-skeleton height="260px" radius="16px" />
      </div>
    } @else if (error()) {
      <cd-error-state [message]="error()!" (retry)="cargar()" />
    } @else if (datos(); as d) {
      <div class="stats-grid">
        <article class="stat-card">
          <div class="stat-top">
            <span>Volumen aprobado</span>
            <span class="stat-icon"><cd-portal-icon name="wallet" [size]="19" /></span>
          </div>
          <strong>{{ d.reporte.resumen.ingresosAprobados | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</strong>
          <small>{{ d.reporte.resumen.pagosAprobados }} pagos aprobados</small>
        </article>
        <article class="stat-card">
          <div class="stat-top">
            <span>Reservas del periodo</span>
            <span class="stat-icon"><cd-portal-icon name="calendar" [size]="19" /></span>
          </div>
          <strong>{{ d.reporte.resumen.reservasPeriodo }}</strong>
          <small>Últimos {{ d.reporte.dias }} días</small>
        </article>
        <article class="stat-card">
          <div class="stat-top">
            <span>Sedes activas</span>
            <span class="stat-icon"><cd-portal-icon name="building" [size]="19" /></span>
          </div>
          <strong>{{ d.reporte.resumen.establecimientosActivos }}</strong>
          <small>{{ d.reporte.resumen.propietariosValidados }} propietarios validados</small>
        </article>
        <article class="stat-card">
          <div class="stat-top">
            <span>Usuarios registrados</span>
            <span class="stat-icon"><cd-portal-icon name="users" [size]="19" /></span>
          </div>
          <strong>{{ d.reporte.resumen.usuarios }}</strong>
          <small>+{{ d.reporte.resumen.usuariosNuevos }} en el periodo</small>
        </article>
      </div>

      <div class="dashboard-two">
        <section class="panel">
          <header class="panel-heading">
            <h3>Propietarios pendientes</h3>
            <a routerLink="/admin/propietarios" class="enlace">Ver todos →</a>
          </header>
          @if (d.pendientes.length === 0) {
            <cd-empty-state title="Todo al día" message="No hay solicitudes de propietario pendientes." />
          } @else {
            <ul class="pendientes">
              @for (item of d.pendientes.slice(0, 5); track item.id) {
                <li>
                  <span class="pendientes__avatar" aria-hidden="true">{{ item.nombreCompleto.charAt(0) }}</span>
                  <div>
                    <strong>{{ item.nombreCompleto }}</strong>
                    <small class="cd-xs cd-muted">{{ item.correo }} · {{ item.documento }}</small>
                  </div>
                  <span class="cd-badge">{{ item.estadoValidacion }}</span>
                </li>
              }
            </ul>
          }
        </section>

        <section class="panel">
          <header class="panel-heading">
            <h3>Accesos rápidos</h3>
          </header>
          <div class="quick-actions">
            <a routerLink="/admin/reportes">
              <span class="quick-actions__icono"><cd-portal-icon name="chart" [size]="18" /></span>
              <span>Reportes de plataforma</span>
              <cd-portal-icon name="chevron" [size]="16" />
            </a>
            <a routerLink="/admin/usuarios">
              <span class="quick-actions__icono"><cd-portal-icon name="users" [size]="18" /></span>
              <span>Usuarios y roles</span>
              <cd-portal-icon name="chevron" [size]="16" />
            </a>
            <a routerLink="/admin/supervision">
              <span class="quick-actions__icono"><cd-portal-icon name="list" [size]="18" /></span>
              <span>Supervisar operación</span>
              <cd-portal-icon name="chevron" [size]="16" />
            </a>
            <a routerLink="/admin/promociones">
              <span class="quick-actions__icono"><cd-portal-icon name="ticket" [size]="18" /></span>
              <span>Promociones y cupones</span>
              <cd-portal-icon name="chevron" [size]="16" />
            </a>
          </div>
        </section>
      </div>

      <section class="panel">
        <header class="panel-heading">
          <h3>Últimas operaciones</h3>
          <a routerLink="/admin/supervision" class="enlace">Ver supervisión →</a>
        </header>
        @if (d.reservas.length === 0) {
          <cd-empty-state title="Sin operaciones" message="Todavía no hay reservas registradas." />
        } @else {
          <div class="tabla-scroll">
            <table class="tabla">
              <caption class="cd-visually-hidden">Últimas reservas</caption>
              <thead>
                <tr>
                  <th scope="col">Reserva</th>
                  <th scope="col">Establecimiento / cancha</th>
                  <th scope="col">Titular</th>
                  <th scope="col">Inicio</th>
                  <th scope="col">Monto</th>
                  <th scope="col">Estado</th>
                </tr>
              </thead>
              <tbody>
                @for (reserva of d.reservas.slice(0, 6); track reserva.id) {
                  <tr>
                    <td class="code">#{{ reserva.id }}</td>
                    <td>
                      <strong>{{ reserva.establecimiento }}</strong>
                      <small class="cd-xs cd-muted">{{ reserva.cancha }}</small>
                    </td>
                    <td>{{ reserva.titular }}</td>
                    <td>{{ reserva.inicio | date: 'd MMM, HH:mm' }}</td>
                    <td>{{ reserva.montoCancha | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</td>
                    <td><span class="cd-badge">{{ reserva.estado }}</span></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
    }
  `,
  styles: `
    .dashboard-intro {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 18px;
      flex-wrap: wrap;
      margin-bottom: 24px;
    }
    .eyebrow {
      margin: 0 0 8px;
      color: var(--cd-color-teal);
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
      color: var(--cd-color-text-muted);
      font-size: 12px;
    }
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 15px;
      margin-bottom: 22px;
    }
    .stat-card {
      min-height: 140px;
      padding: 19px;
      border: 1px solid var(--cd-color-border);
      border-radius: 14px;
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-1);
    }
    .stat-top {
      display: flex;
      align-items: start;
      justify-content: space-between;
      gap: 8px;
      color: var(--cd-color-text-muted);
      font-size: 11px;
      font-weight: 700;
    }
    .stat-icon {
      display: grid;
      place-items: center;
      width: 35px;
      height: 35px;
      border-radius: 10px;
      background: var(--cd-color-success-soft);
      color: var(--cd-color-success);
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
      color: var(--cd-color-text-muted);
      font-size: 11px;
    }
    .dashboard-two {
      display: grid;
      grid-template-columns: minmax(0, 1.6fr) minmax(280px, 1fr);
      gap: 16px;
      margin-bottom: 16px;
    }
    .panel {
      padding: 24px;
      border: 1px solid var(--cd-color-border);
      border-radius: 16px;
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-1);
    }
    .panel-heading {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      margin-bottom: 16px;
    }
    .panel-heading h3 {
      margin: 0;
      font-size: 18px;
    }
    .enlace {
      color: var(--cd-color-teal);
      font-size: 11px;
      font-weight: 800;
      text-decoration: none;
    }
    .enlace:hover {
      text-decoration: underline;
    }
    .pendientes {
      display: grid;
      gap: 10px;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .pendientes li {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 0;
      border-bottom: 1px solid var(--cd-color-border);
    }
    .pendientes li:last-child {
      border-bottom: 0;
    }
    .pendientes__avatar {
      display: grid;
      place-items: center;
      width: 34px;
      height: 34px;
      border-radius: 50%;
      background: var(--cd-color-success-soft);
      color: var(--cd-color-success);
      font-size: 12px;
      font-weight: 900;
    }
    .pendientes div {
      display: grid;
      gap: 2px;
      flex: 1;
      min-width: 0;
    }
    .quick-actions {
      display: grid;
      gap: 8px;
    }
    .quick-actions a {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 14px;
      border: 1px solid var(--cd-color-border);
      border-radius: 11px;
      background: var(--cd-color-surface-alt);
      color: var(--cd-color-text);
      font-size: 12px;
      font-weight: 700;
      text-decoration: none;
    }
    .quick-actions a:hover {
      background: var(--cd-color-success-soft);
      color: var(--cd-color-success);
    }
    .quick-actions a > span:nth-child(2) {
      flex: 1;
    }
    .quick-actions__icono {
      display: grid;
      place-items: center;
      width: 30px;
      height: 30px;
      border-radius: 8px;
      background: var(--cd-color-success-soft);
      color: var(--cd-color-success);
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
      border-bottom: 1px solid var(--cd-color-border);
      color: var(--cd-color-text-muted);
      text-align: left;
      font-size: 10px;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }
    .tabla td {
      padding: 10px;
      border-bottom: 1px solid var(--cd-color-border);
      vertical-align: top;
    }
    .tabla small {
      display: block;
    }
    .code {
      color: var(--cd-color-teal);
      font-weight: 800;
    }
    @media (max-width: 1000px) {
      .stats-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
      .dashboard-two {
        grid-template-columns: 1fr;
      }
    }
    @media (max-width: 560px) {
      .stats-grid {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class AdminDashboardComponent implements OnInit {
  private readonly api = inject(AdminApi);

  protected readonly datos = signal<DatosAdmin | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    void firstValueFrom(
      forkJoin({
        reporte: this.api.reporte(30),
        pendientes: this.api.propietarios('PENDIENTE'),
        reservas: this.api.reservas(),
      }),
    )
      .then((datos) => this.datos.set(datos))
      .catch((error) => this.error.set(toApiError(error).message))
      .finally(() => this.cargando.set(false));
  }
}
