import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ReservasApi } from '../../core/api/reservas.api';
import { EstadoReserva, Reserva } from '../../core/api/reservas.models';
import { VenuesApi } from '../../core/api/venues.api';
import { toApiError } from '../../core/http/api-error';
import { ConfirmService } from '../../shared/ui/confirm/confirm.service';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdPaginatorComponent } from '../../shared/ui/pagination/cd-paginator.component';
import { ToastService } from '../../shared/ui/toast/toast.service';

const ESTADOS: Array<{ valor: EstadoReserva | 'TODAS'; etiqueta: string }> = [
  { valor: 'TODAS', etiqueta: 'Todas' },
  { valor: 'PENDIENTE', etiqueta: 'Pendientes' },
  { valor: 'CONFIRMADA', etiqueta: 'Confirmadas' },
  { valor: 'FINALIZADA', etiqueta: 'Finalizadas' },
  { valor: 'CANCELADA', etiqueta: 'Canceladas' },
];

@Component({
  selector: 'app-mis-reservas',
  imports: [
    RouterLink,
    CurrencyPipe,
    DatePipe,
    CdButtonDirective,
    CdSkeletonComponent,
    CdEmptyStateComponent,
    CdErrorStateComponent,
    CdPaginatorComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cd-container cd-page">
      <header class="cd-page__header">
        <h1 class="cd-page__title">Mis reservas</h1>
        <p class="cd-muted">Consulta el estado de tus reservas, paga pendientes o cancela si aún puedes.</p>
      </header>

      <div class="filtros" role="group" aria-label="Filtrar reservas por estado">
        @for (estado of estados; track estado.valor) {
          <button
            type="button"
            class="chip"
            [class.chip--activo]="filtro() === estado.valor"
            [attr.aria-pressed]="filtro() === estado.valor"
            (click)="cambiarFiltro(estado.valor)"
          >
            {{ estado.etiqueta }}
          </button>
        }
      </div>

      @if (cargando()) {
        <div class="cd-stack">
          @for (i of [1, 2, 3]; track i) {
            <cd-skeleton height="120px" radius="12px" />
          }
        </div>
      } @else if (error()) {
        <cd-error-state [message]="error()!" (retry)="cargar()" />
      } @else if (visibles().length === 0) {
        <cd-empty-state title="Sin reservas" message="Cuando reserves una cancha aparecerá aquí.">
          <a routerLink="/canchas" class="cd-btn cd-btn--primary">Buscar canchas</a>
        </cd-empty-state>
      } @else {
        <div class="cd-stack">
          @for (reserva of visiblesPagina(); track reserva.id) {
            <article class="reserva">
              <div class="reserva__principal">
                <div>
                  <h2 class="reserva__titulo">{{ nombreCancha(reserva.canchaId) }}</h2>
                  <p class="cd-small cd-muted">
                    {{ reserva.inicio | date: 'EEEE d MMM yyyy' }} ·
                    {{ reserva.inicio | date: 'HH:mm' }} - {{ reserva.fin | date: 'HH:mm' }}
                  </p>
                </div>
                <span class="cd-badge" [class]="claseEstado(reserva.estado)">{{ etiquetaEstado(reserva.estado) }}</span>
              </div>

              <div class="reserva__detalle">
                <span class="cd-small">
                  Cancha:
                  <strong>{{ reserva.montoCancha | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</strong>
                  @if (reserva.montoDescuento) {
                    · descuento
                    <strong>-{{ reserva.montoDescuento | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</strong>
                  }
                  @if (reserva.montoCochera) {
                    · cochera
                    <strong>{{ reserva.montoCochera | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</strong>
                  }
                </span>
                <span class="cd-xs cd-muted">Reserva #{{ reserva.id }}</span>
              </div>

              <div class="reserva__acciones">
                <a [routerLink]="['/mis-reservas', reserva.id]" class="cd-btn cd-btn--secondary cd-btn--sm">
                  Ver detalle
                </a>
                @if (reserva.estado === 'PENDIENTE') {
                  <a [routerLink]="['/mis-reservas', reserva.id]" class="cd-btn cd-btn--accent cd-btn--sm">
                    Pagar
                  </a>
                }
                @if (puedeCancelar(reserva)) {
                  <button cdButton variant="danger" size="sm" type="button" (click)="cancelar(reserva)">
                    Cancelar
                  </button>
                }
              </div>
            </article>
          }
        </div>
        <cd-paginator
          [pagina]="pagina()"
          [total]="visibles().length"
          [tamano]="tamanoPagina"
          etiqueta="Paginación de mis reservas"
          (paginaChange)="pagina.set($event)"
        />
      }
    </div>
  `,
  styles: `
    .filtros {
      display: flex;
      gap: var(--cd-space-2);
      flex-wrap: wrap;
      margin-bottom: var(--cd-space-4);
    }
    .chip {
      padding: 8px 14px;
      border-radius: var(--cd-radius-pill);
      border: 1px solid var(--cd-color-border);
      background: var(--cd-color-surface);
      cursor: pointer;
      font-size: var(--cd-text-sm);
      font-weight: 600;
      color: var(--cd-color-text-muted);
    }
    .chip--activo {
      background: var(--cd-color-primary);
      border-color: var(--cd-color-primary);
      color: var(--cd-color-on-primary);
    }
    .reserva {
      display: grid;
      gap: var(--cd-space-2);
      padding: var(--cd-space-4);
      background: var(--cd-color-surface);
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-md);
    }
    .reserva__principal {
      display: flex;
      justify-content: space-between;
      align-items: start;
      gap: var(--cd-space-3);
      flex-wrap: wrap;
    }
    .reserva__titulo {
      font-size: var(--cd-text-base);
    }
    .reserva__detalle {
      display: flex;
      justify-content: space-between;
      gap: var(--cd-space-2);
      flex-wrap: wrap;
    }
    .reserva__acciones {
      display: flex;
      gap: var(--cd-space-2);
      flex-wrap: wrap;
      padding-top: var(--cd-space-2);
      border-top: 1px solid var(--cd-color-border);
    }
    .reserva__acciones .cd-btn {
      text-decoration: none;
    }
  `,
})
export class MisReservasComponent implements OnInit {
  private readonly api = inject(ReservasApi);
  private readonly venues = inject(VenuesApi);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  protected readonly estados = ESTADOS;
  protected readonly reservas = signal<Reserva[]>([]);
  protected readonly nombres = signal<Record<number, string>>({});
  protected readonly filtro = signal<EstadoReserva | 'TODAS'>('TODAS');
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly pagina = signal(1);
  protected readonly tamanoPagina = 10;
  protected readonly visibles = computed<Reserva[]>(() => {
    const filtro = this.filtro();
    return filtro === 'TODAS' ? this.reservas() : this.reservas().filter((r) => r.estado === filtro);
  });
  protected readonly visiblesPagina = computed(() => {
    const desde = (this.pagina() - 1) * this.tamanoPagina;
    return this.visibles().slice(desde, desde + this.tamanoPagina);
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cambiarFiltro(estado: EstadoReserva | 'TODAS'): void {
    this.filtro.set(estado);
    this.pagina.set(1);
  }

  protected nombreCancha(id: number): string {
    return this.nombres()[id] ?? `Cancha #${id}`;
  }

  protected claseEstado(estado: EstadoReserva): string {
    const mapa: Record<EstadoReserva, string> = {
      PENDIENTE: 'cd-badge cd-badge--warning',
      CONFIRMADA: 'cd-badge cd-badge--success',
      FINALIZADA: 'cd-badge cd-badge--info',
      CANCELADA: 'cd-badge',
      RECHAZADA: 'cd-badge cd-badge--danger',
      EXPIRADA: 'cd-badge',
    };
    return mapa[estado];
  }

  protected etiquetaEstado(estado: EstadoReserva): string {
    return estado.charAt(0) + estado.slice(1).toLowerCase();
  }

  protected puedeCancelar(reserva: Reserva): boolean {
    const faltante = new Date(reserva.inicio).getTime() - Date.now();
    return (reserva.estado === 'PENDIENTE' || reserva.estado === 'CONFIRMADA') && faltante > 2 * 3_600_000;
  }

  protected async cancelar(reserva: Reserva): Promise<void> {
    const ok = await this.confirm.confirm({
      title: 'Cancelar reserva',
      message: `Se cancelara la reserva #${reserva.id}. Si corresponde, el reembolso se procesa automaticamente.`,
      confirmText: 'Si, cancelar',
      cancelText: 'Volver',
      danger: true,
    });
    if (!ok) {
      return;
    }
    this.api.cancelar(reserva.id).subscribe({
      next: (actualizada) => {
        this.reservas.update((lista) => lista.map((r) => (r.id === actualizada.id ? actualizada : r)));
        this.toast.success('Reserva cancelada.');
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.mias().subscribe({
      next: (reservas) => {
        this.reservas.set(reservas);
        this.cargando.set(false);
        void this.cargarNombres(reservas);
      },
      error: (error) => {
        this.error.set(toApiError(error).message);
        this.cargando.set(false);
      },
    });
  }

  private async cargarNombres(reservas: Reserva[]): Promise<void> {
    const ids = [...new Set(reservas.map((r) => r.canchaId))].filter((id) => !this.nombres()[id]);
    const resultados = await Promise.allSettled(ids.map((id) => firstValueFrom(this.venues.detalle(id))));
    const mapa = { ...this.nombres() };
    resultados.forEach((resultado) => {
      if (resultado.status === 'fulfilled') {
        mapa[resultado.value.id] = resultado.value.nombre;
      }
    });
    this.nombres.set(mapa);
  }
}
