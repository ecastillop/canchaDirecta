import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from '../../core/auth/auth.api';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';

type Estado = 'pendiente' | 'verificando' | 'ok' | 'error';

@Component({
  selector: 'app-verificar',
  imports: [FormsModule, RouterLink, CdButtonDirective, CdInputDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 id="titulo-acceso">Verificar correo</h1>

    @switch (estado()) {
      @case ('verificando') {
        <p class="cd-muted" role="status">Verificando tu correo...</p>
      }
      @case ('ok') {
        <div class="exito" role="status">
          <p>Tu correo quedo verificado. Ya puedes iniciar sesión.</p>
          <a routerLink="/auth/login" class="cd-btn cd-btn--primary cd-btn--block">Iniciar sesión</a>
        </div>
      }
      @case ('error') {
        <p class="alerta" role="alert">{{ error() }}</p>
        <p class="cd-small cd-muted">Puedes pedir un nuevo enlace de verificación.</p>
      }
      @default {
        <p class="cd-muted">Pega el código o token que recibiste en tu correo.</p>
        @if (error()) {
          <p class="alerta" role="alert">{{ error() }}</p>
        }
      }
    }

    @if (estado() !== 'ok') {
      <form class="cd-stack" (ngSubmit)="verificar()" novalidate>
        <label class="cd-field">
          <span class="cd-field__label">Token de verificación</span>
          <input cdInput name="token" [(ngModel)]="tokenModel" autocomplete="one-time-code" />
        </label>
        <button cdButton type="submit" [loading]="cargando()" [block]="true">Verificar</button>
      </form>

      <hr class="cd-divider separador" />
      <form class="cd-stack" (ngSubmit)="reenviar()" novalidate>
        <label class="cd-field">
          <span class="cd-field__label">Reenviar verificación</span>
          <input cdInput type="email" name="correo" [(ngModel)]="correo" placeholder="tu correo" autocomplete="email" />
        </label>
        <button cdButton type="submit" variant="secondary" [loading]="reenviando()" [block]="true">
          Reenviar enlace
        </button>
      </form>
      @if (aviso()) {
        <p class="cd-small aviso-texto" role="status">{{ aviso() }}</p>
      }
    }
  `,
  styles: `
    h1 {
      font-size: var(--cd-text-xl);
      margin-bottom: var(--cd-space-3);
    }
    .alerta {
      margin-block: var(--cd-space-3);
      padding: var(--cd-space-3);
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-danger-soft);
      color: var(--cd-color-danger);
      font-size: var(--cd-text-sm);
      font-weight: 600;
    }
    .separador {
      margin-block: var(--cd-space-5);
    }
    .exito {
      display: grid;
      gap: var(--cd-space-4);
    }
    .exito .cd-btn {
      text-decoration: none;
    }
    .aviso-texto {
      margin-top: var(--cd-space-3);
      color: var(--cd-color-success);
      font-weight: 600;
    }
  `,
})
export class VerificarComponent implements OnInit {
  private readonly api = inject(AuthApi);

  /** Query param ?token= (llega por withComponentInputBinding). */
  readonly token = input<string | undefined>(undefined);

  protected readonly estado = signal<Estado>('pendiente');
  protected readonly cargando = signal(false);
  protected readonly reenviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected correo = '';
  protected tokenModel = '';

  ngOnInit(): void {
    const token = this.token();
    if (token) {
      this.tokenModel = token;
      void this.verificar();
    }
  }

  protected async verificar(): Promise<void> {
    const token = this.tokenModel.trim();
    if (!token) {
      this.error.set('Ingresa el token de verificación.');
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    this.estado.set('verificando');
    try {
      await firstValueFrom(this.api.verifyEmail(token));
      this.estado.set('ok');
    } catch (error) {
      this.error.set(toApiError(error).message);
      this.estado.set('error');
    } finally {
      this.cargando.set(false);
    }
  }

  protected async reenviar(): Promise<void> {
    const correo = this.correo.trim();
    if (!correo) {
      this.error.set('Ingresa tu correo para reenviar el enlace.');
      return;
    }
    this.reenviando.set(true);
    this.error.set(null);
    try {
      await firstValueFrom(this.api.resendVerification(correo));
      this.aviso.set('Si la cuenta existe, enviamos un nuevo enlace de verificación.');
    } catch (error) {
      this.error.set(toApiError(error).message);
    } finally {
      this.reenviando.set(false);
    }
  }
}
