import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-como-funciona',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hero-como">
      <div class="cd-container">
        <p class="eyebrow">Cómo funciona</p>
        <h1>Menos mensajes.<br /><em>Más momentos.</em></h1>
        <p>Una experiencia completa para jugadores, equipos y espacios deportivos.</p>
      </div>
    </section>

    <div class="cd-container pasos">
      @for (paso of pasos; track paso.numero) {
        <article>
          <span>{{ paso.numero }}</span>
          <h2>{{ paso.titulo }}</h2>
          <p>{{ paso.detalle }}</p>
        </article>
      }
    </div>

    <section class="cta">
      <div class="cd-container cta__inner">
        <div>
          <h2>Tu próximo partido empieza aquí.</h2>
          <p>Busca espacios disponibles, elige tu horario y reserva.</p>
        </div>
        <a routerLink="/canchas" class="cd-btn cd-btn--accent">Buscar espacios →</a>
      </div>
    </section>
  `,
  styles: `
    .hero-como {
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
    .hero-como p:last-child {
      max-width: 48ch;
      margin: 20px 0 0;
      color: var(--cd-color-inverse-muted);
    }
    .pasos {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 20px;
      padding-block: 48px 64px;
    }
    .pasos article {
      padding: 24px;
      border-top: 2px solid var(--cd-color-accent);
      background: var(--cd-color-surface);
    }
    .pasos span {
      color: var(--cd-color-teal, #167a70);
      font-size: 11px;
      font-weight: 800;
      letter-spacing: 0.14em;
    }
    h2 {
      margin: 12px 0 8px;
      font-size: 23px;
    }
    .pasos p {
      margin: 0;
      color: var(--cd-color-text-muted);
      font-size: 13px;
      line-height: 1.6;
    }
    .cta {
      padding: 44px 0;
      background: var(--cd-color-inverse-bg);
      color: var(--cd-color-inverse-text);
    }
    .cta__inner {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 24px;
      flex-wrap: wrap;
    }
    .cta h2 {
      margin: 0 0 6px;
    }
    .cta p {
      margin: 0;
      color: var(--cd-color-inverse-muted);
    }
    @media (max-width: 900px) {
      .pasos {
        grid-template-columns: 1fr 1fr;
      }
    }
    @media (max-width: 560px) {
      .pasos {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class ComoFuncionaComponent {
  protected readonly pasos = [
    { numero: '01', titulo: 'Descubre', detalle: 'Filtra por deporte, distrito, precio y servicios.' },
    { numero: '02', titulo: 'Elige tu horario', detalle: 'Consulta la semana completa y selecciona día y hora.' },
    { numero: '03', titulo: 'Confirma', detalle: 'Reserva con pago demo y recibe tu comprobante.' },
    { numero: '04', titulo: 'Juega', detalle: 'Gestiona tus reservas, favoritos y reseñas.' },
  ];
}
