import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthStore } from '../../core/auth/auth.store';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';

@Component({
  selector: 'app-home',
  imports: [ReactiveFormsModule, RouterLink, CdButtonDirective, CdInputDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hero">
      <div class="cd-container hero__inner">
        <div class="hero__texto">
          <p class="etiqueta">Reserva canchas en Lima</p>
          <h1>Encuentra tu cancha, reserva la hora y juega.</h1>
          <p class="hero__bajada">
            Busca por distrito y deporte, compara precios y disponibilidad, reserva con cochera
            opcional y paga en modo demostración.
          </p>

          <form class="buscador" (ngSubmit)="buscar()" role="search">
            <label class="cd-field buscador__campo">
              <span class="cd-visually-hidden">Que buscas</span>
              <input
                cdInput
                [formControl]="q"
                type="search"
                placeholder="Cancha, establecimiento o distrito"
                autocomplete="off"
              />
            </label>
            <button cdButton type="submit">Buscar canchas</button>
          </form>

          <div class="hero__acciones">
            @if (!auth.isAuthenticated()) {
              <a routerLink="/auth/registro" class="cd-btn cd-btn--secondary">Crear cuenta</a>
            } @else {
              <a routerLink="/mis-reservas" class="cd-btn cd-btn--secondary">Ver mis reservas</a>
            }
            <a routerLink="/canchas" class="cd-btn cd-btn--ghost">Explorar catálogo</a>
          </div>
        </div>

        <div class="hero__panel" aria-hidden="true">
          <div class="mini-card">
            <span class="mini-card__hora">18:00</span>
            <span class="mini-card__cancha">Cancha sintetica · 7 vs 7</span>
            <span class="cd-badge cd-badge--success">Disponible</span>
          </div>
          <div class="mini-card mini-card--alt">
            <span class="mini-card__hora">20:00</span>
            <span class="mini-card__cancha">Los Olivos · Techada</span>
            <span class="cd-badge cd-badge--info">S/ 90 / hora</span>
          </div>
          <div class="mini-card">
            <span class="mini-card__hora">21:00</span>
            <span class="mini-card__cancha">Surco · Cochera incluida</span>
            <span class="cd-badge cd-badge--warning">Última hora</span>
          </div>
        </div>
      </div>
    </section>

    <section class="cd-container beneficios" aria-labelledby="beneficios-titulo">
      <h2 id="beneficios-titulo">Todo el flujo en un solo lugar</h2>
      <div class="grid">
        <article class="cd-card">
          <h3>Busca y compara</h3>
          <p class="cd-muted cd-small">Filtra por distrito, deporte y precio; compara hasta 4 canchas lado a lado.</p>
        </article>
        <article class="cd-card">
          <h3>Reserva sin choques</h3>
          <p class="cd-muted cd-small">
            La disponibilidad se calcula por fecha y hora; una franja solo puede tomarla un usuario.
          </p>
        </article>
        <article class="cd-card">
          <h3>Paga en modo demo</h3>
          <p class="cd-muted cd-small">
            Pago simulado con comprobante demo, reembolsos por reglas y liquidaciones del propietario.
          </p>
        </article>
        <article class="cd-card">
          <h3>Gestiona tu cancha</h3>
          <p class="cd-muted cd-small">
            Los propietarios administran canchas, agenda, bloqueos, promociones y reservas recibidas.
          </p>
        </article>
      </div>
    </section>

    <section class="cd-container cta" aria-labelledby="cta-titulo">
      <div>
        <h2 id="cta-titulo">Tienes una cancha?</h2>
        <p class="cd-muted">Solicita ser propietario y pública tus canchas con agenda y promociones.</p>
      </div>
      <a routerLink="/perfil" class="cd-btn cd-btn--accent">Solicitar ser propietario</a>
    </section>
  `,
  styles: `
    .hero {
      background:
        radial-gradient(900px 420px at 85% 0%, color-mix(in srgb, var(--cd-color-accent) 22%, transparent), transparent),
        var(--cd-color-surface);
      border-bottom: 1px solid var(--cd-color-border);
    }
    .hero__inner {
      display: grid;
      gap: var(--cd-space-6);
      padding-block: var(--cd-space-7);
    }
    .etiqueta {
      display: inline-block;
      padding: 4px 10px;
      border-radius: var(--cd-radius-pill);
      background: color-mix(in srgb, var(--cd-color-accent) 16%, transparent);
      color: var(--cd-color-accent-strong);
      font-size: var(--cd-text-xs);
      font-weight: 800;
      letter-spacing: 0.02em;
      margin-bottom: var(--cd-space-3);
    }
    h1 {
      font-size: clamp(1.75rem, 4.6vw, 2.75rem);
      max-width: 20ch;
    }
    .hero__bajada {
      margin-top: var(--cd-space-3);
      max-width: 58ch;
      color: var(--cd-color-text-muted);
    }
    .buscador {
      display: flex;
      flex-wrap: wrap;
      gap: var(--cd-space-2);
      margin-top: var(--cd-space-5);
      align-items: end;
    }
    .buscador__campo {
      flex: 1 1 260px;
    }
    .hero__acciones {
      display: flex;
      flex-wrap: wrap;
      gap: var(--cd-space-2);
      margin-top: var(--cd-space-4);
    }
    .hero__acciones .cd-btn {
      text-decoration: none;
    }
    .hero__panel {
      display: grid;
      gap: var(--cd-space-3);
      align-content: center;
    }
    .mini-card {
      display: flex;
      align-items: center;
      gap: var(--cd-space-3);
      padding: var(--cd-space-4);
      background: var(--cd-color-surface);
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-md);
      box-shadow: var(--cd-shadow-2);
    }
    .mini-card--alt {
      margin-left: var(--cd-space-6);
    }
    @media (max-width: 640px) {
      .mini-card--alt {
        margin-left: 0;
      }
    }
    .mini-card__hora {
      font-weight: 800;
      font-size: var(--cd-text-lg);
      color: var(--cd-color-primary);
    }
    .mini-card__cancha {
      flex: 1;
      font-size: var(--cd-text-sm);
      color: var(--cd-color-text-muted);
    }
    .beneficios {
      padding-block: var(--cd-space-7);
    }
    .beneficios h2 {
      margin-bottom: var(--cd-space-4);
    }
    .grid {
      display: grid;
      gap: var(--cd-space-4);
    }
    .grid h3 {
      font-size: var(--cd-text-base);
      margin-bottom: var(--cd-space-1);
    }
    .cta {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--cd-space-4);
      padding: var(--cd-space-5);
      margin-bottom: var(--cd-space-7);
      background: var(--cd-color-primary);
      color: var(--cd-color-on-primary);
      border-radius: var(--cd-radius-lg);
    }
    .cta h2 {
      font-size: var(--cd-text-lg);
    }
    .cta p {
      color: color-mix(in srgb, var(--cd-color-on-primary) 78%, transparent);
    }
    .cta .cd-btn {
      text-decoration: none;
    }
    @media (min-width: 768px) {
      .grid {
        grid-template-columns: repeat(2, 1fr);
      }
    }
    @media (min-width: 1024px) {
      .hero__inner {
        grid-template-columns: 1.15fr 0.85fr;
        align-items: center;
      }
      .grid {
        grid-template-columns: repeat(4, 1fr);
      }
    }
  `,
})
export class HomeComponent {
  protected readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  protected readonly q = new FormControl('', { nonNullable: true });

  protected buscar(): void {
    const termino = this.q.value.trim();
    void this.router.navigate(['/canchas'], { queryParams: termino ? { q: termino } : {} });
  }
}
