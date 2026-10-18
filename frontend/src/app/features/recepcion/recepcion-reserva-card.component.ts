import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ReservaRecepcion } from '../../core/api/recepcion.api';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';

/** Tarjeta de reserva en el portal de recepcion, con acciones de check-in y cobro. */
@Component({
  selector: 'cd-recepcion-reserva',
  imports: [CurrencyPipe, DatePipe, CdButtonDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="cd-card fila">
      <div class="fila__datos">
        <div class="cd-row cd-row--between">
          <strong>{{ reserva().cancha }} · #{{ reserva().id }}</strong>
          <span class="cd-badge" [class]="claseEstado()">{{ reserva().estado }}</span>
        </div>
        <p class="cd-small cd-muted">{{ reserva().establecimiento }}</p>
        <p class="cd-small">
          {{ reserva().inicio | date: 'EEE d MMM, HH:mm' }} - {{ reserva().fin | date: 'HH:mm' }}
          · {{ reserva().cliente }}
          @if (reserva().telefono) {
            · <span class="cd-muted">{{ reserva().telefono }}</span>
          }
        </p>
        <p class="cd-small">
          {{ reserva().total | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}
          · <span [class]="clasePago()">{{ etiquetaPago() }}</span>
        </p>
      </div>
      <div class="acciones">
        @if (puedeCobrar()) {
          <button cdButton size="sm" type="button" (click)="cobrar.emit(reserva())">Registrar cobro</button>
        }
        @if (reserva().estado === 'CONFIRMADA' && !reserva().pagoEstado) {
          <button cdButton variant="secondary" size="sm" type="button" (click)="checkIn.emit(reserva())">
            Check-in
          </button>
        }
      </div>
    </article>
  `,
  styles: `
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
      align-items: center;
      flex-wrap: wrap;
    }
    .pago--ok {
      color: #1c7c46;
    }
    .pago--pendiente {
      color: #a4620a;
    }
  `,
})
export class RecepcionReservaCardComponent {
  readonly reserva = input.required<ReservaRecepcion>();
  readonly checkIn = output<ReservaRecepcion>();
  readonly cobrar = output<ReservaRecepcion>();

  protected readonly pagoEstado = computed(() => this.reserva().pagoEstado);

  protected claseEstado(): string {
    const mapa: Record<string, string> = {
      PENDIENTE: 'cd-badge cd-badge--warning',
      CONFIRMADA: 'cd-badge cd-badge--success',
      FINALIZADA: 'cd-badge cd-badge--info',
      CANCELADA: 'cd-badge',
      RECHAZADA: 'cd-badge cd-badge--danger',
      EXPIRADA: 'cd-badge',
    };
    return mapa[this.reserva().estado] ?? 'cd-badge';
  }

  protected clasePago(): string {
    return this.pagoEstado() === 'APROBADO' ? 'pago--ok' : 'pago--pendiente';
  }

  protected etiquetaPago(): string {
    if (this.pagoEstado() === 'APROBADO') {
      return 'Pagado';
    }
    return this.pagoEstado() ? this.pagoEstado()! : 'Sin pago';
  }

  protected puedeCobrar(): boolean {
    const estado = this.reserva().estado;
    return (
      this.pagoEstado() !== 'APROBADO' && (estado === 'PENDIENTE' || estado === 'CONFIRMADA')
    );
  }
}
