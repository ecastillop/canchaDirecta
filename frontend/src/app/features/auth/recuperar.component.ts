import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from '../../core/auth/auth.api';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';

@Component({
  selector: 'app-recuperar',
  imports: [ReactiveFormsModule, RouterLink, CdButtonDirective, CdInputDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (confirmado()) {
      <h1 id="titulo-acceso">Contraseña actualizada</h1>
      <p class="cd-muted">Ya puedes iniciar sesión con tu nueva contraseña.</p>
      <a routerLink="/auth/login" class="cd-btn cd-btn--primary cd-btn--block accion">Iniciar sesión</a>
    } @else if (solicitado()) {
      <h1 id="titulo-acceso">Revisa tu correo</h1>
      <p class="cd-muted">
        Si el correo está registrado, enviamos un enlace para restablecer la contraseña. El enlace
        caduca en poco tiempo.
      </p>
      <p class="cd-small cd-muted">Pega abajo el token recibido para definir tu nueva contraseña.</p>
    } @else if (!modoConfirmacion()) {
      <h1 id="titulo-acceso">Recuperar contraseña</h1>
      <p class="cd-muted intro">Escribe tu correo y te enviaremos un enlace de recuperación.</p>
      @if (error()) {
        <p class="alerta" role="alert">{{ error() }}</p>
      }
      <form class="cd-stack" [formGroup]="solicitud" (ngSubmit)="solicitar()" novalidate>
        <label class="cd-field">
          <span class="cd-field__label">Correo electrónico</span>
          <input cdInput type="email" formControlName="correo" autocomplete="email" />
        </label>
        <button cdButton type="submit" [loading]="cargando()" [block]="true">Enviar enlace</button>
      </form>
      <p class="cd-small cd-muted pie-form"><a routerLink="/auth/login">Volver a iniciar sesión</a></p>
    }

    @if (modoConfirmacion() || solicitado()) {
      @if (error()) {
        <p class="alerta" role="alert">{{ error() }}</p>
      }
      <form class="cd-stack" [formGroup]="confirmacion" (ngSubmit)="confirmar()" novalidate>
        @if (!modoConfirmacion()) {
          <label class="cd-field">
            <span class="cd-field__label">Token de recuperación</span>
            <input cdInput formControlName="token" autocomplete="one-time-code" />
          </label>
        }
        <label class="cd-field">
          <span class="cd-field__label">Nueva contraseña</span>
          <input cdInput type="password" formControlName="nuevaPassword" autocomplete="new-password" />
          <span class="cd-field__hint">Mínimo 8 caracteres.</span>
        </label>
        <label class="cd-field">
          <span class="cd-field__label">Confirmar contraseña</span>
          <input cdInput type="password" formControlName="confirmacionPassword" autocomplete="new-password" />
        </label>
        <button cdButton type="submit" [loading]="cargando()" [block]="true">Cambiar contraseña</button>
      </form>
    }
  `,
  styles: `
    h1 {
      font-size: var(--cd-text-xl);
      margin-bottom: var(--cd-space-2);
    }
    .intro {
      margin-bottom: var(--cd-space-4);
      font-size: var(--cd-text-sm);
    }
    .alerta {
      margin-bottom: var(--cd-space-4);
      padding: var(--cd-space-3);
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-danger-soft);
      color: var(--cd-color-danger);
      font-size: var(--cd-text-sm);
      font-weight: 600;
    }
    .pie-form {
      margin-top: var(--cd-space-4);
    }
    .accion {
      margin-top: var(--cd-space-4);
      text-decoration: none;
    }
  `,
})
export class RecuperarComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AuthApi);

  /** Query param ?token= */
  readonly token = input<string | undefined>(undefined);

  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly solicitado = signal(false);
  protected readonly confirmado = signal(false);
  protected readonly modoConfirmacion = signal(false);

  protected readonly solicitud = this.fb.nonNullable.group({
    correo: ['', [Validators.required, Validators.email]],
  });

  protected readonly confirmacion = this.fb.nonNullable.group({
    token: ['', [Validators.required]],
    nuevaPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(100)]],
    confirmacionPassword: ['', [Validators.required]],
  });

  ngOnInit(): void {
    const token = this.token();
    if (token) {
      this.modoConfirmacion.set(true);
      this.confirmacion.controls.token.setValue(token);
    }
  }

  protected async solicitar(): Promise<void> {
    if (this.solicitud.invalid) {
      this.solicitud.markAllAsTouched();
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    try {
      await firstValueFrom(this.api.requestPasswordReset(this.solicitud.controls.correo.value.trim()));
      this.solicitado.set(true);
    } catch (error) {
      this.error.set(toApiError(error).message);
    } finally {
      this.cargando.set(false);
    }
  }

  protected async confirmar(): Promise<void> {
    const { token, nuevaPassword, confirmacionPassword } = this.confirmacion.getRawValue();
    if (this.confirmacion.invalid) {
      this.confirmacion.markAllAsTouched();
      return;
    }
    if (nuevaPassword !== confirmacionPassword) {
      this.error.set('Las contrasenas no coinciden.');
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    try {
      await firstValueFrom(this.api.confirmPasswordReset(token.trim(), nuevaPassword, confirmacionPassword));
      this.confirmado.set(true);
    } catch (error) {
      this.error.set(toApiError(error).message);
    } finally {
      this.cargando.set(false);
    }
  }
}
