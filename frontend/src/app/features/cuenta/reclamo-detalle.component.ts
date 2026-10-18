import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EngagementApi, ReclamoDetalle } from '../../core/api/engagement.api';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { ToastService } from '../../shared/ui/toast/toast.service';

@Component({
  selector: 'app-reclamo-detalle',
  imports: [
    RouterLink,
    FormsModule,
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
        <a routerLink="/reclamos">Reclamos</a> / <span>#{{ id() }}</span>
      </nav>

      @if (cargando()) {
        <cd-skeleton height="260px" radius="12px" />
      } @else if (error()) {
        <cd-error-state [message]="error()!" [retryable]="false" />
      } @else if (reclamo(); as r) {
        <div class="cd-card">
          <div class="cabecera">
            <div>
              <h1 class="cd-page__title">{{ r.asunto }}</h1>
              <p class="cd-xs cd-muted">
                #{{ r.id }} · {{ r.fechaRegistro | date: 'd MMM yyyy, HH:mm' }}
                @if (r.idReserva) {
                  · reserva #{{ r.idReserva }}
                }
              </p>
            </div>
            <span class="cd-badge" [class]="claseEstado()">{{ r.estado }}</span>
          </div>
          <p class="descripcion">{{ r.descripcion }}</p>
        </div>

        <section class="cd-card seguimientos" aria-labelledby="seguimientos-titulo">
          <h2 id="seguimientos-titulo" class="seccion">Seguimiento</h2>
          @if (r.seguimientos.length === 0) {
            <p class="cd-small cd-muted">Aún no hay respuestas ni comentarios.</p>
          } @else {
            <ol class="linea">
              @for (seguimiento of r.seguimientos; track seguimiento.id) {
                <li>
                  <p>{{ seguimiento.comentario }}</p>
                  <time class="cd-xs cd-muted" [attr.datetime]="seguimiento.fecha">
                    {{ seguimiento.fecha | date: 'd MMM yyyy, HH:mm' }}
                  </time>
                </li>
              }
            </ol>
          }

          @if (r.estado !== 'CERRADO') {
            <form class="cd-stack formulario" (ngSubmit)="comentar()" novalidate>
              <label class="cd-field">
                <span class="cd-field__label">Agregar comentario</span>
                <textarea cdInput name="comentario" [(ngModel)]="comentario" rows="3" maxlength="800"></textarea>
              </label>
              @if (errorComentario()) {
                <p class="alerta" role="alert">{{ errorComentario() }}</p>
              }
              <button cdButton type="submit" [loading]="enviando()">Enviar comentario</button>
            </form>
          }
        </section>
      }
    </div>
  `,
  styles: `
    .migas {
      margin-bottom: var(--cd-space-3);
    }
    .cabecera {
      display: flex;
      justify-content: space-between;
      align-items: start;
      gap: var(--cd-space-3);
      margin-bottom: var(--cd-space-3);
      flex-wrap: wrap;
    }
    .descripcion {
      white-space: pre-line;
    }
    .seguimientos {
      margin-top: var(--cd-space-4);
    }
    .seccion {
      font-size: var(--cd-text-base);
      margin-bottom: var(--cd-space-3);
    }
    .linea {
      list-style: none;
      padding: 0;
      display: grid;
      gap: var(--cd-space-3);
      border-left: 2px solid var(--cd-color-border);
      padding-left: var(--cd-space-4);
      margin-bottom: var(--cd-space-4);
    }
    .linea p {
      font-size: var(--cd-text-sm);
    }
    .formulario {
      max-width: 640px;
    }
    .alerta {
      padding: var(--cd-space-3);
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-danger-soft);
      color: var(--cd-color-danger);
      font-size: var(--cd-text-sm);
      font-weight: 600;
    }
  `,
})
export class ReclamoDetalleComponent implements OnInit {
  private readonly api = inject(EngagementApi);
  private readonly toast = inject(ToastService);

  readonly id = input.required<string>();

  protected readonly reclamo = signal<ReclamoDetalle | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);
  protected readonly errorComentario = signal<string | null>(null);
  protected comentario = '';

  ngOnInit(): void {
    this.cargar();
  }

  protected claseEstado(): string {
    const estado = this.reclamo()?.estado;
    return estado === 'RESUELTO'
      ? 'cd-badge cd-badge--success'
      : estado === 'EN_REVISION'
        ? 'cd-badge cd-badge--info'
        : estado === 'ABIERTO'
          ? 'cd-badge cd-badge--warning'
          : 'cd-badge';
  }

  protected comentar(): void {
    const comentario = this.comentario.trim();
    if (!comentario) {
      this.errorComentario.set('Escribe un comentario.');
      return;
    }
    this.enviando.set(true);
    this.errorComentario.set(null);
    this.api.comentar(Number(this.id()), comentario).subscribe({
      next: () => {
        this.comentario = '';
        this.enviando.set(false);
        this.toast.success('Comentario enviado.');
        this.cargar();
      },
      error: (error) => {
        this.errorComentario.set(toApiError(error).message);
        this.enviando.set(false);
      },
    });
  }

  private cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.reclamo(Number(this.id())).subscribe({
      next: (reclamo) => {
        this.reclamo.set(reclamo);
        this.cargando.set(false);
      },
      error: (error) => {
        this.error.set(toApiError(error).message);
        this.cargando.set(false);
      },
    });
  }
}
