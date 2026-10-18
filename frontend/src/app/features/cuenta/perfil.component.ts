import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { PropietarioApi } from '../../core/api/propietario.api';
import { PropietarioSolicitud } from '../../core/api/propietario.models';
import { AuthApi } from '../../core/auth/auth.api';
import { AuthStore } from '../../core/auth/auth.store';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { ToastService } from '../../shared/ui/toast/toast.service';

@Component({
  selector: 'app-perfil',
  imports: [ReactiveFormsModule, FormsModule, RouterLink, CdButtonDirective, CdInputDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cd-container cd-page">
      <header class="cd-page__header">
        <h1 class="cd-page__title">Mi perfil</h1>
        <p class="cd-muted">Actualiza tus datos de contacto. El correo y los roles no se pueden cambiar aquí.</p>
      </header>

      <div class="columnas">
        <section class="cd-card">
          <h2 class="seccion">Datos personales</h2>
          @if (error()) {
            <p class="alerta" role="alert">{{ error() }}</p>
          }
          <form class="cd-stack" [formGroup]="form" (ngSubmit)="guardar()" novalidate>
            <div class="dos">
              <label class="cd-field">
                <span class="cd-field__label">Nombres</span>
                <input cdInput formControlName="nombres" autocomplete="given-name" />
                @if (form.controls.nombres.invalid && form.controls.nombres.touched) {
                  <span class="cd-field__error">Ingresa tus nombres.</span>
                }
              </label>
              <label class="cd-field">
                <span class="cd-field__label">Apellidos</span>
                <input cdInput formControlName="apellidos" autocomplete="family-name" />
                @if (form.controls.apellidos.invalid && form.controls.apellidos.touched) {
                  <span class="cd-field__error">Ingresa tus apellidos.</span>
                }
              </label>
            </div>
            <label class="cd-field">
              <span class="cd-field__label">Teléfono</span>
              <input cdInput type="tel" formControlName="telefono" autocomplete="tel" />
            </label>
            <label class="cd-field">
              <span class="cd-field__label">Correo</span>
              <input cdInput [value]="auth.user()?.correo ?? ''" disabled />
              <span class="cd-field__hint">El correo de la cuenta no se puede modificar.</span>
            </label>
            <button cdButton type="submit" [loading]="guardando()">Guardar cambios</button>
          </form>
        </section>

        <div class="cd-stack">
          <section class="cd-card">
            <h2 class="seccion">Cuenta</h2>
            <dl class="datos">
              <div>
                <dt>Estado</dt>
                <dd>{{ auth.user()?.estado }}</dd>
              </div>
              <div>
                <dt>Correo verificado</dt>
                <dd>
                  <span class="cd-badge" [class]="auth.user()?.correoVerificado ? 'cd-badge--success' : 'cd-badge--warning'">
                    {{ auth.user()?.correoVerificado ? 'Verificado' : 'Pendiente' }}
                  </span>
                </dd>
              </div>
              <div>
                <dt>Roles</dt>
                <dd>{{ auth.roles().join(', ') || 'USER' }}</dd>
              </div>
            </dl>
            @if (!auth.user()?.correoVerificado) {
              <p class="cd-small cd-muted aviso">
                Verifica tu correo para poder reservar. Revisa el enlace enviado a
                <strong>{{ auth.user()?.correo }}</strong>.
              </p>
              <button
                cdButton
                variant="secondary"
                size="sm"
                type="button"
                [loading]="enviandoVerificacion()"
                (click)="reenviarVerificacion()"
              >
                Reenviar enlace de verificación
              </button>
            }
          </section>

          <section class="cd-card" aria-labelledby="propietario-titulo">
            <h2 id="propietario-titulo" class="seccion">Cuenta de propietario</h2>
            @if (auth.isAdmin()) {
              <p class="cd-small cd-muted">
                Tu cuenta es de administrador de plataforma: no necesitas solicitar el rol de propietario.
                @if (auth.isOwner()) {
                  Ya tienes el rol <strong>OWNER</strong>, así que puedes publicar y gestionar tus propios
                  establecimientos.
                } @else {
                  Si necesitas publicar canchas propias, asigna el rol <strong>OWNER</strong> desde
                  Administración → Usuarios → Roles.
                }
              </p>
              <div class="cd-row">
                <a routerLink="/admin" class="cd-btn cd-btn--secondary">Ir a Administración</a>
                @if (auth.isOwner()) {
                  <a routerLink="/panel" class="cd-btn cd-btn--primary">Ir al panel del propietario</a>
                }
              </div>
            } @else if (solicitud(); as s) {
              <p class="cd-small">
                Solicitud #{{ s.id }} ·
                <span
                  class="cd-badge"
                  [class]="
                    s.estadoValidacion === 'APROBADO'
                      ? 'cd-badge--success'
                      : s.estadoValidacion === 'RECHAZADO'
                        ? 'cd-badge--danger'
                        : 'cd-badge--warning'
                  "
                >
                  {{ s.estadoValidacion }}
                </span>
              </p>
              <p class="cd-small cd-muted">Documento: {{ s.documento }}</p>
              @if (s.estadoValidacion === 'PENDIENTE') {
                <p class="cd-small cd-muted">Un administrador revisará tu solicitud.</p>
              }
              @if (s.estadoValidacion === 'APROBADO' || s.estadoValidacion === 'VALIDADO') {
                <a routerLink="/panel" class="cd-btn cd-btn--primary">Ir al panel del propietario</a>
              }
            } @else {
              <p class="cd-small cd-muted">
                Envia tu documento de identidad para solicitar el rol de propietario y publicar canchas.
              </p>
              @if (errorSolicitud()) {
                <p class="alerta" role="alert">{{ errorSolicitud() }}</p>
              }
              <form class="cd-stack" (ngSubmit)="solicitar()" novalidate>
                <label class="cd-field">
                  <span class="cd-field__label">Documento de identidad</span>
                  <input cdInput name="documento" [(ngModel)]="documento" maxlength="20" autocomplete="off" />
                </label>
                <button cdButton variant="secondary" type="submit" [loading]="solicitando()">
                  Solicitar ser propietario
                </button>
              </form>
            }
          </section>
        </div>
      </div>
    </div>
  `,
  styles: `
    .columnas {
      display: grid;
      gap: var(--cd-space-4);
    }
    .seccion {
      font-size: var(--cd-text-base);
      margin-bottom: var(--cd-space-3);
    }
    .dos {
      display: grid;
      gap: var(--cd-space-3);
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
    }
    .aviso {
      margin-top: var(--cd-space-3);
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
    .cd-card .cd-btn {
      text-decoration: none;
    }
    @media (min-width: 768px) {
      .dos {
        grid-template-columns: 1fr 1fr;
      }
    }
    @media (min-width: 1024px) {
      .columnas {
        grid-template-columns: 1.3fr 1fr;
        align-items: start;
      }
    }
  `,
})
export class PerfilComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly propietarios = inject(PropietarioApi);
  private readonly authApi = inject(AuthApi);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthStore);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly enviandoVerificacion = signal(false);
  protected readonly solicitud = signal<PropietarioSolicitud | null>(null);
  protected readonly solicitando = signal(false);
  protected readonly errorSolicitud = signal<string | null>(null);
  protected documento = '';

  protected readonly form = this.fb.nonNullable.group({
    nombres: ['', [Validators.required, Validators.maxLength(80)]],
    apellidos: ['', [Validators.required, Validators.maxLength(80)]],
    telefono: ['', [Validators.maxLength(20)]],
  });

  ngOnInit(): void {
    const user = this.auth.user();
    if (user) {
      this.form.patchValue({
        nombres: user.nombres,
        apellidos: user.apellidos,
        telefono: user.telefono ?? '',
      });
    }
    this.cargarSolicitud();
  }

  protected async guardar(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    this.error.set(null);
    try {
      const value = this.form.getRawValue();
      await this.auth.updateProfile({
        nombres: value.nombres.trim(),
        apellidos: value.apellidos.trim(),
        telefono: value.telefono.trim() || undefined,
      });
      this.toast.success('Perfil actualizado.');
    } catch (error) {
      this.error.set(toApiError(error).message);
    } finally {
      this.guardando.set(false);
    }
  }

  protected async solicitar(): Promise<void> {
    const documento = this.documento.trim();
    if (!documento) {
      this.errorSolicitud.set('Ingresa tu documento de identidad.');
      return;
    }
    this.solicitando.set(true);
    this.errorSolicitud.set(null);
    try {
      this.solicitud.set(await firstValueFrom(this.propietarios.solicitar(documento)));
      this.toast.success('Solicitud enviada. Un administrador la revisará.');
    } catch (error) {
      this.errorSolicitud.set(toApiError(error).message);
    } finally {
      this.solicitando.set(false);
    }
  }

  protected async reenviarVerificacion(): Promise<void> {
    const correo = this.auth.user()?.correo;
    if (!correo) {
      return;
    }
    this.enviandoVerificacion.set(true);
    try {
      await firstValueFrom(this.authApi.resendVerification(correo));
      this.toast.success('Te enviamos un nuevo enlace de verificación.');
    } catch (error) {
      this.toast.error(toApiError(error).message);
    } finally {
      this.enviandoVerificacion.set(false);
    }
  }

  private async cargarSolicitud(): Promise<void> {
    try {
      this.solicitud.set(await firstValueFrom(this.propietarios.miSolicitud()));
    } catch (error) {
      if (toApiError(error).status !== 404) {
        this.errorSolicitud.set(toApiError(error).message);
      }
    }
  }
}
