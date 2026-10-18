import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
import { EngagementApi, Notificacion } from '../../core/api/engagement.api';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdPaginatorComponent } from '../../shared/ui/pagination/cd-paginator.component';
import { ToastService } from '../../shared/ui/toast/toast.service';

@Component({
  selector: 'app-notificaciones',
  imports: [DatePipe, CdButtonDirective, CdSkeletonComponent, CdEmptyStateComponent, CdErrorStateComponent, CdPaginatorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cd-container cd-page">
      <header class="cd-page__header">
        <div class="cd-row cd-row--between">
          <div>
            <h1 class="cd-page__title">Notificaciones</h1>
            <p class="cd-muted">Tienes {{ noLeidas() }} notificación(es) sin leer.</p>
          </div>
          @if (noLeidas() > 0) {
            <button cdButton variant="secondary" size="sm" type="button" (click)="marcarTodas()">
              Marcar todas como leidas
            </button>
          }
        </div>
      </header>

      @if (cargando()) {
        <div class="cd-stack">
          @for (i of [1, 2, 3, 4]; track i) {
            <cd-skeleton height="90px" radius="12px" />
          }
        </div>
      } @else if (error()) {
        <cd-error-state [message]="error()!" (retry)="cargar()" />
      } @else if (notificaciones().length === 0) {
        <cd-empty-state title="Sin notificaciones" message="Aqui veras avisos de tus reservas, pagos y reclamos." />
      } @else {
        <ul class="lista">
          @for (notificacion of notificacionesPagina(); track notificacion.id) {
            <li class="item" [class.item--nueva]="!notificacion.leida">
              <div class="item__cabecera">
                <h2>{{ notificacion.titulo }}</h2>
                <time class="cd-xs cd-muted" [attr.datetime]="notificacion.fechaEnvio">
                  {{ notificacion.fechaEnvio | date: 'd MMM yyyy, HH:mm' }}
                </time>
              </div>
              <p class="cd-small">{{ notificacion.mensaje }}</p>
              <div class="item__pie">
                @if (!notificacion.leida) {
                  <span class="cd-badge cd-badge--accent">Nueva</span>
                  <button cdButton variant="ghost" size="sm" type="button" (click)="marcar(notificacion)">
                    Marcar como leida
                  </button>
                } @else {
                  <span class="cd-badge">Leida</span>
                }
              </div>
            </li>
          }
        </ul>
        <cd-paginator
          [pagina]="pagina()"
          [total]="notificaciones().length"
          [tamano]="tamanoPagina"
          etiqueta="Paginación de notificaciones"
          (paginaChange)="pagina.set($event)"
        />
      }
    </div>
  `,
  styles: `
    .lista {
      list-style: none;
      padding: 0;
      display: grid;
      gap: var(--cd-space-3);
    }
    .item {
      display: grid;
      gap: var(--cd-space-2);
      padding: var(--cd-space-4);
      background: var(--cd-color-surface);
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-md);
    }
    .item--nueva {
      border-left: 4px solid var(--cd-color-accent);
    }
    .item__cabecera {
      display: flex;
      justify-content: space-between;
      gap: var(--cd-space-3);
      align-items: baseline;
      flex-wrap: wrap;
    }
    .item__cabecera h2 {
      font-size: var(--cd-text-base);
    }
    .item__pie {
      display: flex;
      align-items: center;
      gap: var(--cd-space-2);
    }
  `,
})
export class NotificacionesComponent implements OnInit {
  private readonly api = inject(EngagementApi);
  private readonly toast = inject(ToastService);

  protected readonly notificaciones = signal<Notificacion[]>([]);
  protected readonly noLeidas = signal(0);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly pagina = signal(1);
  protected readonly tamanoPagina = 10;
  protected readonly notificacionesPagina = computed(() => {
    const desde = (this.pagina() - 1) * this.tamanoPagina;
    return this.notificaciones().slice(desde, desde + this.tamanoPagina);
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.notificaciones().subscribe({
      next: (respuesta) => {
        this.notificaciones.set(respuesta.notificaciones);
        this.noLeidas.set(respuesta.noLeidas);
        this.cargando.set(false);
      },
      error: (error) => {
        this.error.set(toApiError(error).message);
        this.cargando.set(false);
      },
    });
  }

  protected marcar(notificacion: Notificacion): void {
    this.api.marcarLeida(notificacion.id).subscribe({
      next: () => {
        this.notificaciones.update((lista) =>
          lista.map((n) => (n.id === notificacion.id ? { ...n, leida: true } : n)),
        );
        this.noLeidas.update((n) => Math.max(0, n - 1));
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  protected marcarTodas(): void {
    const pendientes = this.notificaciones().filter((n) => !n.leida);
    pendientes.forEach((notificacion) => this.marcar(notificacion));
  }
}
