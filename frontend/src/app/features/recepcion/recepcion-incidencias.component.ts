import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { IncidenciaRecepcion, RecepcionApi } from '../../core/api/recepcion.api';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { ToastService } from '../../shared/ui/toast/toast.service';

/** Incidencias operativas reportadas desde recepcion. */
@Component({
  selector: 'app-recepcion-incidencias',
  imports: [
    DatePipe,
    CdButtonDirective,
    CdInputDirective,
    CdSkeletonComponent,
    CdErrorStateComponent,
    CdEmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="cd-card formulario" (submit)="registrar($event)" novalidate>
      <h2 class="seccion">Reportar incidencia</h2>
      <div class="grid">
        <label class="cd-field">
          <span class="cd-field__label">Asunto</span>
          <input cdInput maxlength="150" [value]="asunto()" (input)="cambiarAsunto($event)"
            placeholder="Cancha mojada, luces fundidas…" required />
        </label>
        <label class="cd-field">
          <span class="cd-field__label">Reserva relacionada (opcional)</span>
          <input cdInput type="number" min="1" [value]="idReserva() ?? ''" (input)="cambiarReserva($event)"
            placeholder="N.º de reserva" />
        </label>
      </div>
      <label class="cd-field">
        <span class="cd-field__label">Descripción</span>
        <textarea cdInput rows="3" maxlength="800" [value]="descripcion()"
          (input)="cambiarDescripcion($event)" placeholder="Detalla lo ocurrido" required></textarea>
      </label>
      <footer class="acciones">
        <span class="cd-small cd-muted">La administración recibe la incidencia como reclamo interno.</span>
        <button cdButton type="submit" [loading]="guardando()" [disabled]="!puedeEnviar()">Registrar incidencia</button>
      </footer>
    </form>

    <h2 class="seccion lista">Historial</h2>
    @if (cargando()) {
      <div class="cd-stack">
        @for (i of [1, 2]; track i) {
          <cd-skeleton height="92px" radius="12px" />
        }
      </div>
    } @else if (error()) {
      <cd-error-state [message]="error()!" (retry)="cargar()" />
    } @else if (incidencias().length === 0) {
      <cd-empty-state title="Sin incidencias" message="Aún no has reportado incidencias." />
    } @else {
      <div class="cd-stack">
        @for (incidencia of incidencias(); track incidencia.id) {
          <article class="cd-card">
            <div class="cd-row cd-row--between">
              <strong>{{ incidencia.asunto }}</strong>
              <span class="cd-badge">{{ incidencia.estado }}</span>
            </div>
            <p class="cd-small cd-muted">
              {{ incidencia.fechaRegistro | date: 'd MMM yyyy, HH:mm' }}
              @if (incidencia.idReserva) {
                · reserva #{{ incidencia.idReserva }}
              }
            </p>
            <p class="cd-small">{{ incidencia.descripcion }}</p>
          </article>
        }
      </div>
    }
  `,
  styles: `
    .formulario {
      display: grid;
      gap: var(--cd-space-3);
      max-width: 820px;
      margin-bottom: var(--cd-space-5);
    }
    .grid {
      display: grid;
      gap: var(--cd-space-3);
    }
    .seccion {
      margin: 0;
      font-size: var(--cd-text-base);
    }
    .lista {
      margin-bottom: var(--cd-space-3);
    }
    .acciones {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--cd-space-3);
      flex-wrap: wrap;
    }
    @media (min-width: 720px) {
      .grid {
        grid-template-columns: 1fr 1fr;
      }
    }
  `,
})
export class RecepcionIncidenciasComponent implements OnInit {
  private readonly api = inject(RecepcionApi);
  private readonly toast = inject(ToastService);

  protected readonly asunto = signal('');
  protected readonly descripcion = signal('');
  protected readonly idReserva = signal<number | null>(null);
  protected readonly incidencias = signal<IncidenciaRecepcion[]>([]);
  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    await this.cargar();
  }

  protected puedeEnviar(): boolean {
    return this.asunto().trim().length > 0 && this.descripcion().trim().length > 0 && !this.guardando();
  }

  protected cambiarAsunto(evento: Event): void {
    this.asunto.set((evento.target as HTMLInputElement).value);
  }

  protected cambiarDescripcion(evento: Event): void {
    this.descripcion.set((evento.target as HTMLTextAreaElement).value);
  }

  protected cambiarReserva(evento: Event): void {
    const valor = (evento.target as HTMLInputElement).value;
    this.idReserva.set(valor ? Number(valor) : null);
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.incidencias.set(await firstValueFrom(this.api.incidencias()));
    } catch (error) {
      this.error.set(toApiError(error).message);
    } finally {
      this.cargando.set(false);
    }
  }

  protected async registrar(evento: Event): Promise<void> {
    evento.preventDefault();
    if (!this.puedeEnviar()) {
      return;
    }
    this.guardando.set(true);
    try {
      await firstValueFrom(
        this.api.registrarIncidencia({
          asunto: this.asunto().trim(),
          descripcion: this.descripcion().trim(),
          idReserva: this.idReserva(),
        }),
      );
      this.toast.success('Incidencia registrada.');
      this.asunto.set('');
      this.descripcion.set('');
      this.idReserva.set(null);
      await this.cargar();
    } catch (error) {
      this.toast.error(toApiError(error).message);
    } finally {
      this.guardando.set(false);
    }
  }
}
