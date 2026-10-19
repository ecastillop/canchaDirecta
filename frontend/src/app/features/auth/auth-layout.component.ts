import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-auth-layout',
  imports: [RouterOutlet, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="cd-skip-link" href="#contenido">Saltar al contenido</a>
    <div class="marco">
      <aside class="lado-marca">
        <a routerLink="/" class="marca" aria-label="CanchaDirecta, volver al inicio">
          <span class="marca__icono" aria-hidden="true">A</span>
          <span class="marca__texto">
            <strong>canchadirecta</strong>
            <small>reserva · juega · repite</small>
          </span>
        </a>
        <div class="lado-marca__texto">
          <p class="eyebrow">Tu próximo partido empieza aquí</p>
          <p class="lado-marca__titulo">El espacio ideal.<br /><em>El momento</em><br />perfecto.</p>
          <p>Reserva canchas, organiza tu equipo y vuelve a jugar sin complicaciones.</p>
        </div>
        <div class="cancha-art" aria-hidden="true">
          <span class="cancha-art__linea"></span>
          <span class="cancha-art__circulo"></span>
          <span class="balon">⚽</span>
        </div>
      </aside>
      <main id="contenido" tabindex="-1" class="contenido">
        <section class="tarjeta" aria-labelledby="titulo-acceso">
          <router-outlet />
        </section>
        <p class="nota">Los pagos y liquidaciones son simulados.</p>
      </main>
    </div>
  `,
  styles: `
    .marco {
      min-height: 100dvh;
      display: grid;
      grid-template-columns: 1.05fr 0.95fr;
      background: var(--cd-color-inverse-bg);
      color: var(--cd-color-inverse-text);
    }
    .lado-marca {
      position: relative;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      gap: 30px;
      overflow: hidden;
      padding: clamp(28px, 5vw, 60px);
      background:
        radial-gradient(circle at 80% 20%, color-mix(in srgb, var(--cd-color-teal, #167a70) 55%, transparent), transparent 48%),
        radial-gradient(circle at 20% 90%, color-mix(in srgb, var(--cd-color-accent) 16%, transparent), transparent 42%);
    }
    .marca {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      color: var(--cd-color-inverse-text);
      text-decoration: none;
    }
    .marca__icono {
      display: grid;
      place-items: center;
      width: 42px;
      height: 42px;
      border-radius: 13px;
      background: var(--cd-color-accent);
      color: var(--cd-color-primary);
      font-family: var(--cd-font-display);
      font-size: 22px;
      font-weight: 900;
      transform: rotate(-6deg);
    }
    .marca__texto {
      display: grid;
      line-height: 1;
    }
    .marca__texto strong {
      font-family: var(--cd-font-display);
      font-size: 20px;
      letter-spacing: -0.04em;
    }
    .marca__texto small {
      margin-top: 4px;
      color: var(--cd-color-inverse-muted);
      font-size: 8px;
      font-weight: 700;
      letter-spacing: 0.18em;
      text-transform: uppercase;
    }
    .lado-marca__texto {
      position: relative;
      z-index: 1;
      max-width: 520px;
    }
    .eyebrow {
      margin: 0 0 16px;
      color: var(--cd-color-accent);
      font-size: 11px;
      font-weight: 800;
      letter-spacing: 0.18em;
      text-transform: uppercase;
    }
    .lado-marca__titulo {
      margin: 0;
      font-size: clamp(42px, 5vw, 72px);
      line-height: 0.98;
      letter-spacing: -0.055em;
    }
    .lado-marca__titulo em {
      color: var(--cd-color-accent);
      font-style: normal;
    }
    .lado-marca__texto > p:last-child {
      max-width: 38ch;
      margin: 22px 0 0;
      color: var(--cd-color-inverse-muted);
    }
    .cancha-art {
      position: relative;
      width: min(100%, 430px);
      height: 170px;
      border-radius: 16px;
      background: linear-gradient(145deg, #1b6b54, #104737);
      transform: perspective(700px) rotateY(-8deg);
    }
    .cancha-art__linea {
      position: absolute;
      inset: 18% 12%;
      border: 2px solid rgba(255, 255, 255, 0.4);
    }
    .cancha-art__circulo {
      position: absolute;
      top: 50%;
      left: 50%;
      width: 34%;
      aspect-ratio: 1;
      border: 2px solid rgba(255, 255, 255, 0.4);
      border-radius: 50%;
      transform: translate(-50%, -50%);
    }
    .balon {
      position: absolute;
      right: 16%;
      top: 30%;
      font-size: 42px;
      filter: drop-shadow(0 12px 16px rgba(0, 0, 0, 0.3));
    }
    .contenido {
      display: grid;
      align-content: center;
      justify-items: center;
      gap: 14px;
      padding: clamp(24px, 4vw, 48px);
      background: var(--cd-color-bg);
      color: var(--cd-color-text);
    }
    .tarjeta {
      width: min(450px, 100%);
      padding: clamp(24px, 4vw, 36px);
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-lg);
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-2);
    }
    .nota {
      color: var(--cd-color-text-muted);
      font-size: var(--cd-text-xs);
      text-align: center;
    }
    @media (max-width: 900px) {
      .marco {
        grid-template-columns: 1fr;
      }
      .lado-marca {
        display: none;
      }
      .contenido {
        min-height: 100dvh;
      }
    }
  `,
})
export class AuthLayoutComponent {}
