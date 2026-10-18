import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-auth-layout',
  imports: [RouterOutlet, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="marco">
      <header>
        <a routerLink="/" class="marca" aria-label="CanchaDirecta, volver al inicio">
          <span class="marca__icono" aria-hidden="true">CD</span>
          <span>CanchaDirecta</span>
        </a>
      </header>
      <main class="contenido">
        <section class="tarjeta" aria-labelledby="titulo-acceso">
          <router-outlet />
        </section>
        <p class="nota cd-xs cd-muted">Los pagos y liquidaciones son simulados.</p>
      </main>
    </div>
  `,
  styles: `
    .marco {
      min-height: 100dvh;
      display: flex;
      flex-direction: column;
      background:
        radial-gradient(1200px 400px at 15% -10%, color-mix(in srgb, var(--cd-color-accent) 18%, transparent), transparent),
        var(--cd-color-bg);
    }
    header {
      padding: var(--cd-space-4);
    }
    .marca {
      display: inline-flex;
      align-items: center;
      gap: var(--cd-space-2);
      font-weight: 800;
      text-decoration: none;
      color: var(--cd-color-primary);
    }
    .marca__icono {
      display: inline-grid;
      place-items: center;
      width: 36px;
      height: 36px;
      border-radius: 10px;
      background: var(--cd-color-primary);
      color: var(--cd-color-on-primary);
      font-size: 12px;
    }
    .contenido {
      flex: 1;
      display: grid;
      align-content: center;
      justify-items: center;
      gap: var(--cd-space-4);
      padding: var(--cd-space-4) var(--cd-space-4) var(--cd-space-7);
    }
    .tarjeta {
      width: min(460px, 100%);
      background: var(--cd-color-surface);
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-lg);
      box-shadow: var(--cd-shadow-2);
      padding: var(--cd-space-5);
    }
    .nota {
      text-align: center;
      max-width: 46ch;
    }
  `,
})
export class AuthLayoutComponent {}
