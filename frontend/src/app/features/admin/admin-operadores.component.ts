import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  AdminApi,
  AdminEstablecimiento,
  AdminOperador,
  AdminUsuario,
} from '../../core/api/admin.api';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { ConfirmService } from '../../shared/ui/confirm/confirm.service';
import { ToastService } from '../../shared/ui/toast/toast.service';

/** Personal y permisos de recepcion por sede (ADR-0038). */
@Component({
  selector: 'app-admin-operadores',
  imports: [
    CdButtonDirective,
    CdInputDirective,
    CdSkeletonComponent,
    CdErrorStateComponent,
    CdEmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="cabecera">
      <p class="eyebrow">Administración</p>
      <h2 class="intro-title">Operadores de recepción</h2>
      <p class="cd-small cd-muted">
        Vincula usuarios a una sede para que operen su turno: agenda, reserva presencial, cobros
        e incidencias. Solo ven las sedes asignadas.
      </p>
    </header>

    @if (cargando()) {
      <cd-skeleton height="300px" radius="16px" />
    } @else if (error()) {
      <cd-error-state [message]="error()!" (retry)="cargarEstablecimientos()" />
    } @else {
      <label class="cd-field sede">
        <span class="cd-field__label">Sede</span>
        <select cdInput [value]="idEstablecimiento() ?? ''" (change)="cambiarSede($event)">
          <option value="">Selecciona una sede</option>
          @for (sede of establecimientos(); track sede.id) {
            <option [value]="sede.id">{{ sede.nombre }} · {{ sede.distrito }}</option>
          }
        </select>
      </label>

      @if (idEstablecimiento()) {
        <section class="cd-card alta">
          <h3 class="seccion">Vincular operador</h3>
          <div class="grid">
            <label class="cd-field">
              <span class="cd-field__label">Buscar usuario por correo</span>
              <input cdInput type="search" [value]="correo()" (input)="buscar($event)"
                placeholder="correo@canchadirecta.pe" autocomplete="off" />
            </label>
            <label class="cd-field">
              <span class="cd-field__label">Cargo (opcional)</span>
              <input cdInput maxlength="60" [value]="cargo()" (input)="cambiarCargo($event)"
                placeholder="Recepción, cajero…" />
            </label>
          </div>
          @if (candidatos().length > 0) {
            <ul class="resultados">
              @for (usuario of candidatos(); track usuario.id) {
                <li>
                  <span>
                    <strong>{{ usuario.nombreCompleto }}</strong>
                    <span class="cd-small cd-muted">{{ usuario.correo }} · {{ usuario.roles.join(', ') }}</span>
                  </span>
                  <button cdButton size="sm" type="button" [loading]="guardando()"
                    (click)="asignar(usuario)">Vincular</button>
                </li>
              }
            </ul>
          }
        </section>

        <h3 class="seccion">Operadores de la sede</h3>
        @if (operadores().length === 0) {
          <cd-empty-state title="Sin operadores" message="Esta sede todavía no tiene personal de recepción." />
        } @else {
          <ul class="lista">
            @for (operador of operadores(); track operador.idUsuario) {
              <li class="cd-card">
                <span>
                  <strong>{{ operador.nombre }}</strong>
                  <p class="cd-small cd-muted">
                    {{ operador.correo }}
                    @if (operador.cargo) {
                      · {{ operador.cargo }}
                    }
                  </p>
                </span>
                <button cdButton variant="danger" size="sm" type="button" (click)="remover(operador)">
                  Quitar acceso
                </button>
              </li>
            }
          </ul>
        }
      }
    }
  `,
  styles: `
    .cabecera {
      display: grid;
      gap: 6px;
      margin-bottom: var(--cd-space-4);
    }
    .eyebrow {
      margin: 0;
      color: var(--cd-color-text-muted);
      font-size: var(--cd-text-xs);
      font-weight: 700;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }
    .intro-title {
      margin: 0;
      font-size: clamp(22px, 2.4vw, 30px);
    }
    .sede {
      max-width: 420px;
      margin-bottom: var(--cd-space-4);
    }
    .alta {
      display: grid;
      gap: var(--cd-space-3);
      max-width: 820px;
      margin-bottom: var(--cd-space-5);
    }
    .seccion {
      margin: 0 0 var(--cd-space-3);
      font-size: var(--cd-text-base);
    }
    .grid {
      display: grid;
      gap: var(--cd-space-3);
    }
    .resultados,
    .lista {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: var(--cd-space-2);
    }
    .resultados li,
    .lista li {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--cd-space-3);
    }
    .resultados li {
      padding: var(--cd-space-3);
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-md);
    }
    @media (min-width: 720px) {
      .grid {
        grid-template-columns: 1fr 1fr;
      }
    }
  `,
})
export class AdminOperadoresComponent implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);

  protected readonly establecimientos = signal<AdminEstablecimiento[]>([]);
  protected readonly operadores = signal<AdminOperador[]>([]);
  protected readonly candidatos = signal<AdminUsuario[]>([]);
  protected readonly idEstablecimiento = signal<number | null>(null);
  protected readonly correo = signal('');
  protected readonly cargo = signal('');
  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    await this.cargarEstablecimientos();
  }

  protected async cargarEstablecimientos(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.establecimientos.set(await firstValueFrom(this.api.establecimientos()));
    } catch (error) {
      this.error.set(toApiError(error).message);
    } finally {
      this.cargando.set(false);
    }
  }

  protected async cambiarSede(evento: Event): Promise<void> {
    const valor = (evento.target as HTMLSelectElement).value;
    this.idEstablecimiento.set(valor ? Number(valor) : null);
    this.candidatos.set([]);
    this.correo.set('');
    await this.cargarOperadores();
  }

  protected cambiarCargo(evento: Event): void {
    this.cargo.set((evento.target as HTMLInputElement).value);
  }

  protected buscar(evento: Event): void {
    const valor = (evento.target as HTMLInputElement).value;
    this.correo.set(valor);
    if (valor.trim().length < 3) {
      this.candidatos.set([]);
      return;
    }
    this.api.usuarios(valor.trim()).subscribe({
      next: (usuarios) => this.candidatos.set(usuarios.slice(0, 8)),
      error: () => this.candidatos.set([]),
    });
  }

  protected asignar(usuario: AdminUsuario): void {
    const id = this.idEstablecimiento();
    if (!id) {
      return;
    }
    this.guardando.set(true);
    this.api.asignarOperador(id, usuario.id, this.cargo().trim() || undefined).subscribe({
      next: (operadores) => {
        this.operadores.set(operadores);
        this.candidatos.set([]);
        this.correo.set('');
        this.cargo.set('');
        this.guardando.set(false);
        this.toast.success(`${usuario.nombreCompleto} ahora opera esta sede.`);
      },
      error: (error) => {
        this.guardando.set(false);
        this.toast.error(toApiError(error).message);
      },
    });
  }

  protected async remover(operador: AdminOperador): Promise<void> {
    const id = this.idEstablecimiento();
    if (!id) {
      return;
    }
    const ok = await this.confirm.confirm({
      title: 'Quitar acceso',
      message: `${operador.nombre} dejará de operar esta sede.`,
      confirmText: 'Quitar',
      danger: true,
    });
    if (!ok) {
      return;
    }
    this.api.removerOperador(id, operador.idUsuario).subscribe({
      next: (operadores) => {
        this.operadores.set(operadores);
        this.toast.success('Acceso retirado.');
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  private async cargarOperadores(): Promise<void> {
    const id = this.idEstablecimiento();
    if (!id) {
      this.operadores.set([]);
      return;
    }
    try {
      this.operadores.set(await firstValueFrom(this.api.operadores(id)));
    } catch (error) {
      this.toast.error(toApiError(error).message);
    }
  }
}
