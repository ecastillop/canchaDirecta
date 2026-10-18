import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { EngagementApi, Calificacion } from '../../core/api/engagement.api';
import { PagosApi } from '../../core/api/pagos.api';
import { ReservasApi } from '../../core/api/reservas.api';
import { Pago, Reserva } from '../../core/api/reservas.models';
import { VenuesApi } from '../../core/api/venues.api';
import { CanchaDetalle } from '../../core/api/venues.models';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { ConfirmService } from '../../shared/ui/confirm/confirm.service';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { ToastService } from '../../shared/ui/toast/toast.service';

@Component({
  selector: 'app-reserva-detalle',
  imports: [
    RouterLink,
    FormsModule,
    ReactiveFormsModule,
    CurrencyPipe,
    DatePipe,
    CdButtonDirective,
    CdInputDirective,
    CdSkeletonComponent,
    CdErrorStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cd-container cd-page">
      <nav class="migas cd-small cd-muted" aria-label="Ruta de navegación">
        <a routerLink="/mis-reservas">Mis reservas</a> / <span>#{{ id() }}</span>
      </nav>

      @if (cargando()) {
        <cd-skeleton height="280px" radius="12px" />
      } @else if (error()) {
        <cd-error-state [message]="error()!" [retryable]="false" />
      } @else if (reserva(); as r) {
        <div class="columnas">
          <section class="cd-card">
            <div class="cabecera">
              <div>
                <h1 class="cd-page__title">{{ cancha()?.nombre ?? 'Cancha #' + r.canchaId }}</h1>
                @if (cancha(); as c) {
                  <p class="cd-muted">{{ c.establecimiento }} · {{ c.distrito.nombre }}</p>
                }
              </div>
              <span class="cd-badge" [class]="claseEstado()">{{ r.estado }}</span>
            </div>

            <dl class="datos">
              <div>
                <dt>Fecha y hora</dt>
                <dd>
                  {{ r.inicio | date: 'EEEE d MMMM yyyy' }}, {{ r.inicio | date: 'HH:mm' }} -
                  {{ r.fin | date: 'HH:mm' }}
                </dd>
              </div>
              <div>
                <dt>Reserva creada</dt>
                <dd>{{ r.fechaCreacion | date: 'd MMM yyyy, HH:mm' }}</dd>
              </div>
              <div>
                <dt>Monto cancha</dt>
                <dd>{{ r.montoCancha | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</dd>
              </div>
              @if (r.montoDescuento) {
                <div>
                  <dt>Descuento</dt>
                  <dd>-{{ r.montoDescuento | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</dd>
                </div>
              }
              @if (r.montoCochera) {
                <div>
                  <dt>Cochera</dt>
                  <dd>{{ r.montoCochera | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</dd>
                </div>
              }
              <div class="total">
                <dt>Total</dt>
                <dd>
                  {{ r.montoCancha + (r.montoCochera ?? 0) - r.montoDescuento | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}
                </dd>
              </div>
            </dl>

            <div class="acciones">
              <a routerLink="/mis-reservas" class="cd-btn cd-btn--ghost">Volver</a>
              @if (puedeCancelar()) {
                <button cdButton variant="danger" type="button" (click)="cancelar()">Cancelar reserva</button>
              }
            </div>
          </section>

          <aside class="cd-card">
            <h2 class="seccion">Pago</h2>
            @if (pago(); as p) {
              <dl class="datos">
                <div>
                  <dt>Estado</dt>
                  <dd><span class="cd-badge" [class]="clasePago(p.estado)">{{ p.estado }}</span></dd>
                </div>
                <div>
                  <dt>Monto</dt>
                  <dd>{{ p.monto | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</dd>
                </div>
                <div>
                  <dt>Medio</dt>
                  <dd>{{ p.medioPago }}</dd>
                </div>
                <div>
                  <dt>Fecha</dt>
                  <dd>{{ p.fechaPago | date: 'd MMM yyyy, HH:mm' }}</dd>
                </div>
                @if (p.codigoOperacion) {
                  <div>
                    <dt>Operación</dt>
                    <dd>{{ p.codigoOperacion }}</dd>
                  </div>
                }
              </dl>
              @if (p.reembolsos.length) {
                <h3 class="subseccion">Reembolsos</h3>
                <ul class="reembolsos">
                  @for (reembolso of p.reembolsos; track reembolso.id) {
                    <li>
                      <span>{{ reembolso.motivo }}</span>
                      <strong>{{ reembolso.monto | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</strong>
                      <span class="cd-badge" [class]="reembolso.estado === 'PROCESADO' ? 'cd-badge--success' : 'cd-badge--info'">
                        {{ reembolso.estado }}
                      </span>
                    </li>
                  }
                </ul>
              }
            } @else if (puedePagar()) {
              <p class="cd-small cd-muted">Esta reserva aún no tiene pago. Completa el pago simulado.</p>
              @if (errorPago()) {
                <p class="alerta" role="alert">{{ errorPago() }}</p>
              }
              <form class="cd-stack" [formGroup]="pagoForm" (ngSubmit)="pagar()" novalidate>
                <label class="cd-field">
                  <span class="cd-field__label">Medio de pago</span>
                  <select cdInput formControlName="medioPago">
                    <option value="TARJETA">Tarjeta (demo)</option>
                    <option value="YAPE">Yape (demo)</option>
                    <option value="PLIN">Plin (demo)</option>
                    <option value="EFECTIVO">Efectivo en local</option>
                  </select>
                </label>
                <label class="cd-field">
                  <span class="cd-field__label">Código de operación <span class="optional">(opcional)</span></span>
                  <input cdInput formControlName="codigoOperacion" />
                </label>
                <button cdButton variant="accent" type="submit" [loading]="pagando()" [block]="true">
                  Pagar ahora
                </button>
              </form>
            } @else {
              <p class="cd-small cd-muted">Esta reserva no requiere pago en su estado actual.</p>
            }
          </aside>

          @if (r.estado === 'FINALIZADA') {
            <section class="cd-card calificacion" aria-labelledby="calificacion-titulo">
              <h2 id="calificacion-titulo" class="seccion">Tu calificación</h2>
              @if (calificacionPropia(); as calificacion) {
                <p class="estrellas" [attr.aria-label]="'Calificacion ' + calificacion.puntaje + ' de 5'">
                  {{ estrellas(calificacion.puntaje) }}
                </p>
                @if (calificacion.comentario) {
                  <p class="cd-small">{{ calificacion.comentario }}</p>
                }
              } @else {
                <p class="cd-small cd-muted">Cuentanos como fue tu experiencia en esta cancha.</p>
                @if (errorCalificacion()) {
                  <p class="alerta" role="alert">{{ errorCalificacion() }}</p>
                }
                <div class="estrellas" role="radiogroup" aria-label="Puntaje de 1 a 5">
                  @for (valor of [1, 2, 3, 4, 5]; track valor) {
                    <button
                      type="button"
                      role="radio"
                      [attr.aria-checked]="puntaje() === valor"
                      [attr.aria-label]="valor + ' de 5'"
                      [class.activa]="puntaje() >= valor"
                      (click)="puntaje.set(valor)"
                    >
                      ★
                    </button>
                  }
                </div>
                <label class="cd-field">
                  <span class="cd-field__label">Comentario <span class="optional">(opcional)</span></span>
                  <textarea cdInput [(ngModel)]="comentario" rows="3" maxlength="500"></textarea>
                </label>
                <button cdButton type="button" [loading]="calificando()" (click)="calificar()">
                  Enviar calificación
                </button>
              }
            </section>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .migas {
      margin-bottom: var(--cd-space-3);
    }
    .columnas {
      display: grid;
      gap: var(--cd-space-4);
    }
    .cabecera {
      display: flex;
      justify-content: space-between;
      align-items: start;
      gap: var(--cd-space-3);
      margin-bottom: var(--cd-space-3);
      flex-wrap: wrap;
    }
    .seccion {
      font-size: var(--cd-text-base);
      margin-bottom: var(--cd-space-3);
    }
    .subseccion {
      font-size: var(--cd-text-sm);
      margin-top: var(--cd-space-4);
      margin-bottom: var(--cd-space-2);
    }
    .datos {
      display: grid;
      gap: var(--cd-space-2);
      margin: 0;
    }
    .datos > div {
      display: flex;
      justify-content: space-between;
      gap: var(--cd-space-3);
      font-size: var(--cd-text-sm);
    }
    .datos dt {
      color: var(--cd-color-text-muted);
    }
    .datos dd {
      margin: 0;
      font-weight: 700;
      text-align: right;
    }
    .total {
      border-top: 1px solid var(--cd-color-border);
      padding-top: var(--cd-space-2);
      font-size: var(--cd-text-base);
    }
    .acciones {
      display: flex;
      gap: var(--cd-space-2);
      flex-wrap: wrap;
      margin-top: var(--cd-space-4);
    }
    .acciones .cd-btn {
      text-decoration: none;
    }
    .reembolsos {
      list-style: none;
      padding: 0;
      display: grid;
      gap: var(--cd-space-2);
      font-size: var(--cd-text-sm);
    }
    .reembolsos li {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--cd-space-2);
      flex-wrap: wrap;
    }
    .alerta {
      margin-bottom: var(--cd-space-3);
      padding: var(--cd-space-3);
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-danger-soft);
      color: var(--cd-color-danger);
      font-size: var(--cd-text-sm);
      font-weight: 600;
    }
    .calificacion {
      grid-column: 1 / -1;
      display: grid;
      gap: var(--cd-space-2);
      justify-items: start;
    }
    .estrellas {
      display: flex;
      gap: 4px;
      font-size: 24px;
      color: #d97706;
    }
    .estrellas button {
      border: 0;
      background: transparent;
      color: var(--cd-color-border);
      font-size: 28px;
      line-height: 1;
      cursor: pointer;
      padding: 0 2px;
    }
    .estrellas button.activa {
      color: #d97706;
    }
    @media (min-width: 1024px) {
      .columnas {
        grid-template-columns: 1.4fr 1fr;
        align-items: start;
      }
    }
  `,
})
export class ReservaDetalleComponent implements OnInit {
  private readonly reservasApi = inject(ReservasApi);
  private readonly pagosApi = inject(PagosApi);
  private readonly venues = inject(VenuesApi);
  private readonly engagement = inject(EngagementApi);
  private readonly fb = inject(FormBuilder);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly id = input.required<string>();

  protected readonly reserva = signal<Reserva | null>(null);
  protected readonly cancha = signal<CanchaDetalle | null>(null);
  protected readonly pago = signal<Pago | null>(null);
  protected readonly cargando = signal(true);
  protected readonly pagando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly errorPago = signal<string | null>(null);
  protected readonly calificaciones = signal<Calificacion[]>([]);
  protected readonly puntaje = signal(5);
  protected readonly calificando = signal(false);
  protected readonly errorCalificacion = signal<string | null>(null);
  protected comentario = '';

  protected readonly pagoForm = this.fb.nonNullable.group({
    medioPago: ['TARJETA'],
    codigoOperacion: [''],
  });

  ngOnInit(): void {
    void this.cargar();
  }

  protected puedePagar(): boolean {
    const estado = this.reserva()?.estado;
    return estado === 'PENDIENTE' || estado === 'CONFIRMADA';
  }

  protected puedeCancelar(): boolean {
    const reserva = this.reserva();
    if (!reserva) {
      return false;
    }
    const faltante = new Date(reserva.inicio).getTime() - Date.now();
    return (reserva.estado === 'PENDIENTE' || reserva.estado === 'CONFIRMADA') && faltante > 2 * 3_600_000;
  }

  protected claseEstado(): string {
    const estado = this.reserva()?.estado;
    return estado === 'CONFIRMADA'
      ? 'cd-badge cd-badge--success'
      : estado === 'PENDIENTE'
        ? 'cd-badge cd-badge--warning'
        : 'cd-badge';
  }

  protected clasePago(estado: Pago['estado']): string {
    return estado === 'APROBADO'
      ? 'cd-badge cd-badge--success'
      : estado === 'RECHAZADO'
        ? 'cd-badge cd-badge--danger'
        : 'cd-badge--info';
  }

  protected calificacionPropia(): Calificacion | undefined {
    const id = Number(this.id());
    return this.calificaciones().find((calificacion) => calificacion.reservaId === id);
  }

  protected estrellas(puntaje: number): string {
    return '★'.repeat(puntaje) + '☆'.repeat(5 - puntaje);
  }

  protected calificar(): void {
    const reserva = this.reserva();
    if (!reserva) {
      return;
    }
    this.calificando.set(true);
    this.errorCalificacion.set(null);
    this.engagement.calificarReserva(reserva.id, this.puntaje(), this.comentario.trim() || undefined).subscribe({
      next: () => {
        this.calificando.set(false);
        this.comentario = '';
        this.toast.success('Gracias por calificar.');
        void this.cargarCalificaciones(reserva.canchaId);
      },
      error: (error) => {
        this.errorCalificacion.set(toApiError(error).message);
        this.calificando.set(false);
      },
    });
  }

  protected async cancelar(): Promise<void> {    const reserva = this.reserva();
    if (!reserva) {
      return;
    }
    const ok = await this.confirm.confirm({
      title: 'Cancelar reserva',
      message: 'Si corresponde, el reembolso se procesa automaticamente segun la regla de cancelacion.',
      confirmText: 'Si, cancelar',
      danger: true,
    });
    if (!ok) {
      return;
    }
    this.reservasApi.cancelar(reserva.id).subscribe({
      next: (actualizada) => {
        this.reserva.set(actualizada);
        this.toast.success('Reserva cancelada.');
        void this.cargarPago();
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  protected pagar(): void {
    const reserva = this.reserva();
    if (!reserva) {
      return;
    }
    this.pagando.set(true);
    this.errorPago.set(null);
    const { medioPago, codigoOperacion } = this.pagoForm.getRawValue();
    this.pagosApi
      .pagar(reserva.id, { medioPago, codigoOperacion: codigoOperacion.trim() || null })
      .subscribe({
        next: (pago) => {
          this.pago.set(pago);
          this.pagando.set(false);
          this.toast.success('Pago registrado (demo).');
        },
        error: (error) => {
          this.errorPago.set(toApiError(error).message);
          this.pagando.set(false);
        },
      });
  }

  private async cargar(): Promise<void> {
    const id = Number(this.id());
    if (!Number.isFinite(id)) {
      this.error.set('Reserva invalida.');
      this.cargando.set(false);
      return;
    }
    try {
      const reservas = await firstValueFrom(this.reservasApi.mias());
      const reserva = reservas.find((r) => r.id === id);
      if (!reserva) {
        this.error.set('No encontramos esa reserva en tu cuenta.');
        this.cargando.set(false);
        return;
      }
      this.reserva.set(reserva);
      this.cargando.set(false);
      void this.cargarCancha(reserva.canchaId);
      void this.cargarPago();
      void this.cargarCalificaciones(reserva.canchaId);
    } catch (error) {
      this.error.set(toApiError(error).message);
      this.cargando.set(false);
    }
  }

  private async cargarCancha(id: number): Promise<void> {
    try {
      this.cancha.set(await firstValueFrom(this.venues.detalle(id)));
    } catch {
      // el nombre de cancha es opcional
    }
  }

  private async cargarCalificaciones(idCancha: number): Promise<void> {
    try {
      const respuesta = await firstValueFrom(this.engagement.calificacionesCancha(idCancha));
      this.calificaciones.set(respuesta.calificaciones);
    } catch {
      // la calificacion es opcional en el detalle
    }
  }

  private async cargarPago(): Promise<void> {
    const reserva = this.reserva();
    if (!reserva) {
      return;
    }
    try {
      this.pago.set(await firstValueFrom(this.pagosApi.obtener(reserva.id)));
    } catch (error) {
      if (toApiError(error).status !== 404) {
        this.errorPago.set(toApiError(error).message);
      }
    }
  }
}
