import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
import { ReservasApi } from '../../core/api/reservas.api';
import { EstadoReserva, Reserva, ReservaRecibida } from '../../core/api/reservas.models';
import { OwnerStore } from '../../core/api/owner.store';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { ConfirmService } from '../../shared/ui/confirm/confirm.service';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdPaginatorComponent } from '../../shared/ui/pagination/cd-paginator.component';
import { ToastService } from '../../shared/ui/toast/toast.service';

const ESTADOS: Array<{ valor: EstadoReserva | ''; etiqueta: string }> = [
  { valor: '', etiqueta: 'Todas' },
  { valor: 'PENDIENTE', etiqueta: 'Pendientes' },
  { valor: 'CONFIRMADA', etiqueta: 'Confirmadas' },
  { valor: 'FINALIZADA', etiqueta: 'Finalizadas' },
  { valor: 'CANCELADA', etiqueta: 'Canceladas' },
];

@Component({
  selector: 'app-panel-reservas',
  imports: [
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
    <div class="filtros" role="group" aria-label="Filtrar reservas recibidas">
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
          <cd-skeleton height="110px" radius="12px" />
        }
      </div>
    } @else if (error()) {
      <cd-error-state [message]="error()!" (retry)="cargar()" />
    } @else if (reservas().length === 0) {
      <cd-empty-state title="Sin reservas recibidas" message="Cuando un cliente reserve tus canchas aparecerán aquí." />
    } @else {
      <div class="cd-stack">
        @for (reserva of reservasPagina(); track reserva.id) {
          <article class="cd-card fila">
            <div class="fila__datos">
              <div class="cd-row cd-row--between">
                <strong>{{ nombreCancha(reserva.canchaId) }} · #{{ reserva.id }}</strong>
                <span class="insignias">
                  <span class="cd-badge" [class]="clasePago(reserva.pagoEstado)">{{ etiquetaPago(reserva.pagoEstado) }}</span>
                  <span class="cd-badge" [class]="claseEstado(reserva.estado)">{{ reserva.estado }}</span>
                </span>
              </div>
              @if (reserva.cliente) {
                <p class="cd-small">
                  Cliente: <strong>{{ reserva.cliente }}</strong>
                  @if (reserva.telefono) {
                    ·
                    <a
                      [href]="'tel:' + reserva.telefono"
                      [attr.aria-label]="'Llamar a ' + reserva.cliente + ' al ' + reserva.telefono"
                      >{{ reserva.telefono }}</a
                    >
                  }
                </p>
              }
              <p class="cd-small cd-muted">
                {{ reserva.inicio | date: 'EEEE d MMM, HH:mm' }} - {{ reserva.fin | date: 'HH:mm' }}
              </p>
              <p class="cd-small">
                Cancha {{ reserva.montoCancha | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}
                @if (reserva.montoCochera) {
                  · cochera {{ reserva.montoCochera | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}
                }
                @if (reserva.montoDescuento) {
                  · descuento {{ reserva.montoDescuento | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}
                }
              </p>
            </div>
            <div class="acciones">
              @if (reserva.estado === 'PENDIENTE') {
                <button cdButton size="sm" type="button" (click)="confirmar(reserva)">Confirmar</button>
                <button cdButton variant="danger" size="sm" type="button" (click)="rechazar(reserva)">Rechazar</button>
              }
              @if (reserva.estado === 'CONFIRMADA') {
                <button cdButton variant="danger" size="sm" type="button" (click)="revocar(reserva)">Revocar</button>
              }
            </div>
          </article>
        }
      </div>
      <cd-paginator
        [pagina]="pagina()"
        [total]="reservas().length"
        [tamano]="tamanoPagina"
        etiqueta="Paginación de reservas recibidas"
        (paginaChange)="pagina.set($event)"
      />
    }
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
    .fila {
      display: flex;
      justify-content: space-between;
      gap: var(--cd-space-3);
      align-items: center;
      flex-wrap: wrap;
    }
    .fila__datos {
      display: grid;
      gap: var(--cd-space-1);
    }
    .acciones {
      display: flex;
      gap: var(--cd-space-2);
      flex-wrap: wrap;
    }
    .insignias {
      display: flex;
      gap: var(--cd-space-1);
      flex-wrap: wrap;
      justify-content: flex-end;
    }
  `,
})
export class PanelReservasComponent implements OnInit {
  private readonly api = inject(ReservasApi);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  protected readonly store = inject(OwnerStore);

  protected readonly estados = ESTADOS;
  protected readonly reservas = signal<ReservaRecibida[]>([]);
  protected readonly filtro = signal<EstadoReserva | ''>('');
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly pagina = signal(1);
  protected readonly tamanoPagina = 10;
  protected readonly reservasPagina = computed(() => {
    const desde = (this.pagina() - 1) * this.tamanoPagina;
    return this.reservas().slice(desde, desde + this.tamanoPagina);
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cambiarFiltro(estado: EstadoReserva | ''): void {
    this.filtro.set(estado);
    this.pagina.set(1);
    this.cargar();
  }

  protected nombreCancha(id: number): string {
    return this.store.canchas().find((cancha) => cancha.id === id)?.nombre ?? `Cancha #${id}`;
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

  /** Ultimo pago de la reserva, en palabras: es lo que el propietario necesita antes de atenderla. */
  protected etiquetaPago(pagoEstado: ReservaRecibida['pagoEstado']): string {
    switch (pagoEstado) {
      case 'APROBADO':
        return 'Pagado';
      case 'RECHAZADO':
        return 'Pago rechazado';
      case 'REEMBOLSADO':
        return 'Reembolsado';
      case 'PENDIENTE':
        return 'Pago pendiente';
      default:
        return 'Sin pago';
    }
  }

  protected clasePago(pagoEstado: ReservaRecibida['pagoEstado']): string {
    switch (pagoEstado) {
      case 'APROBADO':
        return 'cd-badge cd-badge--success';
      case 'RECHAZADO':
        return 'cd-badge cd-badge--danger';
      case 'REEMBOLSADO':
        return 'cd-badge cd-badge--info';
      case 'PENDIENTE':
        return 'cd-badge cd-badge--warning';
      default:
        return 'cd-badge';
    }
  }

  protected async confirmar(reserva: Reserva): Promise<void> {
    const ok = await this.confirm.confirm({
      title: 'Confirmar reserva',
      message: `Confirma la reserva #${reserva.id}. El cliente recibira el aviso.`,
      confirmText: 'Confirmar',
    });
    if (!ok) {
      return;
    }
    this.api.confirmar(reserva.id).subscribe({
      next: (actualizada) => this.reemplazar(actualizada, 'Reserva confirmada.'),
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  protected async rechazar(reserva: Reserva): Promise<void> {
    const ok = await this.confirm.confirm({
      title: 'Rechazar reserva',
      message: 'La franja se libera y, si corresponde, se aplica el reembolso.',
      confirmText: 'Rechazar',
      danger: true,
    });
    if (!ok) {
      return;
    }
    this.api.rechazar(reserva.id, 'No disponible').subscribe({
      next: (actualizada) => this.reemplazar(actualizada, 'Reserva rechazada.'),
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  protected async revocar(reserva: Reserva): Promise<void> {
    const ok = await this.confirm.confirm({
      title: 'Revocar reserva confirmada',
      message: 'Se aplicará un reembolso total al cliente. Esta acción no se puede deshacer.',
      confirmText: 'Revocar',
      danger: true,
    });
    if (!ok) {
      return;
    }
    this.api.revocar(reserva.id, 'Revocada por el propietario').subscribe({
      next: (actualizada) => this.reemplazar(actualizada, 'Reserva revocada.'),
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.recibidas(this.filtro() || null).subscribe({
      next: (reservas) => {
        this.reservas.set(reservas);
        this.cargando.set(false);
      },
      error: (error) => {
        this.error.set(toApiError(error).message);
        this.cargando.set(false);
      },
    });
  }

  /** La respuesta de confirmar/rechazar/revocar es una reserva sin titular: se conserva la fila. */
  private reemplazar(actualizada: Reserva, mensaje: string): void {
    this.reservas.update((lista) =>
      lista.map((r) => (r.id === actualizada.id ? { ...r, ...actualizada } : r)),
    );
    this.toast.success(mensaje);
  }
}
