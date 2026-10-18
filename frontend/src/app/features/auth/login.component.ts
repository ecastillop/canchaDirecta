import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiError, toApiError } from '../../core/http/api-error';
import { AuthStore } from '../../core/auth/auth.store';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, CdButtonDirective, CdInputDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 id="titulo-acceso">Iniciar sesión</h1>
    <p class="cd-muted intro">Accede para reservar canchas y gestionar tus reservas.</p>

    @if (error()) {
      <p class="alerta" role="alert">{{ error() }}</p>
    }

    <form class="cd-stack" [formGroup]="form" (ngSubmit)="enviar()" novalidate>
      <label class="cd-field">
        <span class="cd-field__label">Correo electrónico</span>
        <input
          cdInput
          type="email"
          formControlName="correo"
          autocomplete="email"
          [class.is-invalid]="invalido('correo')"
          [attr.aria-invalid]="invalido('correo')"
          aria-describedby="login-correo-error"
        />
        @if (invalido('correo')) {
          <span id="login-correo-error" class="cd-field__error">Ingresa un correo válido.</span>
        }
      </label>

      <label class="cd-field">
        <span class="cd-field__label">Contraseña</span>
        <input
          cdInput
          [type]="verPassword() ? 'text' : 'password'"
          formControlName="password"
          autocomplete="current-password"
          [class.is-invalid]="invalido('password')"
          [attr.aria-invalid]="invalido('password')"
          aria-describedby="login-password-error"
        />
        @if (invalido('password')) {
          <span id="login-password-error" class="cd-field__error">Ingresa tu contraseña (mínimo 8 caracteres).</span>
        }
      </label>

      <label class="cd-field cd-field--check">
        <input type="checkbox" [checked]="verPassword()" (change)="verPassword.set(!verPassword())" />
        <span class="cd-small">Mostrar contraseña</span>
      </label>

      <button cdButton type="submit" [loading]="cargando()" [block]="true">Ingresar</button>
    </form>

    <div class="enlaces">
      <a routerLink="/auth/recuperar">Olvide mi contraseña</a>
      <span class="cd-small cd-muted">
        No tienes cuenta?
        <a routerLink="/auth/registro" [queryParams]="redirect() ? { redirect: redirect() } : {}">Regístrate</a>
      </span>
    </div>
  `,
  styles: `
    h1 {
      font-size: var(--cd-text-xl);
      margin-bottom: var(--cd-space-1);
    }
    .intro {
      margin-bottom: var(--cd-space-5);
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
    .enlaces {
      display: grid;
      gap: var(--cd-space-3);
      margin-top: var(--cd-space-5);
      font-size: var(--cd-text-sm);
    }
  `,
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  /** Llega por query param (?redirect=...). */
  readonly redirect = input<string | undefined>(undefined);

  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly verPassword = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    correo: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  protected invalido(campo: 'correo' | 'password'): boolean {
    const control = this.form.controls[campo];
    return control.invalid && (control.touched || control.dirty);
  }

  protected async enviar(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    try {
      const { correo, password } = this.form.getRawValue();
      await this.auth.login(correo.trim(), password);
      const destino = this.redirect() || '/';
      await this.router.navigateByUrl(destino.startsWith('/') ? destino : '/');
    } catch (error) {
      const apiError: ApiError = toApiError(error);
      this.error.set(apiError.message);
    } finally {
      this.cargando.set(false);
    }
  }
}
