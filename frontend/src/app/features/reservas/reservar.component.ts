import { CurrencyPipe, DatePipe, LowerCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { PagosApi } from '../../core/api/pagos.api';
import { ReservasApi } from '../../core/api/reservas.api';
import { Pago, Reserva } from '../../core/api/reservas.models';
import { VenuesApi } from '../../core/api/venues.api';
import { CanchaDetalle, CocheraDisponible } from '../../core/api/venues.models';
import { APP_CONFIG } from '../../core/config/app-config';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';

@Component({
  selector: 'app-reservar',
  imports: [
    RouterLink,
    ReactiveFormsModule,
    CurrencyPipe,
    DatePipe,
    LowerCasePipe,
    CdButtonDirective,
    CdInputDirective,
    CdSkeletonComponent,
    CdErrorStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cd-container cd-page">
      <header class="cd-page__header">
        <h1 class="cd-page__title">Confirmar reserva</h1>
        <p class="cd-muted">Revisa la franja, agrega cochera si la necesitas y confirma.</p>
      </header>

      @if (cargando()) {
        <cd-skeleton height="300px" radius="12px" />
      } @else if (errorCarga()) {
        <cd-error-state [message]="errorCarga()!" [retryable]="false" />
      } @else if (cancha(); as c) {
        <div class="columnas">
          <section class="cd-card">
            <h2 class="cd-page__title seccion">{{ c.nombre }}</h2>
            <p class="cd-muted">{{ c.establecimiento }} · {{ c.distrito.nombre }}</p>
            <hr class="cd-divider" />
            <dl class="datos">
              <div>
                <dt>Fecha</dt>
                <dd>{{ inicio() | date: 'EEEE d MMMM yyyy' }}</dd>
              </div>
              <div>
                <dt>Horario</dt>
                <dd>{{ inicio() | date: 'HH:mm' }} - {{ fin() | date: 'HH:mm' }}</dd>
              </div>
              <div>
                <dt>Precio cancha</dt>
                <dd>{{ precioBase() | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</dd>
              </div>
              @if (cocheraSeleccionada(); as cochera) {
                <div>
                  <dt>Cochera</dt>
                  <dd>
                    {{ cochera.nombre }} ·
                    {{ cochera.tarifaHora * horas() | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}
                  </dd>
                </div>
              }
              <div class="total">
                <dt>Total estimado</dt>
                <dd>{{ totalEstimado() | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</dd>
              </div>
            </dl>
            <p class="cd-xs cd-muted">
              Descuentos por promoción se calculan al confirmar. Pago simulado: no se cobra dinero real.
            </p>
          </section>

          <aside class="cd-card">
            @switch (paso()) {
              @case ('datos') {
                <h2 class="seccion">Datos de la reserva</h2>
                @if (error()) {
                  <p class="alerta" role="alert">{{ error() }}</p>
                }
                <form class="cd-stack" [formGroup]="form" (ngSubmit)="crear()" novalidate>
                  <label class="cd-field">
                    <span class="cd-field__label">Cochera <span class="optional">(opcional)</span></span>
                    <select cdInput formControlName="cocheraId">
                      <option [ngValue]="null">Sin cochera</option>
                      @for (cochera of cocheras(); track cochera.id) {
                        <option [ngValue]="cochera.id">
                          {{ cochera.nombre }} ·
                          {{ cochera.tarifaHora | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}/h
                        </option>
                      }
                    </select>
                  </label>
                  <label class="cd-field">
                    <span class="cd-field__label">Código de promoción <span class="optional">(opcional)</span></span>
                    <input cdInput formControlName="codigoPromocion" autocomplete="off" />
                  </label>
                  <button cdButton variant="accent" type="submit" [loading]="enviando()" [block]="true">
                    Confirmar reserva
                  </button>
                </form>
              }
              @case ('pago') {
                <h2 class="seccion">Pago simulado</h2>
                @if (reserva(); as r) {
                  <p class="cd-small cd-muted">
                    Reserva #{{ r.id }} creada. Monto cancha:
                    {{ r.montoCancha | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}
                    @if (r.montoDescuento) {
                      · descuento {{ r.montoDescuento | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}
                    }
                    @if (r.montoCochera) {
                      · cochera {{ r.montoCochera | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}
                    }
                  </p>
                }
                @if (error()) {
                  <p class="alerta" role="alert">{{ error() }}</p>
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
                    <input cdInput formControlName="codigoOperacion" autocomplete="off" />
                  </label>
                  <button cdButton variant="accent" type="submit" [loading]="pagando()" [block]="true">
                    Pagar ahora
                  </button>
                  <a routerLink="/mis-reservas" class="cd-btn cd-btn--ghost cd-btn--block">Pagar despues</a>
                </form>
              }
              @case ('listo') {
                <div class="exito" role="status">
                  <h2 class="seccion">Reserva registrada</h2>
                  @if (pago(); as p) {
                    <p>
                      Pago {{ p.estado | lowercase }} por
                      <strong>{{ p.monto | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</strong>.
                    </p>
                  }
                  <p class="cd-small cd-muted">
                    El propietario debe confirmar la reserva. Puedes seguir el estado en Mis reservas.
                  </p>
                  <a routerLink="/mis-reservas" class="cd-btn cd-btn--primary cd-btn--block">Ir a mis reservas</a>
                  <a routerLink="/canchas" class="cd-btn cd-btn--secondary cd-btn--block">Seguir explorando</a>
                </div>
              }
            }
          </aside>
        </div>
      }
    </div>
  `,
  styles: `
    .columnas {
      display: grid;
      gap: var(--cd-space-4);
    }
    .seccion {
      font-size: var(--cd-text-lg);
      margin-bottom: var(--cd-space-3);
    }
    .datos {
      display: grid;
      gap: var(--cd-space-2);
      margin: 0;
      margin-top: var(--cd-space-3);
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
    .alerta {
      margin-bottom: var(--cd-space-3);
      padding: var(--cd-space-3);
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-danger-soft);
      color: var(--cd-color-danger);
      font-size: var(--cd-text-sm);
      font-weight: 600;
    }
    .exito {
      display: grid;
      gap: var(--cd-space-2);
    }
    .exito .cd-btn {
      text-decoration: none;
    }
    .cd-btn--block + .cd-btn--block {
      margin-top: var(--cd-space-2);
    }
    @media (min-width: 1024px) {
      .columnas {
        grid-template-columns: 1.2fr 1fr;
        align-items: start;
      }
    }
  `,
})
export class ReservarComponent implements OnInit {
  private readonly venues = inject(VenuesApi);
  private readonly reservasApi = inject(ReservasApi);
  private readonly pagosApi = inject(PagosApi);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);

  readonly canchaId = input.required<string>();

  protected readonly cancha = signal<CanchaDetalle | null>(null);
  protected readonly cocheras = signal<CocheraDisponible[]>([]);
  protected readonly cocheraSeleccionada = signal<CocheraDisponible | null>(null);
  protected readonly reserva = signal<Reserva | null>(null);
  protected readonly pago = signal<Pago | null>(null);
  protected readonly paso = signal<'datos' | 'pago' | 'listo'>('datos');
  protected readonly cargando = signal(true);
  protected readonly enviando = signal(false);
  protected readonly pagando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly errorCarga = signal<string | null>(null);

  protected readonly inicio = signal(this.query('inicio'));
  protected readonly fin = signal(this.query('fin'));
  protected readonly precioBase = signal(Number(this.query('precio')) || 0);
  protected readonly idempotencyKey = crypto.randomUUID();

  protected readonly form = this.fb.group({
    cocheraId: [null as number | null],
    codigoPromocion: [''],
  });

  protected readonly pagoForm = this.fb.nonNullable.group({
    medioPago: ['TARJETA', [Validators.required]],
    codigoOperacion: [''],
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected horas(): number {
    const inicio = new Date(this.inicio()).getTime();
    const fin = new Date(this.fin()).getTime();
    return Math.max(1, Math.round((fin - inicio) / 3_600_000));
  }

  protected totalEstimado(): number {
    const cochera = this.cocheraSeleccionada();
    return this.precioBase() + (cochera ? cochera.tarifaHora * this.horas() : 0);
  }

  protected crear(): void {
    const id = Number(this.canchaId());
    if (!this.inicio() || !this.fin()) {
      this.error.set('La franja seleccionada no es valida. Vuelve al detalle de la cancha.');
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    const { cocheraId, codigoPromocion } = this.form.getRawValue();
    this.reservasApi
      .crear(
        {
          canchaId: id,
          inicio: this.inicio(),
          fin: this.fin(),
          cocheraId: cocheraId ?? null,
          codigoPromocion: codigoPromocion?.trim() || null,
        },
        this.idempotencyKey,
      )
      .subscribe({
        next: (reserva) => {
          this.reserva.set(reserva);
          this.paso.set('pago');
          this.enviando.set(false);
        },
        error: (error) => {
          this.error.set(toApiError(error).message);
          this.enviando.set(false);
        },
      });
  }

  protected pagar(): void {
    const reserva = this.reserva();
    if (!reserva) {
      return;
    }
    this.pagando.set(true);
    this.error.set(null);
    const { medioPago, codigoOperacion } = this.pagoForm.getRawValue();
    this.pagosApi
      .pagar(reserva.id, { medioPago, codigoOperacion: codigoOperacion.trim() || null })
      .subscribe({
        next: (pago) => {
          this.pago.set(pago);
          this.paso.set('listo');
          this.pagando.set(false);
        },
        error: (error) => {
          this.error.set(toApiError(error).message);
          this.pagando.set(false);
        },
      });
  }

  private cargar(): void {
    const id = Number(this.canchaId());
    if (!Number.isFinite(id) || !this.inicio() || !this.fin()) {
      this.errorCarga.set('Faltan datos para preparar la reserva. Vuelve a elegir una franja.');
      this.cargando.set(false);
      return;
    }
    this.cargando.set(true);
    void Promise.all([
      firstValueFrom(this.venues.detalle(id)),
      firstValueFrom(this.venues.cocheras(id, this.inicio(), this.fin())).catch(() => [] as CocheraDisponible[]),
    ])
      .then(([detalle, cocheras]) => {
        this.cancha.set(detalle);
        this.cocheras.set(cocheras);
        if (!this.precioBase()) {
          this.precioBase.set(detalle.precioBase * this.horas());
        }
        this.form.controls.cocheraId.valueChanges.subscribe((cocheraId) => {
          this.cocheraSeleccionada.set(cocheras.find((c) => c.id === cocheraId) ?? null);
        });
        this.cargando.set(false);
      })
      .catch((error) => {
        this.errorCarga.set(toApiError(error).message);
        this.cargando.set(false);
      });
  }

  private query(nombre: string): string {
    return this.route.snapshot.queryParamMap.get(nombre) ?? '';
  }
}
