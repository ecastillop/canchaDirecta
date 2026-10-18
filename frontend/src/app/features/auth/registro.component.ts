import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthStore } from '../../core/auth/auth.store';
import { ApiError, toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';

function coinciden(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password')?.value;
  const confirmacion = group.get('confirmacionPassword')?.value;
  return password && confirmacion && password !== confirmacion ? { noCoinciden: true } : null;
}

@Component({
  selector: 'app-registro',
  imports: [ReactiveFormsModule, RouterLink, CdButtonDirective, CdInputDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (registrado()) {
      <div class="exito" role="status">
        <h1 id="titulo-acceso">Revisa tu correo</h1>
        <p>
          Creamos tu cuenta. Te enviamos un enlace de verificación a
          <strong>{{ correoRegistrado() }}</strong>. Verifica tu correo para iniciar sesión.
        </p>
        <p class="cd-small cd-muted">
          Si no llega el mensaje, puedes pedir uno nuevo desde la pantalla de verificación.
        </p>
        <a
          routerLink="/auth/login"
          [queryParams]="redirect() ? { redirect: redirect() } : {}"
          class="cd-btn cd-btn--primary cd-btn--block"
        >
          Ir a iniciar sesión
        </a>
      </div>
    } @else {
      <h1 id="titulo-acceso">Crear cuenta</h1>
      <p class="cd-muted intro">Regístrate para reservar y gestionar tus canchas.</p>

      @if (error()) {
        <p class="alerta" role="alert">{{ error() }}</p>
      }

      <form class="cd-stack" [formGroup]="form" (ngSubmit)="enviar()" novalidate>
        <div class="dos">
          <label class="cd-field">
            <span class="cd-field__label">Nombres</span>
            <input cdInput formControlName="nombres" autocomplete="given-name" [class.is-invalid]="invalido('nombres')" />
            @if (invalido('nombres')) {
              <span class="cd-field__error">Ingresa tus nombres.</span>
            }
          </label>
          <label class="cd-field">
            <span class="cd-field__label">Apellidos</span>
            <input cdInput formControlName="apellidos" autocomplete="family-name" [class.is-invalid]="invalido('apellidos')" />
            @if (invalido('apellidos')) {
              <span class="cd-field__error">Ingresa tus apellidos.</span>
            }
          </label>
        </div>

        <label class="cd-field">
          <span class="cd-field__label">Correo electrónico</span>
          <input cdInput type="email" formControlName="correo" autocomplete="email" [class.is-invalid]="invalido('correo')" />
          @if (invalido('correo')) {
            <span class="cd-field__error">Ingresa un correo válido.</span>
          }
        </label>

        <label class="cd-field">
          <span class="cd-field__label">Teléfono <span class="optional">(opcional)</span></span>
          <input cdInput type="tel" formControlName="telefono" autocomplete="tel" />
        </label>

        <label class="cd-field">
          <span class="cd-field__label">Contraseña</span>
          <input cdInput type="password" formControlName="password" autocomplete="new-password" [class.is-invalid]="invalido('password')" />
          <span class="cd-field__hint">Mínimo 8 caracteres.</span>
        </label>

        <label class="cd-field">
          <span class="cd-field__label">Confirmar contraseña</span>
          <input
            cdInput
            type="password"
            formControlName="confirmacionPassword"
            autocomplete="new-password"
            [class.is-invalid]="invalido('confirmacionPassword')"
          />
          @if (form.errors?.['noCoinciden'] && form.controls.confirmacionPassword.touched) {
            <span class="cd-field__error">Las contrasenas no coinciden.</span>
          }
        </label>

        <button cdButton type="submit" [loading]="cargando()" [block]="true">Crear cuenta</button>
      </form>

      <p class="cd-small cd-muted pie-form">
        Ya tienes cuenta?
        <a routerLink="/auth/login" [queryParams]="redirect() ? { redirect: redirect() } : {}">Ingresa</a>
      </p>
    }
  `,
  styles: `
    h1 {
      font-size: var(--cd-text-xl);
      margin-bottom: var(--cd-space-1);
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
    .dos {
      display: grid;
      gap: var(--cd-space-3);
    }
    @media (min-width: 520px) {
      .dos {
        grid-template-columns: 1fr 1fr;
      }
    }
    .pie-form {
      margin-top: var(--cd-space-4);
    }
    .exito {
      display: grid;
      gap: var(--cd-space-3);
    }
    .exito .cd-btn {
      margin-top: var(--cd-space-3);
      text-decoration: none;
    }
  `,
})
export class RegistroComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  /** Ruta a la que volver tras registrarse (por ejemplo, el checkout). */
  readonly redirect = input<string | undefined>(undefined);

  protected readonly cargando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly registrado = signal(false);
  protected readonly correoRegistrado = signal('');

  protected readonly form = this.fb.nonNullable.group(
    {
      nombres: ['', [Validators.required, Validators.maxLength(80)]],
      apellidos: ['', [Validators.required, Validators.maxLength(80)]],
      correo: ['', [Validators.required, Validators.email]],
      telefono: ['', [Validators.maxLength(20)]],
      password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(100)]],
      confirmacionPassword: ['', [Validators.required]],
    },
    { validators: coinciden },
  );

  protected invalido(campo: keyof typeof this.form.controls): boolean {
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
      const value = this.form.getRawValue();
      await this.auth.register({
        nombres: value.nombres.trim(),
        apellidos: value.apellidos.trim(),
        correo: value.correo.trim(),
        telefono: value.telefono.trim() || undefined,
        password: value.password,
        confirmacionPassword: value.confirmacionPassword,
      });
      this.correoRegistrado.set(value.correo.trim());
      try {
        // El backend no exige verificacion para operar: continuamos la reserva sin friccion.
        await this.auth.login(value.correo.trim(), value.password);
        const destino = this.redirect() || '/';
        await this.router.navigateByUrl(destino.startsWith('/') ? destino : '/');
      } catch (loginError) {
        const apiError: ApiError = toApiError(loginError);
        if (apiError.status === 401 || apiError.status === 403) {
          this.registrado.set(true);
        } else {
          throw loginError;
        }
      }
    } catch (error) {
      this.error.set(toApiError(error).message);
    } finally {
      this.cargando.set(false);
    }
  }
}
