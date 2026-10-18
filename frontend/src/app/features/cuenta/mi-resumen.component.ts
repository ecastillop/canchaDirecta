import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, firstValueFrom, forkJoin, of } from 'rxjs';
import { ActividadesApi } from '../../core/api/actividades.api';
import { Inscripcion } from '../../core/api/actividades.models';
import { EngagementApi, Notificacion, Reclamo } from '../../core/api/engagement.api';
import { ReservasApi } from '../../core/api/reservas.api';
import { Reserva } from '../../core/api/reservas.models';
import { VenuesApi } from '../../core/api/venues.api';
import { CanchaDetalle } from '../../core/api/venues.models';
import { AuthStore } from '../../core/auth/auth.store';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdIconComponent } from '../../shared/ui/icon/cd-icon.component';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';

interface ResumenPanel {
  reservas: Reserva[];
  inscripciones: Inscripcion[];
  reclamos: Reclamo[];
  notificaciones: Notificacion[];
  noLeidas: number;
  favoritos: CanchaDetalle[];
  nombresCancha: Map<number, string>;
}

@Component({
  selector: 'app-mi-resumen',
  imports: [
    RouterLink,
    CurrencyPipe,
    DatePipe,
    CdButtonDirective,
    CdIconComponent,
    CdSkeletonComponent,
    CdEmptyStateComponent,
    CdErrorStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="cabecera">
      <div>
        <p class="eyebrow">Mi cuenta</p>
        <h1>Hola, {{ auth.user()?.nombres || 'jugador' }}</h1>
        <p class="cd-small cd-muted">Tu próxima reserva, favoritos y pendientes en un vistazo.</p>
      </div>
      <a routerLink="/canchas" cdButton variant="primary" size="sm">Reservar cancha</a>
    </header>

    @if (cargando()) {
      <div class="cd-stack">
        <cd-skeleton height="100px" radius="16px" />
        <cd-skeleton height="220px" radius="16px" />
      </div>
    } @else if (error()) {
      <cd-error-state [message]="error()!" (retry)="cargar()" />
    } @else if (resumen(); as r) {
      <div class="kpis">
        <article class="kpi">
          <div class="kpi__top">
            <span class="kpi__label">Próxima reserva</span>
            <span class="kpi__icono" aria-hidden="true">▤</span>
          </div>
          @if (proxima(); as p) {
            <strong class="kpi__valor">{{ p.inicio | date: 'd MMM' }}</strong>
            <span class="cd-xs cd-muted">{{ p.inicio | date: 'HH:mm' }} · {{ nombreCancha(p.canchaId) }}</span>
          } @else {
            <strong class="kpi__valor kpi__valor--suave">Sin reservas</strong>
            <span class="cd-xs cd-muted">Reserva tu próximo partido</span>
          }
        </article>
        <article class="kpi">
          <div class="kpi__top">
            <span class="kpi__label">Reservas activas</span>
            <span class="kpi__icono" aria-hidden="true">✓</span>
          </div>
          <strong class="kpi__valor">{{ reservasActivas().length }}</strong>
          <span class="cd-xs cd-muted">{{ pendientesPago().length }} pendientes de confirmar</span>
        </article>
        <article class="kpi">
          <div class="kpi__top">
            <span class="kpi__label">Favoritos</span>
            <span class="kpi__icono" aria-hidden="true">♡</span>
          </div>
          <strong class="kpi__valor">{{ r.favoritos.length }}</strong>
          <span class="cd-xs cd-muted">Canchas guardadas</span>
        </article>
        <article class="kpi">
          <div class="kpi__top">
            <span class="kpi__label">Sin leer</span>
            <span class="kpi__icono" aria-hidden="true">◔</span>
          </div>
          <strong class="kpi__valor">{{ r.noLeidas }}</strong>
          <span class="cd-xs cd-muted">Notificaciones</span>
        </article>
      </div>

      <div class="grid">
        <div class="columna">
          <section class="cd-card">
            <div class="cd-row cd-row--between">
              <h2 class="seccion">Tu próxima reserva</h2>
              <a routerLink="/mis-reservas" class="enlace cd-small">Ver todas →</a>
            </div>
            @if (proxima(); as p) {
              <div class="proxima">
                <div class="proxima__fecha">
                  <strong>{{ p.inicio | date: 'd' }}</strong>
                  <span>{{ p.inicio | date: 'MMM' }}</span>
                </div>
                <div class="proxima__datos">
                  <strong>{{ nombreCancha(p.canchaId) }}</strong>
                  <span class="cd-small cd-muted">
                    {{ p.inicio | date: 'EEEE d MMM, HH:mm' }} - {{ p.fin | date: 'HH:mm' }}
                  </span>
                  <span
                    class="cd-badge"
                    [class.cd-badge--success]="p.estado === 'CONFIRMADA'"
                  >
                    {{ p.estado }}
                  </span>
                </div>
                <div class="proxima__acciones">
                  <strong>{{ monto(p) | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</strong>
                  <a [routerLink]="['/mis-reservas', p.id]" cdButton variant="secondary" size="sm">
                    Ver detalle
                  </a>
                </div>
              </div>
            } @else {
              <cd-empty-state
                title="Aún no tienes una reserva próxima"
                message="Encuentra una cancha disponible y reserva en minutos."
              >
                <a routerLink="/canchas" cdButton variant="secondary" size="sm">Explorar canchas</a>
              </cd-empty-state>
            }
          </section>

          <section class="cd-card">
            <h2 class="seccion">Pendientes</h2>
            @if (pendientes().length === 0) {
              <p class="cd-small cd-muted">No tienes pendientes. Todo al día.</p>
            } @else {
              <ul class="pendientes">
                @for (item of pendientes(); track item.texto) {
                  <li>
                    <span class="punto" aria-hidden="true"></span>
                    <div>
                      <strong>{{ item.texto }}</strong>
                      <span class="cd-xs cd-muted">{{ item.detalle }}</span>
                    </div>
                    <a [routerLink]="item.ruta" class="enlace cd-small">Revisar →</a>
                  </li>
                }
              </ul>
            }
          </section>
        </div>

        <div class="columna">
          <section class="cd-card">
            <h2 class="seccion">Accesos rápidos</h2>
            <div class="quick-actions">
              <a routerLink="/canchas">
                <span class="quick-actions__icono" aria-hidden="true">⌕</span>
                <span>Buscar canchas</span>
                <span aria-hidden="true">→</span>
              </a>
              <a routerLink="/favoritos">
                <span class="quick-actions__icono" aria-hidden="true">♡</span>
                <span>Mis favoritos</span>
                <span aria-hidden="true">→</span>
              </a>
              <a routerLink="/actividades">
                <span class="quick-actions__icono" aria-hidden="true">◎</span>
                <span>Unirme a actividades</span>
                <span aria-hidden="true">→</span>
              </a>
              <a routerLink="/ayuda">
                <span class="quick-actions__icono" aria-hidden="true">?</span>
                <span>Pedir ayuda</span>
                <span aria-hidden="true">→</span>
              </a>
            </div>
          </section>

          <section class="cd-card">
            <div class="cd-row cd-row--between">
              <h2 class="seccion">Favoritos</h2>
              <a routerLink="/favoritos" class="enlace cd-small">Ver todos →</a>
            </div>
            @if (r.favoritos.length === 0) {
              <p class="cd-small cd-muted">Todavía no guardaste canchas favoritas.</p>
            } @else {
              <ul class="favoritos">
                @for (cancha of r.favoritos; track cancha.id) {
                  <li>
                    <a [routerLink]="['/canchas', cancha.id]">
                      <span class="favoritos__icono" aria-hidden="true"><cd-icon name="heart" [size]="15" /></span>
                      <span>
                        <strong>{{ cancha.nombre }}</strong>
                        <small class="cd-xs cd-muted">{{ cancha.distrito.nombre }}</small>
                      </span>
                    </a>
                  </li>
                }
              </ul>
            }
          </section>

          <section class="cd-card">
            <div class="cd-row cd-row--between">
              <h2 class="seccion">Avisos recientes</h2>
              <a routerLink="/notificaciones" class="enlace cd-small">Ver todos →</a>
            </div>
            @if (r.notificaciones.length === 0) {
              <p class="cd-small cd-muted">No tienes notificaciones recientes.</p>
            } @else {
              <ul class="avisos">
                @for (aviso of r.notificaciones; track aviso.id) {
                  <li [class.avisos__no-leida]="!aviso.leida">
                    <strong>{{ aviso.titulo }}</strong>
                    <span class="cd-xs cd-muted">{{ aviso.mensaje }}</span>
                    <span class="cd-xs cd-muted">{{ aviso.fechaEnvio | date: 'd MMM, HH:mm' }}</span>
                  </li>
                }
              </ul>
            }
          </section>
        </div>
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
      font-size: clamp(30px, 3.4vw, 44px);
    }
    .cabecera p {
      margin: 6px 0 0;
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
      min-height: 132px;
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
      font-size: 15px;
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
      font-size: clamp(22px, 2.4vw, 30px);
      letter-spacing: -0.03em;
      line-height: 1.1;
    }
    .kpi__valor--suave {
      font-size: 20px;
      color: var(--cd-color-text-muted);
    }
    .grid {
      display: grid;
      grid-template-columns: minmax(0, 1.5fr) minmax(300px, 1fr);
      gap: 16px;
      align-items: start;
    }
    .columna {
      display: grid;
      gap: 16px;
    }
    .seccion {
      font-size: var(--cd-text-base);
      margin: 0 0 14px;
    }
    .enlace {
      color: var(--cd-color-primary);
      font-weight: 700;
      text-decoration: none;
    }
    .enlace:hover {
      text-decoration: underline;
    }
    .proxima {
      display: grid;
      grid-template-columns: 70px minmax(0, 1fr) auto;
      align-items: center;
      gap: 16px;
    }
    .proxima__fecha {
      display: grid;
      justify-items: center;
      gap: 2px;
      padding: 12px 8px;
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-accent);
      color: var(--cd-color-primary);
    }
    .proxima__fecha strong {
      font-family: var(--cd-font-display);
      font-size: 30px;
      line-height: 1;
    }
    .proxima__fecha span {
      font-size: 10px;
      font-weight: 800;
      letter-spacing: 0.1em;
      text-transform: uppercase;
    }
    .proxima__datos {
      display: grid;
      gap: 5px;
      justify-items: start;
    }
    .proxima__acciones {
      display: grid;
      gap: 8px;
      justify-items: end;
      text-align: right;
    }
    .pendientes {
      display: grid;
      gap: 10px;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .pendientes li {
      display: grid;
      grid-template-columns: 10px minmax(0, 1fr) auto;
      align-items: center;
      gap: 10px;
    }
    .punto {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--cd-color-accent);
    }
    .pendientes div {
      display: grid;
      gap: 2px;
    }
    .quick-actions {
      display: grid;
      gap: 8px;
    }
    .quick-actions a {
      display: flex;
      align-items: center;
      gap: 11px;
      padding: 13px 14px;
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
      font-size: 14px;
    }
    .favoritos,
    .avisos {
      display: grid;
      gap: 10px;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .favoritos a {
      display: flex;
      align-items: center;
      gap: 10px;
      color: inherit;
      text-decoration: none;
    }
    .favoritos a:hover strong {
      color: var(--cd-color-primary);
    }
    .favoritos__icono {
      display: grid;
      place-items: center;
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: var(--cd-color-surface-alt);
      color: var(--cd-color-primary);
    }
    .favoritos span strong,
    .favoritos span small {
      display: block;
    }
    .avisos li {
      display: grid;
      gap: 2px;
      padding-left: 10px;
      border-left: 2px solid var(--cd-color-border);
    }
    .avisos li.avisos__no-leida {
      border-left-color: var(--cd-color-accent);
    }
    @media (max-width: 1000px) {
      .grid {
        grid-template-columns: 1fr;
      }
      .kpis {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }
    @media (max-width: 560px) {
      .kpis {
        grid-template-columns: 1fr;
      }
      .proxima {
        grid-template-columns: 58px minmax(0, 1fr);
      }
      .proxima__acciones {
        grid-column: 2;
        justify-items: start;
        text-align: left;
      }
    }
  `,
})
export class MiResumenComponent implements OnInit {
  private readonly reservasApi = inject(ReservasApi);
  private readonly actividadesApi = inject(ActividadesApi);
  private readonly engagement = inject(EngagementApi);
  private readonly venues = inject(VenuesApi);
  protected readonly auth = inject(AuthStore);

  protected readonly resumen = signal<ResumenPanel | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly reservasActivas = computed(
    () => this.resumen()?.reservas.filter((reserva) => this.esActiva(reserva)) ?? [],
  );
  protected readonly proxima = computed(() => this.reservasActivas()[0] ?? null);
  protected readonly pendientesPago = computed(
    () => this.reservasActivas().filter((reserva) => reserva.estado === 'PENDIENTE'),
  );
  protected readonly pendientes = computed(() => {
    const resumen = this.resumen();
    if (!resumen) {
      return [];
    }
    const items: Array<{ texto: string; detalle: string; ruta: string }> = [];
    if (this.pendientesPago().length) {
      items.push({
        texto: `${this.pendientesPago().length} reserva(s) pendiente(s) de confirmación`,
        detalle: 'El establecimiento debe confirmar tu solicitud.',
        ruta: '/mis-reservas',
      });
    }
    const inscripciones = resumen.inscripciones.filter(
      (inscripcion) => inscripcion.estado === 'CONFIRMADA' && new Date(inscripcion.inicio) > new Date(),
    );
    if (inscripciones.length) {
      items.push({
        texto: `${inscripciones.length} actividad(es) próxima(s)`,
        detalle: 'Revisa fechas y horarios de tus inscripciones.',
        ruta: '/mis-inscripciones',
      });
    }
    const reclamos = resumen.reclamos.filter(
      (reclamo) => reclamo.estado === 'ABIERTO' || reclamo.estado === 'EN_REVISION',
    );
    if (reclamos.length) {
      items.push({
        texto: `${reclamos.length} reclamo(s) en revisión`,
        detalle: 'El equipo de soporte está atendiendo tu caso.',
        ruta: '/reclamos',
      });
    }
    return items;
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected nombreCancha(idCancha: number): string {
    return this.resumen()?.nombresCancha.get(idCancha) ?? `Cancha #${idCancha}`;
  }

  protected monto(reserva: Reserva): number {
    return Math.max(0, reserva.montoCancha - reserva.montoDescuento + (reserva.montoCochera ?? 0));
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    void this.cargarAsync()
      .catch((error) => this.error.set(toApiError(error).message))
      .finally(() => this.cargando.set(false));
  }

  private async cargarAsync(): Promise<void> {
    const datos = await firstValueFrom(
      forkJoin({
        reservas: this.reservasApi.mias(100),
        inscripciones: this.actividadesApi.misInscripciones(),
        reclamos: this.engagement.misReclamos(20),
        notificaciones: this.engagement.notificaciones(5),
        favoritosIds: this.venues.favoritos(),
      }),
    );

    const idsCancha = new Set<number>();
    datos.reservas.filter((reserva) => this.esActiva(reserva)).forEach((reserva) => idsCancha.add(reserva.canchaId));
    const favoritosIds = datos.favoritosIds.slice(0, 3);

    const [detallesReservas, favoritos] = await Promise.all([
      firstValueFrom(
        idsCancha.size === 0
          ? of([])
          : forkJoin(
              [...idsCancha].map((id) =>
                this.venues.detalle(id).pipe(catchError(() => of(null))),
              ),
            ),
      ),
      firstValueFrom(
        favoritosIds.length === 0
          ? of([])
          : forkJoin(favoritosIds.map((id) => this.venues.detalle(id).pipe(catchError(() => of(null))))),
      ),
    ]);

    const nombresCancha = new Map<number, string>();
    detallesReservas.forEach((detalle) => {
      if (detalle) {
        nombresCancha.set(detalle.id, detalle.nombre);
      }
    });
    favoritos.forEach((detalle) => {
      if (detalle) {
        nombresCancha.set(detalle.id, detalle.nombre);
      }
    });

    this.resumen.set({
      reservas: [...datos.reservas].sort(
        (a, b) => new Date(a.inicio).getTime() - new Date(b.inicio).getTime(),
      ),
      inscripciones: datos.inscripciones,
      reclamos: datos.reclamos,
      notificaciones: datos.notificaciones.notificaciones,
      noLeidas: datos.notificaciones.noLeidas,
      favoritos: favoritos.filter((detalle): detalle is CanchaDetalle => detalle !== null),
      nombresCancha,
    });
  }

  private esActiva(reserva: Reserva): boolean {
    return (
      (reserva.estado === 'PENDIENTE' || reserva.estado === 'CONFIRMADA') &&
      new Date(reserva.inicio) > new Date()
    );
  }
}
