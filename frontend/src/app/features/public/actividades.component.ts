import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ActividadesApi } from '../../core/api/actividades.api';
import { Actividad, Inscripcion } from '../../core/api/actividades.models';
import { AuthStore } from '../../core/auth/auth.store';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdIconComponent } from '../../shared/ui/icon/cd-icon.component';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { ToastService } from '../../shared/ui/toast/toast.service';

@Component({
  selector: 'app-actividades',
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
    <section class="hero-actividades">
      <div class="cd-container">
        <p class="eyebrow">Actividades y comunidad</p>
        <h1>El deporte se vive <em>en equipo.</em></h1>
        <p>Torneos, clases y partidos abiertos publicados por los espacios deportivos.</p>
      </div>
    </section>

    <div class="cd-container contenido">
      <div class="encabezado">
        <div>
          <p class="eyebrow-oscuro">Próximas actividades</p>
          <h2>{{ actividades().length }} {{ actividades().length === 1 ? 'actividad disponible' : 'actividades disponibles' }}</h2>
        </div>
        @if (auth.isAuthenticated()) {
          <a routerLink="/mis-inscripciones" cdButton variant="secondary" size="sm">
            Mis inscripciones
            @if (inscripciones().length) {
              <span class="contador">{{ inscripciones().length }}</span>
            }
          </a>
        }
      </div>

      @if (cargando()) {
        <div class="grid" aria-busy="true">
          @for (item of [1, 2, 3]; track item) {
            <cd-skeleton height="260px" radius="16px" />
          }
        </div>
      } @else if (error()) {
        <cd-error-state [message]="error()!" (retry)="cargar()" />
      } @else if (actividades().length === 0) {
        <cd-empty-state
          title="Aún no hay actividades publicadas"
          message="Los espacios deportivos publicarán aquí sus torneos, clases y partidos abiertos."
        >
          <a routerLink="/canchas" cdButton variant="secondary" size="sm">Explorar canchas</a>
        </cd-empty-state>
      } @else {
        <div class="grid">
          @for (actividad of actividades(); track actividad.id) {
            <article class="actividad">
              <div class="actividad__fecha" aria-hidden="true">
                <strong>{{ actividad.inicio | date: 'd' }}</strong>
                <span>{{ actividad.inicio | date: 'MMM' }}</span>
              </div>
              <div class="actividad__cuerpo">
                <div class="cd-row actividad__tags">
                  <span class="cd-badge">{{ actividad.distrito }}</span>
                  @if (actividad.cuposDisponibles <= 5) {
                    <span class="cd-badge cd-badge--warning">Últimos {{ actividad.cuposDisponibles }} cupos</span>
                  } @else {
                    <span class="cd-badge cd-badge--success">{{ actividad.cuposDisponibles }} cupos libres</span>
                  }
                  @if (esInscrito(actividad.id)) {
                    <span class="cd-badge cd-badge--accent"><cd-icon name="check" [size]="11" /> Inscrito</span>
                  }
                </div>
                <h3>{{ actividad.nombre }}</h3>
                @if (actividad.descripcion) {
                  <p class="cd-small cd-muted descripcion">{{ actividad.descripcion }}</p>
                }
                <p class="meta cd-small cd-muted">
                  <cd-icon name="calendar" [size]="14" />
                  {{ actividad.inicio | date: 'EEE d MMM, HH:mm' }} - {{ actividad.fin | date: 'HH:mm' }}
                </p>
                <p class="meta cd-small cd-muted">
                  <cd-icon name="pin" [size]="14" />
                  <a class="enlace" [routerLink]="['/establecimientos', actividad.idEstablecimiento]">
                    {{ actividad.establecimiento }}
                  </a>
                </p>
                <div class="actividad__pie">
                  <span class="precio">
                    @if (actividad.precio > 0) {
                      <strong>{{ actividad.precio | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</strong>
                      <small>por persona</small>
                    } @else {
                      <strong>Gratis</strong>
                    }
                  </span>
                  @if (!auth.isAuthenticated()) {
                    <a
                      [routerLink]="['/auth/login']"
                      [queryParams]="{ redirect: '/actividades' }"
                      cdButton
                      variant="secondary"
                      size="sm"
                    >
                      Ingresa para inscribirte
                    </a>
                  } @else if (esInscrito(actividad.id)) {
                    <button
                      cdButton
                      variant="secondary"
                      size="sm"
                      type="button"
                      [loading]="accionando() === actividad.id"
                      (click)="cancelarInscripcion(actividad)"
                    >
                      Cancelar inscripción
                    </button>
                  } @else if (actividad.cuposDisponibles === 0) {
                    <button cdButton variant="secondary" size="sm" type="button" disabled>Sin cupos</button>
                  } @else {
                    <button
                      cdButton
                      variant="primary"
                      size="sm"
                      type="button"
                      [loading]="accionando() === actividad.id"
                      (click)="inscribirse(actividad)"
                    >
                      Inscribirme
                    </button>
                  }
                </div>
              </div>
            </article>
          }
        </div>
      }

      <div class="aviso">
        <div>
          <strong>¿Organizas una actividad?</strong>
          <p>Publica torneos, clases o partidos abiertos desde el panel de tu espacio deportivo.</p>
        </div>
        <a routerLink="/panel/actividades" cdButton variant="secondary" size="sm">Ir al panel</a>
      </div>
    </div>
  `,
  styles: `
    .hero-actividades {
      padding: clamp(56px, 8vw, 96px) 0 clamp(48px, 6vw, 76px);
      background: var(--cd-color-inverse-bg);
      color: var(--cd-color-inverse-text);
    }
    .eyebrow {
      margin: 0 0 14px;
      color: var(--cd-color-brand-light);
      font-size: 11px;
      font-weight: 800;
      letter-spacing: 0.2em;
      text-transform: uppercase;
    }
    h1 {
      margin: 0;
      font-size: clamp(42px, 5.4vw, 70px);
      letter-spacing: -0.05em;
    }
    h1 em {
      color: var(--cd-color-brand-light);
      font-style: normal;
    }
    .hero-actividades p:last-child {
      max-width: 46ch;
      margin: 20px 0 0;
      color: var(--cd-color-inverse-muted);
    }
    .contenido {
      padding-block: 40px 70px;
    }
    .encabezado {
      display: flex;
      align-items: end;
      justify-content: space-between;
      gap: 18px;
      flex-wrap: wrap;
      margin-bottom: 20px;
    }
    .eyebrow-oscuro {
      margin: 0 0 6px;
      color: var(--cd-color-teal, #167a70);
      font-size: 10px;
      font-weight: 800;
      letter-spacing: 0.16em;
      text-transform: uppercase;
    }
    .encabezado h2 {
      margin: 0;
      font-size: clamp(24px, 3vw, 34px);
      letter-spacing: -0.03em;
    }
    .contador {
      display: inline-grid;
      place-items: center;
      min-width: 20px;
      height: 20px;
      margin-left: 6px;
      padding: 0 6px;
      border-radius: 999px;
      background: var(--cd-color-accent-soft);
      color: var(--cd-color-text);
      font-size: 11px;
      font-weight: 800;
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 16px;
    }
    .actividad {
      display: grid;
      grid-template-columns: 82px minmax(0, 1fr);
      overflow: hidden;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-lg);
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-1);
    }
    .actividad__fecha {
      display: grid;
      align-content: center;
      justify-items: center;
      gap: 2px;
      padding: 18px 10px;
      background: var(--cd-color-accent-soft);
      color: var(--cd-color-text);
    }
    .actividad__fecha strong {
      font-family: var(--cd-font-display);
      font-size: 32px;
      line-height: 1;
    }
    .actividad__fecha span {
      font-size: 11px;
      font-weight: 800;
      letter-spacing: 0.1em;
      text-transform: uppercase;
    }
    .actividad__cuerpo {
      display: grid;
      align-content: start;
      gap: 8px;
      padding: 18px;
    }
    .actividad__tags {
      flex-wrap: wrap;
    }
    .actividad h3 {
      margin: 0;
      font-size: 22px;
      letter-spacing: -0.02em;
    }
    .descripcion {
      margin: 0;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
    .meta {
      display: flex;
      align-items: center;
      gap: 6px;
      margin: 0;
    }
    .enlace {
      color: inherit;
      font-weight: 600;
      text-decoration: none;
    }
    .enlace:hover {
      color: var(--cd-color-primary);
      text-decoration: underline;
    }
    .actividad__pie {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
      margin-top: 6px;
    }
    .precio strong {
      font-size: 20px;
    }
    .precio small {
      color: var(--cd-color-text-muted);
      font-size: 11px;
    }
    .aviso {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 18px;
      flex-wrap: wrap;
      margin-top: 28px;
      padding: 22px 24px;
      border-radius: var(--cd-radius-md);
      background: var(--cd-color-surface-alt);
    }
    .aviso p {
      margin: 4px 0 0;
      color: var(--cd-color-text-muted);
      font-size: 13px;
    }
    @media (max-width: 900px) {
      .grid {
        grid-template-columns: 1fr;
      }
    }
    @media (max-width: 520px) {
      .actividad {
        grid-template-columns: 64px minmax(0, 1fr);
      }
      .actividad__fecha strong {
        font-size: 26px;
      }
    }
  `,
})
export class ActividadesComponent implements OnInit {
  private readonly api = inject(ActividadesApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly auth = inject(AuthStore);
  protected readonly actividades = signal<Actividad[]>([]);
  protected readonly inscripciones = signal<Inscripcion[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly accionando = signal<number | null>(null);

  private readonly idsInscritos = computed(
    () =>
      new Set(
        this.inscripciones()
          .filter((inscripcion) => inscripcion.estado === 'CONFIRMADA')
          .map((inscripcion) => inscripcion.idActividad),
      ),
  );

  ngOnInit(): void {
    void this.cargar();
  }

  protected esInscrito(idActividad: number): boolean {
    return this.idsInscritos().has(idActividad);
  }

  protected async inscribirse(actividad: Actividad): Promise<void> {
    if (!this.auth.isAuthenticated()) {
      await this.router.navigate(['/auth/login'], { queryParams: { redirect: '/actividades' } });
      return;
    }
    this.accionando.set(actividad.id);
    try {
      await firstValueFrom(this.api.inscribir(actividad.id));
      this.toast.success('Inscripción confirmada.');
      await this.cargar();
    } catch (error) {
      this.toast.error(toApiError(error).message);
    } finally {
      this.accionando.set(null);
    }
  }

  protected async cancelarInscripcion(actividad: Actividad): Promise<void> {
    this.accionando.set(actividad.id);
    try {
      await firstValueFrom(this.api.cancelarInscripcion(actividad.id));
      this.toast.success('Inscripción cancelada.');
      await this.cargar();
    } catch (error) {
      this.toast.error(toApiError(error).message);
    } finally {
      this.accionando.set(null);
    }
  }

  protected cargar(): void {
    void this.cargarAsync();
  }

  private async cargarAsync(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      const actividades = await firstValueFrom(this.api.publicas());
      this.actividades.set(actividades);
      if (this.auth.isAuthenticated()) {
        this.inscripciones.set(await firstValueFrom(this.api.misInscripciones()));
      } else {
        this.inscripciones.set([]);
      }
    } catch (error) {
      this.error.set(toApiError(error).message);
    } finally {
      this.cargando.set(false);
    }
  }
}
