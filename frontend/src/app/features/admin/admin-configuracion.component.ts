import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { AdminApi, AdminConfiguracion } from '../../core/api/admin.api';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { ToastService } from '../../shared/ui/toast/toast.service';

/** Configuracion persistente de la plataforma (ADR-0037). */
@Component({
  selector: 'app-admin-configuracion',
  imports: [ReactiveFormsModule, DatePipe, CdButtonDirective, CdInputDirective, CdSkeletonComponent, CdErrorStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="cabecera">
      <p class="eyebrow">Administración</p>
      <h2 class="intro-title">Configuración de la plataforma</h2>
      <p class="cd-small cd-muted">
        Estos valores se aplican en tiempo real a las reservas, cancelaciones, reembolsos y
        liquidaciones. Cada cambio queda registrado en la auditoría.
      </p>
    </header>

    @if (cargando()) {
      <cd-skeleton height="320px" radius="16px" />
    } @else if (error()) {
      <cd-error-state [message]="error()!" (retry)="cargar()" />
    } @else {
      <form class="cd-card configuracion" [formGroup]="form" (ngSubmit)="guardar()" novalidate>
        <section>
          <h3 class="seccion">Reglas económicas</h3>
          <div class="grid">
            <label class="cd-field">
              <span class="cd-field__label">Comisión de plataforma (%)</span>
              <input cdInput type="number" min="0" max="100" step="0.01" formControlName="comisionPorcentaje" />
              <span class="cd-xs cd-muted">Se descuenta al propietario en cada liquidación.</span>
              @if (form.controls.comisionPorcentaje.invalid && form.controls.comisionPorcentaje.touched) {
                <span class="cd-error">Debe estar entre 0 y 100.</span>
              }
            </label>
          </div>
        </section>

        <section>
          <h3 class="seccion">Cancelaciones y reembolsos</h3>
          <div class="grid">
            <label class="cd-field">
              <span class="cd-field__label">Horas mínimas para cancelar</span>
              <input cdInput type="number" min="0" max="720" formControlName="horasMinimasCancelacion" />
              <span class="cd-xs cd-muted">Antelación mínima antes del inicio de la reserva.</span>
            </label>
            <label class="cd-field">
              <span class="cd-field__label">Horas para reembolso gratuito</span>
              <input cdInput type="number" min="0" max="720" formControlName="horasCancelacionGratis" />
              <span class="cd-xs cd-muted">Si el cliente cancela con más antelación, se reembolsa el total.</span>
            </label>
          </div>
        </section>

        <section>
          <h3 class="seccion">Agenda y soporte</h3>
          <div class="grid">
            <label class="cd-field">
              <span class="cd-field__label">Días máximos de anticipación</span>
              <input cdInput type="number" min="1" max="365" formControlName="diasAnticipacionMax" />
              <span class="cd-xs cd-muted">Aplica a la búsqueda de disponibilidad y a nuevas reservas.</span>
            </label>
            <label class="cd-field">
              <span class="cd-field__label">Correo de soporte</span>
              <input cdInput type="email" maxlength="160" formControlName="correoSoporte" />
              <span class="cd-xs cd-muted">Contacto publicado para consultas de usuarios.</span>
              @if (form.controls.correoSoporte.invalid && form.controls.correoSoporte.touched) {
                <span class="cd-error">Ingresa un correo válido.</span>
              }
            </label>
          </div>
        </section>

        <footer class="acciones">
          <span class="cd-xs cd-muted">
            @if (configuracion()?.fechaActualizacion; as fecha) {
              Última actualización: {{ fecha | date: 'd MMM yyyy, HH:mm' }}
            } @else {
              Sin actualizaciones registradas.
            }
          </span>
          <button cdButton type="submit" [loading]="guardando()" [disabled]="form.invalid">Guardar cambios</button>
        </footer>
      </form>
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
    .configuracion {
      display: grid;
      gap: var(--cd-space-5);
      max-width: 920px;
    }
    .seccion {
      margin: 0 0 var(--cd-space-3);
      font-size: var(--cd-text-base);
    }
    .grid {
      display: grid;
      gap: var(--cd-space-3);
    }
    .cd-field__label {
      margin-bottom: 6px;
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
export class AdminConfiguracionComponent implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly configuracion = signal<AdminConfiguracion | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    comisionPorcentaje: [10, [Validators.required, Validators.min(0), Validators.max(100)]],
    horasCancelacionGratis: [24, [Validators.required, Validators.min(0), Validators.max(720)]],
    horasMinimasCancelacion: [2, [Validators.required, Validators.min(0), Validators.max(720)]],
    diasAnticipacionMax: [365, [Validators.required, Validators.min(1), Validators.max(365)]],
    correoSoporte: ['', [Validators.required, Validators.email, Validators.maxLength(160)]],
  });

  ngOnInit(): void {
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      const config = await firstValueFrom(this.api.configuracion());
      this.configuracion.set(config);
      this.form.reset({
        comisionPorcentaje: config.comisionPorcentaje,
        horasCancelacionGratis: config.horasCancelacionGratis,
        horasMinimasCancelacion: config.horasMinimasCancelacion,
        diasAnticipacionMax: config.diasAnticipacionMax,
        correoSoporte: config.correoSoporte,
      });
    } catch (error) {
      this.error.set(toApiError(error).message);
    } finally {
      this.cargando.set(false);
    }
  }

  protected async guardar(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    try {
      const actualizada = await firstValueFrom(this.api.actualizarConfiguracion(this.form.getRawValue()));
      this.configuracion.set(actualizada);
      this.toast.success('Configuración guardada.');
    } catch (error) {
      this.toast.error(toApiError(error).message);
    } finally {
      this.guardando.set(false);
    }
  }
}
