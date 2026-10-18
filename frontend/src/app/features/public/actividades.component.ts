import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-actividades',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hero-actividades">
      <div class="cd-container">
        <p class="eyebrow">Actividades y comunidad</p>
        <h1>El deporte se vive <em>en equipo.</em></h1>
        <p>Torneos, clases y partidos abiertos para volver a jugar con otros.</p>
      </div>
    </section>

    <div class="cd-container contenido">
      <div class="grid">
        <article class="actividad">
          <span class="actividad__icono" aria-hidden="true">🏆</span>
          <small>Torneos</small>
          <h2>Compite con tu equipo</h2>
          <p>Organiza torneos relámpago y encuentra canchas con disponibilidad para varias fechas.</p>
          <a routerLink="/canchas" class="cd-btn cd-btn--secondary cd-btn--sm">Buscar espacios →</a>
        </article>
        <article class="actividad">
          <span class="actividad__icono" aria-hidden="true">🎯</span>
          <small>Clases</small>
          <h2>Aprende con profesionales</h2>
          <p>Encuentra espacios equipados para entrenar fútbol, pádel, básquet, vóley o tenis.</p>
          <a routerLink="/canchas" class="cd-btn cd-btn--secondary cd-btn--sm">Ver canchas →</a>
        </article>
        <article class="actividad">
          <span class="actividad__icono" aria-hidden="true">🤝</span>
          <small>Partidos abiertos</small>
          <h2>Completa tu equipo</h2>
          <p>Reserva una cancha y comparte el enlace con tus amigos para coordinar el próximo partido.</p>
          <a routerLink="/canchas" class="cd-btn cd-btn--secondary cd-btn--sm">Reservar ahora →</a>
        </article>
      </div>

      <div class="aviso">
        <strong>¿Organizas una actividad?</strong>
        <p>Escríbenos a soporte&#64;canchadirecta.demo y la publicamos en la comunidad.</p>
      </div>
    </div>
  `,
  styles: `
    .hero-actividades {
      padding: clamp(56px, 8vw, 96px) 0 clamp(48px, 6vw, 76px);
      background: var(--cd-color-inverse-bg);
      color: var(--cd-color-inverse-text);
    }
    .eyebrow {
      margin: 0 0 14px;
      color: var(--cd-color-accent);
      font-size: 11px;
      font-weight: 800;
      letter-spacing: 0.2em;
      text-transform: uppercase;
    }
    h1 {
      margin: 0;
      font-size: clamp(42px, 5.4vw, 70px);
      letter-spacing: -0.05em;
    }
    h1 em {
      color: var(--cd-color-accent);
      font-style: normal;
    }
    .hero-actividades p:last-child {
      max-width: 46ch;
      margin: 20px 0 0;
      color: var(--cd-color-inverse-muted);
    }
    .contenido {
      padding-block: 44px 70px;
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 20px;
    }
    .actividad {
      display: grid;
      gap: 8px;
      padding: 24px;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-lg);
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-1);
    }
    .actividad__icono {
      font-size: 34px;
    }
    .actividad small {
      color: var(--cd-color-teal, #167a70);
      font-size: 10px;
      font-weight: 800;
      letter-spacing: 0.14em;
      text-transform: uppercase;
    }
    h2 {
      margin: 0;
      font-size: 24px;
    }
    .actividad p {
      margin: 0;
      color: var(--cd-color-text-muted);
      font-size: 13px;
      line-height: 1.6;
    }
    .actividad .cd-btn {
      justify-self: start;
      margin-top: 8px;
      text-decoration: none;
    }
    .aviso {
      margin-top: 26px;
      padding: 20px 24px;
      border-radius: var(--cd-radius-md);
      background: var(--cd-color-surface-alt);
    }
    .aviso p {
      margin: 4px 0 0;
      color: var(--cd-color-text-muted);
      font-size: 13px;
    }
    @media (max-width: 900px) {
      .grid {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class ActividadesComponent {}
