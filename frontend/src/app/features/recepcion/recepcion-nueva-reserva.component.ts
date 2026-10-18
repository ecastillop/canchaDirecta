import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  ClienteRecepcion,
  RecepcionApi,
} from '../../core/api/recepcion.api';
import { RecepcionStore, hoyIso } from '../../core/api/recepcion.store';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { ToastService } from '../../shared/ui/toast/toast.service';

/** Reserva presencial (walk-in) para un cliente registrado. */
@Component({
  selector: 'app-recepcion-nueva-reserva',
  imports: [CdButtonDirective, CdInputDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="cd-small cd-muted intro">
      Registra una reserva presencial para un cliente ya registrado. Si el pago se recibe en el
      momento, queda aprobado de inmediato.
    </p>

    <form class="cd-card formulario" (submit)="registrar($event)" novalidate>
      <div class="grid">
        <label class="cd-field">
          <span class="cd-field__label">Sede</span>
          <select cdInput [value]="store.seleccionado()" (change)="cambiarSede($event)" required>
            @for (sede of store.establecimientos(); track sede.id) {
              <option [value]="sede.id">{{ sede.nombre }}</option>
            }
          </select>
        </label>

        <label class="cd-field">
          <span class="cd-field__label">Cancha</span>
          <select cdInput [value]="idCancha() ?? ''" (change)="cambiarCancha($event)" required>
            <option value="">Selecciona una cancha</option>
            @for (cancha of store.canchas(); track cancha.id) {
              <option [value]="cancha.id">{{ cancha.nombre }}</option>
            }
          </select>
        </label>

        <label class="cd-field">
          <span class="cd-field__label">Fecha</span>
          <input cdInput type="date" [value]="fecha()" (change)="cambiarFecha($event)" required />
        </label>

        <label class="cd-field">
          <span class="cd-field__label">Hora de inicio</span>
          <input cdInput type="time" [value]="hora()" (change)="cambiarHora($event)" required />
        </label>

        <label class="cd-field">
          <span class="cd-field__label">Duración (min)</span>
          <select cdInput [value]="duracion()" (change)="cambiarDuracion($event)">
            <option value="60">60 minutos</option>
            <option value="90">90 minutos</option>
            <option value="120">120 minutos</option>
          </select>
        </label>

        <label class="cd-field campo--check">
          <input type="checkbox" [checked]="pagoRecibido()" (change)="cambiarPago($event)" />
          <span>Pago recibido en caja (efectivo)</span>
        </label>
      </div>

      <section class="cliente">
        <h2 class="seccion">Cliente</h2>
        @if (cliente(); as elegido) {
          <div class="elegido">
            <div>
              <strong>{{ elegido.nombre }}</strong>
              <p class="cd-small cd-muted">{{ elegido.correo }} · {{ elegido.telefono ?? 'sin teléfono' }}</p>
            </div>
            <button cdButton variant="secondary" size="sm" type="button" (click)="limpiarCliente()">
              Cambiar
            </button>
          </div>
        } @else {
          <label class="cd-field">
            <span class="cd-field__label">Buscar por nombre, correo o teléfono</span>
            <input cdInput type="search" [value]="texto()" (input)="buscar($event)"
              placeholder="Escribe al menos 2 caracteres" autocomplete="off" />
          </label>
          @if (resultados().length > 0) {
            <ul class="resultados">
              @for (candidato of resultados(); track candidato.id) {
                <li>
                  <button type="button" (click)="elegirCliente(candidato)">
                    <strong>{{ candidato.nombre }}</strong>
                    <span class="cd-small cd-muted">{{ candidato.correo }} · {{ candidato.telefono ?? 'sin teléfono' }}</span>
                  </button>
                </li>
              }
            </ul>
          }
        }
      </section>

      <footer class="acciones">
        <span class="cd-small cd-muted">La franja se valida contra la agenda de la cancha.</span>
        <button cdButton type="submit" [loading]="guardando()" [disabled]="!puedeEnviar()">
          Registrar reserva
        </button>
      </footer>
    </form>
  `,
  styles: `
    .intro {
      max-width: 70ch;
      margin-bottom: var(--cd-space-4);
    }
    .formulario {
      display: grid;
      gap: var(--cd-space-4);
      max-width: 920px;
    }
    .grid {
      display: grid;
      gap: var(--cd-space-3);
    }
    .campo--check {
      display: flex;
      align-items: center;
      gap: var(--cd-space-2);
    }
    .seccion {
      margin: 0 0 var(--cd-space-3);
      font-size: var(--cd-text-base);
    }
    .elegido {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--cd-space-3);
      padding: var(--cd-space-3);
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-md);
    }
    .resultados {
      list-style: none;
      margin: var(--cd-space-2) 0 0;
      padding: 0;
      display: grid;
      gap: 6px;
    }
    .resultados button {
      display: grid;
      gap: 2px;
      width: 100%;
      padding: var(--cd-space-3);
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-md);
      background: var(--cd-color-surface);
      text-align: left;
      cursor: pointer;
    }
    .acciones {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--cd-space-3);
      flex-wrap: wrap;
      padding-top: var(--cd-space-3);
      border-top: 1px solid var(--cd-color-border);
    }
    @media (min-width: 720px) {
      .grid {
        grid-template-columns: 1fr 1fr;
      }
    }
  `,
})
export class RecepcionNuevaReservaComponent implements OnInit {
  private readonly api = inject(RecepcionApi);
  private readonly toast = inject(ToastService);
  protected readonly store = inject(RecepcionStore);

  protected readonly idCancha = signal<number | null>(null);
  protected readonly fecha = signal(hoyIso());
  protected readonly hora = signal('10:00');
  protected readonly duracion = signal('60');
  protected readonly pagoRecibido = signal(true);
  protected readonly texto = signal('');
  protected readonly resultados = signal<ClienteRecepcion[]>([]);
  protected readonly cliente = signal<ClienteRecepcion | null>(null);
  protected readonly guardando = signal(false);

  async ngOnInit(): Promise<void> {
    await this.store.init();
  }

  protected puedeEnviar(): boolean {
    return this.idCancha() !== null && this.cliente() !== null && !this.guardando();
  }

  protected async cambiarSede(evento: Event): Promise<void> {
    const valor = (evento.target as HTMLSelectElement).value;
    await this.store.seleccionar(valor ? Number(valor) : null);
    this.idCancha.set(null);
  }

  protected cambiarCancha(evento: Event): void {
    const valor = (evento.target as HTMLSelectElement).value;
    this.idCancha.set(valor ? Number(valor) : null);
  }

  protected cambiarFecha(evento: Event): void {
    this.fecha.set((evento.target as HTMLInputElement).value);
  }

  protected cambiarHora(evento: Event): void {
    this.hora.set((evento.target as HTMLInputElement).value);
  }

  protected cambiarDuracion(evento: Event): void {
    this.duracion.set((evento.target as HTMLSelectElement).value);
  }

  protected cambiarPago(evento: Event): void {
    this.pagoRecibido.set((evento.target as HTMLInputElement).checked);
  }

  protected buscar(evento: Event): void {
    const valor = (evento.target as HTMLInputElement).value;
    this.texto.set(valor);
    this.cliente.set(null);
    if (valor.trim().length < 2) {
      this.resultados.set([]);
      return;
    }
    this.api.clientes(valor.trim()).subscribe({
      next: (clientes) => this.resultados.set(clientes),
      error: () => this.resultados.set([]),
    });
  }

  protected elegirCliente(cliente: ClienteRecepcion): void {
    this.cliente.set(cliente);
    this.resultados.set([]);
  }

  protected limpiarCliente(): void {
    this.cliente.set(null);
    this.texto.set('');
  }

  protected async registrar(evento: Event): Promise<void> {
    evento.preventDefault();
    const cliente = this.cliente();
    const idCancha = this.idCancha();
    if (!cliente || idCancha === null) {
      return;
    }
    this.guardando.set(true);
    try {
      const creada = await firstValueFrom(
        this.api.reservaPresencial({
          idEstablecimiento: this.store.seleccionado(),
          idCancha,
          idCliente: cliente.id,
          fecha: this.fecha(),
          horaInicio: this.hora(),
          duracionMinutos: Number(this.duracion()),
          pagoRecibido: this.pagoRecibido(),
        }),
      );
      this.toast.success(`Reserva presencial #${creada.id} registrada.`);
      this.limpiarCliente();
      this.idCancha.set(null);
    } catch (error) {
      this.toast.error(toApiError(error).message);
    } finally {
      this.guardando.set(false);
    }
  }
}
