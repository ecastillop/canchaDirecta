import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthStore } from '../../core/auth/auth.store';
import { FavoritosService } from '../../core/api/favoritos.service';
import { VenuesApi } from '../../core/api/venues.api';
import { Cancha, Distrito, Item } from '../../core/api/venues.models';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdIconComponent } from '../../shared/ui/icon/cd-icon.component';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';

interface DeporteTile {
  id: number;
  nombre: string;
  emoji: string;
  clase: string;
}

const EMOJIS: Record<string, string> = {
  fútbol: '⚽',
  futbol: '⚽',
  pádel: '🎾',
  padel: '🎾',
  básquet: '🏀',
  basquet: '🏀',
  vóley: '🏐',
  voley: '🏐',
  tenis: '🎾',
};

@Component({
  selector: 'app-home',
  imports: [ReactiveFormsModule, RouterLink, CurrencyPipe, DecimalPipe, CdButtonDirective, CdIconComponent, CdInputDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hero">
      <div class="cd-container hero__grid">
        <div class="hero__copy">
          <p class="eyebrow"><span class="dot" aria-hidden="true"></span> Tu próximo partido empieza aquí</p>
          <h1>El espacio ideal.<br /><em>El momento</em><br />perfecto.</h1>
          <p class="hero__lead">
            Encuentra canchas, revisa horarios y reserva en minutos. Más deporte, menos coordinación.
          </p>
          <div class="hero__acciones">
            <a routerLink="/canchas" class="cd-btn cd-btn--lime">Buscar espacios <span aria-hidden="true">→</span></a>
            @if (auth.isAuthenticated()) {
              <a routerLink="/mis-reservas" class="cd-btn cd-btn--hero">Ver mis reservas</a>
            } @else {
              <a routerLink="/auth/registro" class="cd-btn cd-btn--hero">Crear mi cuenta</a>
            }
          </div>
          <div class="hero__social">
            <div class="avatares" aria-hidden="true"><span>AR</span><span>AG</span><span>DM</span><span>CR</span></div>
            <div>
              <strong>+2,400 jugadores</strong>
              <small>Encuentra tu próxima cancha</small>
            </div>
          </div>
        </div>

        <div class="hero__visual" aria-hidden="true">
          <div class="cancha-art">
            <div class="cancha-art__linea cancha-art__linea--vertical"></div>
            <div class="cancha-art__circulo"></div>
            <span class="cancha-art__numero">07</span>
            <span class="balon">⚽</span>
          </div>
          <div class="tarjeta-flotante">
            <span class="tarjeta-flotante__icono">✓</span>
            <div>
              <small>PRÓXIMA RESERVA</small>
              <strong>{{ destacadas()[0]?.nombre ?? 'Cancha Norte' }}</strong>
              <span>Hoy · 7:00 p. m.</span>
            </div>
          </div>
          <div class="sello-flotante">JUEGA<br /><strong>SIN LÍMITES.</strong></div>
        </div>
      </div>
    </section>

    <section class="busqueda cd-container" aria-label="Buscar espacios">
      <form class="tarjeta-busqueda" [formGroup]="form" (ngSubmit)="buscar()">
        <div class="tarjeta-busqueda__cabecera">
          <strong><span aria-hidden="true">⌕</span> Encuentra tu espacio</strong>
          <span class="cd-small cd-muted">Reserva en menos de 2 minutos</span>
        </div>
        <div class="tarjeta-busqueda__grid">
          <label class="cd-field">
            <span class="cd-field__label">¿Qué deporte?</span>
            <select cdInput formControlName="idDeporte">
              <option [ngValue]="null">Todos</option>
              @for (deporte of deportes(); track deporte.id) {
                <option [ngValue]="deporte.id">{{ deporte.nombre }}</option>
              }
            </select>
          </label>
          <label class="cd-field">
            <span class="cd-field__label">¿En qué distrito?</span>
            <select cdInput formControlName="idDistrito">
              <option [ngValue]="null">Todos</option>
              @for (distrito of distritos(); track distrito.id) {
                <option [ngValue]="distrito.id">{{ distrito.nombre }}</option>
              }
            </select>
          </label>
          <label class="cd-field">
            <span class="cd-field__label">¿Qué día?</span>
            <input cdInput type="date" formControlName="fecha" [min]="hoy" />
          </label>
          <button cdButton type="submit" class="buscar-btn">Ver disponibilidad <span aria-hidden="true">→</span></button>
        </div>
      </form>
    </section>

    <section class="cd-container seccion" aria-labelledby="deportes-titulo">
      <div class="seccion__cabecera">
        <div>
          <p class="eyebrow eyebrow--dark">Elige tu pasión</p>
          <h2 id="deportes-titulo">Un deporte para <em>cada momento.</em></h2>
        </div>
        <p class="cd-muted seccion__bajada">
          Desde partidos entre amigos hasta entrenamiento de alto nivel. Tu lugar está aquí.
        </p>
      </div>
      <div class="deportes">
        @for (deporte of deportesTiles(); track deporte.id; let i = $index) {
          <button type="button" [class]="'deporte deporte--' + deporte.clase" (click)="buscarDeporte(deporte.id)">
            <span class="deporte__numero">0{{ i + 1 }}</span>
            <span class="deporte__emoji" aria-hidden="true">{{ deporte.emoji }}</span>
            <strong>{{ deporte.nombre }}</strong>
            <span class="deporte__flecha" aria-hidden="true">→</span>
          </button>
        }
      </div>
    </section>

    <section class="seccion seccion--tinte" aria-labelledby="destacadas-titulo">
      <div class="cd-container">
        <div class="seccion__cabecera">
          <div>
            <p class="eyebrow eyebrow--dark">Descubre cerca de ti</p>
            <h2 id="destacadas-titulo">Espacios que <em>inspiran.</em></h2>
          </div>
          <a routerLink="/canchas" class="cd-btn cd-btn--secondary">Ver todos los espacios <span aria-hidden="true">→</span></a>
        </div>
        @if (cargandoDestacadas()) {
          <div class="destacadas">
            @for (item of [1, 2, 3]; track item) {
              <div class="tarjeta-espacio tarjeta-espacio--cargando" aria-hidden="true"></div>
            }
          </div>
        } @else {
          <div class="destacadas">
            @for (cancha of destacadas(); track cancha.id) {
              <article class="tarjeta-espacio">
                <div [class]="'tarjeta-espacio__media tarjeta-espacio__media--' + claseDeporte(cancha)">
                  <a
                    class="tarjeta-espacio__media-link"
                    [routerLink]="['/canchas', cancha.id]"
                    [attr.aria-label]="'Ver ' + cancha.nombre"
                  >
                    @if (cancha.fotoPrincipal) {
                      <img [src]="cancha.fotoPrincipal" [alt]="'Foto de ' + cancha.nombre" (error)="ocultarImagen($event)" />
                    }
                    <span class="tarjeta-espacio__deporte">{{ cancha.deportes[0] ?? 'Cancha' }}</span>
                  </a>
                  <button
                    type="button"
                    class="tarjeta-espacio__corazon"
                    [class.activo]="favoritos.tiene(cancha.id)"
                    (click)="alternarFavorito(cancha, $event)"
                    [attr.aria-label]="favoritos.tiene(cancha.id) ? 'Quitar de favoritos' : 'Agregar a favoritos'"
                    [attr.aria-pressed]="favoritos.tiene(cancha.id)"
                  >
                    {{ favoritos.tiene(cancha.id) ? '♥' : '♡' }}
                  </button>
                </div>
                <a class="tarjeta-espacio__cuerpo" [routerLink]="['/canchas', cancha.id]">
                  <div class="tarjeta-espacio__meta">
                    <span><cd-icon name="pin" [size]="13" /> {{ cancha.distrito }}</span>
                    <span class="rating">
                      <cd-icon name="star" [size]="13" />
                      {{ cancha.totalCalificaciones ? (cancha.promedioCalificacion | number: '1.1-1') : 'Nuevo' }}
                      @if (cancha.totalCalificaciones) {
                        <small>({{ cancha.totalCalificaciones }})</small>
                      }
                    </span>
                  </div>
                  <h3>{{ cancha.nombre }}</h3>
                  <p class="cd-small cd-muted">{{ cancha.establecimiento }}</p>
                  <div class="tarjeta-espacio__pie">
                    <span>Desde <strong>{{ cancha.precioBase | currency: 'PEN' : 'symbol-narrow' : '1.0-0' }}</strong> <small>/ hora</small></span>
                    <span class="flecha" aria-hidden="true">→</span>
                  </div>
                </a>
              </article>
            }
          </div>
        }
      </div>
    </section>

    <section class="cd-container seccion por-que">
      <div class="por-que__visual" aria-hidden="true">
        <div class="por-que__big">PLAY<br />MORE<span>.</span></div>
        <div class="por-que__strip">TU TIEMPO VALE ORO. APROVÉCHALO.</div>
      </div>
      <div class="por-que__contenido">
        <p class="eyebrow eyebrow--dark">Fácil y sin complicaciones</p>
        <h2>Todo listo.<br /><em>Solo falta jugar.</em></h2>
        <ol class="pasos">
          <li><span>01</span><div><h3>Encuentra el lugar</h3><p>Filtra por deporte, distrito, precio y servicios.</p></div></li>
          <li><span>02</span><div><h3>Elige tu horario</h3><p>Consulta la semana completa y selecciona el día y la hora.</p></div></li>
          <li><span>03</span><div><h3>Confirma y disfruta</h3><p>Reserva con pago demo y revisa tus comprobantes.</p></div></li>
        </ol>
        <a routerLink="/canchas" class="cd-btn cd-btn--primary">Empezar ahora <span aria-hidden="true">→</span></a>
      </div>
    </section>

    <section class="cta-final">
      <div class="cd-container cta-final__inner">
        <div>
          <p class="eyebrow">¿Administras espacios deportivos?</p>
          <h2>Tu negocio también<br /><em>juega en grande.</em></h2>
          <p>Gestiona sedes, canchas, agenda, tarifas, reservas y liquidaciones desde un solo lugar.</p>
        </div>
        @if (auth.isAuthenticated()) {
          <a routerLink="/panel" class="cd-btn cd-btn--lime">Ir a mi panel <span aria-hidden="true">→</span></a>
        } @else {
          <a routerLink="/auth/registro" class="cd-btn cd-btn--lime">Ser propietario <span aria-hidden="true">→</span></a>
        }
      </div>
    </section>
  `,
  styles: `
    :host {
      display: block;
    }
    .hero {
      position: relative;
      overflow: hidden;
      background: var(--cd-color-inverse-bg);
      color: var(--cd-color-inverse-text);
      padding: clamp(56px, 8vw, 104px) 0 clamp(120px, 12vw, 170px);
    }
    .hero::before {
      content: '';
      position: absolute;
      inset: -30% -10% auto 45%;
      height: 110%;
      background:
        radial-gradient(circle at 60% 30%, color-mix(in srgb, var(--cd-color-teal, #167a70) 42%, transparent), transparent 55%),
        radial-gradient(circle at 85% 70%, color-mix(in srgb, var(--cd-color-accent) 8%, transparent), transparent 45%);
      pointer-events: none;
    }
    .hero__grid {
      position: relative;
      display: grid;
      grid-template-columns: 1.05fr 0.95fr;
      gap: clamp(24px, 4vw, 70px);
      align-items: center;
    }
    .eyebrow {
      margin: 0 0 18px;
      color: var(--cd-color-brand-light);
      font-size: 11px;
      font-weight: 800;
      letter-spacing: 0.18em;
      text-transform: uppercase;
    }
    .eyebrow--dark {
      color: var(--cd-color-teal, #167a70);
    }
    .dot {
      display: inline-block;
      width: 7px;
      height: 7px;
      margin-right: 8px;
      border-radius: 50%;
      background: var(--cd-color-accent);
      box-shadow: 0 0 0 4px color-mix(in srgb, var(--cd-color-accent) 20%, transparent);
    }
    .hero h1 {
      margin: 0;
      font-size: clamp(46px, 6.3vw, 88px);
      line-height: 0.98;
      letter-spacing: -0.055em;
    }
    .hero h1 em,
    .seccion h2 em,
    .por-que h2 em,
    .cta-final h2 em {
      color: var(--cd-color-brand-light);
      font-style: normal;
    }
    .hero__lead {
      max-width: 38ch;
      margin: 26px 0 0;
      color: var(--cd-color-inverse-muted);
      font-size: clamp(15px, 1.3vw, 18px);
      line-height: 1.65;
    }
    .hero__acciones {
      display: flex;
      gap: 12px;
      flex-wrap: wrap;
      margin-top: 32px;
    }
    .hero .cd-btn--hero {
      border: 1px solid color-mix(in srgb, var(--cd-color-inverse-text) 32%, transparent);
      background: transparent;
      color: var(--cd-color-inverse-text);
    }
    .hero .cd-btn--hero:hover {
      border-color: var(--cd-color-brand-light);
      color: var(--cd-color-brand-light);
    }
    .hero__social {
      display: flex;
      align-items: center;
      gap: 14px;
      margin-top: 38px;
    }
    .avatares {
      display: flex;
    }
    .avatares span {
      display: grid;
      place-items: center;
      width: 34px;
      height: 34px;
      margin-left: -8px;
      border: 2px solid var(--cd-color-inverse-bg);
      border-radius: 50%;
      background: var(--cd-color-success-soft);
      color: var(--cd-color-brand-ink);
      font-size: 10px;
      font-weight: 800;
    }
    .avatares span:nth-child(2) {
      background: var(--cd-color-danger-soft);
    }
    .avatares span:nth-child(3) {
      background: var(--cd-color-info-soft);
    }
    .avatares span:nth-child(4) {
      background: var(--cd-color-warning-soft);
    }
    .avatares span:first-child {
      margin-left: 0;
    }
    .hero__social strong,
    .hero__social small {
      display: block;
    }
    .hero__social strong {
      font-size: 13px;
    }
    .hero__social small {
      color: var(--cd-color-inverse-muted);
      font-size: 11px;
    }
    .hero__visual {
      position: relative;
      min-height: 420px;
    }
    .cancha-art {
      position: relative;
      height: clamp(300px, 30vw, 420px);
      border-radius: 20px;
      background:
        repeating-linear-gradient(120deg, rgba(255, 255, 255, 0.04) 0 2px, transparent 2px 14px),
        linear-gradient(145deg, #1b6b54, #104737);
      box-shadow: var(--cd-shadow-3);
      transform: perspective(900px) rotateY(-9deg) rotateX(3deg);
      overflow: hidden;
    }
    .cancha-art__linea {
      position: absolute;
      inset: 12% 9%;
      border: 2px solid rgba(255, 255, 255, 0.5);
    }
    .cancha-art__linea--vertical {
      left: 50%;
      right: auto;
      width: 0;
      border-width: 0 0 0 2px;
      border-color: rgba(255, 255, 255, 0.45);
    }
    .cancha-art__circulo {
      position: absolute;
      top: 50%;
      left: 50%;
      width: 42%;
      aspect-ratio: 1;
      border: 2px solid rgba(255, 255, 255, 0.5);
      border-radius: 50%;
      transform: translate(-50%, -50%);
    }
    .cancha-art__numero {
      position: absolute;
      right: 6%;
      bottom: -4%;
      color: rgba(255, 255, 255, 0.12);
      font-family: var(--cd-font-display);
      font-size: clamp(120px, 14vw, 210px);
      font-weight: 900;
      line-height: 1;
    }
    .balon {
      position: absolute;
      top: 38%;
      right: 18%;
      font-size: clamp(48px, 6vw, 78px);
      filter: drop-shadow(0 18px 24px rgba(0, 0, 0, 0.35));
      animation: flotar 4s ease-in-out infinite;
    }
    @keyframes flotar {
      0%,
      100% {
        transform: translateY(0) rotate(-8deg);
      }
      50% {
        transform: translateY(-14px) rotate(4deg);
      }
    }
    .tarjeta-flotante {
      position: absolute;
      left: -6%;
      bottom: 8%;
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 14px 18px;
      border-radius: 14px;
      background: var(--cd-color-surface);
      color: var(--cd-color-text);
      box-shadow: var(--cd-shadow-3);
    }
    .tarjeta-flotante__icono {
      display: grid;
      place-items: center;
      width: 38px;
      height: 38px;
      border-radius: 10px;
      background: var(--cd-color-success-soft);
      color: var(--cd-color-success);
      font-weight: 900;
    }
    .tarjeta-flotante small,
    .tarjeta-flotante strong,
    .tarjeta-flotante span {
      display: block;
    }
    .tarjeta-flotante small {
      color: var(--cd-color-text-muted);
      font-size: 9px;
      font-weight: 800;
      letter-spacing: 0.14em;
    }
    .tarjeta-flotante strong {
      margin: 3px 0;
      font-size: 14px;
    }
    .tarjeta-flotante span {
      color: var(--cd-color-text-muted);
      font-size: 11px;
    }
    .sello-flotante {
      position: absolute;
      top: 0;
      right: 0;
      color: var(--cd-color-accent);
      font-family: var(--cd-font-display);
      font-size: 20px;
      font-weight: 800;
      line-height: 1.05;
      letter-spacing: -0.03em;
      transform: rotate(7deg);
      text-align: right;
    }
    .sello-flotante strong {
      font-size: 25px;
    }
    .busqueda {
      position: relative;
      z-index: 2;
      margin-top: -78px;
    }
    .tarjeta-busqueda {
      padding: 22px 24px 24px;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-lg);
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-2);
    }
    .tarjeta-busqueda__cabecera {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      margin-bottom: 16px;
    }
    .tarjeta-busqueda__cabecera strong {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      font-size: 12px;
      letter-spacing: 0.1em;
      text-transform: uppercase;
    }
    .tarjeta-busqueda__grid {
      display: grid;
      grid-template-columns: 1fr 1fr 1fr auto;
      gap: 12px;
      align-items: end;
    }
    .tarjeta-busqueda .cd-field {
      margin: 0;
    }
    .buscar-btn {
      min-height: 44px;
      white-space: nowrap;
    }
    .seccion {
      padding-block: clamp(56px, 7vw, 90px) 0;
    }
    .seccion__cabecera {
      display: flex;
      align-items: end;
      justify-content: space-between;
      gap: 24px;
      margin-bottom: 30px;
    }
    .seccion h2 {
      margin: 0;
      font-size: clamp(30px, 3.4vw, 46px);
    }
    .seccion h2 em,
    .por-que h2 em {
      color: var(--cd-color-teal, #167a70);
    }
    .seccion__bajada {
      max-width: 42ch;
      font-size: 14px;
    }
    .deportes {
      display: grid;
      grid-template-columns: repeat(6, 1fr);
      gap: 12px;
    }
    .deporte {
      position: relative;
      display: grid;
      gap: 4px;
      min-height: 142px;
      padding: 16px;
      border: 1px solid var(--cd-color-border);
      border-radius: 14px;
      background: var(--cd-color-surface-alt);
      color: var(--cd-color-text);
      text-align: left;
      transition:
        transform var(--cd-duration-base),
        background var(--cd-duration-base),
        border-color var(--cd-duration-base);
    }
    .deporte:hover {
      transform: translateY(-4px);
      border-color: var(--cd-color-accent-soft);
      background: var(--cd-color-accent-soft);
    }
    .deporte__numero {
      color: var(--cd-color-text-muted);
      font-size: 10px;
      font-weight: 700;
      letter-spacing: 0.12em;
    }
    .deporte__emoji {
      font-size: 34px;
      line-height: 1.2;
    }
    .deporte strong {
      font-family: var(--cd-font-display);
      font-size: 17px;
      letter-spacing: -0.02em;
    }
    .deporte__flecha {
      position: absolute;
      right: 14px;
      bottom: 14px;
      display: grid;
      place-items: center;
      width: 26px;
      height: 26px;
      border-radius: 50%;
      background: var(--cd-color-surface-alt);
      font-size: 13px;
    }
    .deporte--verde,
    .deporte--azul,
    .deporte--naranja,
    .deporte--rosa {
      background: var(--cd-color-surface-alt);
    }
    .seccion--tinte {
      margin-top: 70px;
      padding-block: 64px;
      background: var(--cd-color-surface-alt);
    }
    .destacadas {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 22px;
    }
    .tarjeta-espacio {
      display: flex;
      flex-direction: column;
      overflow: hidden;
      border: 1px solid var(--cd-color-border);
      border-radius: 16px;
      background: var(--cd-color-surface);
      color: var(--cd-color-text);
      text-decoration: none;
      transition:
        transform var(--cd-duration-base),
        box-shadow var(--cd-duration-base);
    }
    .tarjeta-espacio:hover {
      transform: translateY(-5px);
      box-shadow: var(--cd-shadow-2);
      color: var(--cd-color-text);
    }
    .tarjeta-espacio--cargando {
      min-height: 330px;
      background: linear-gradient(90deg, var(--cd-color-surface-alt), var(--cd-color-surface), var(--cd-color-surface-alt));
      background-size: 200% 100%;
      animation: brillo 1.4s linear infinite;
    }
    @keyframes brillo {
      to {
        background-position: -200% 0;
      }
    }
    .tarjeta-espacio__media {
      position: relative;
      height: 190px;
      overflow: hidden;
      background:
        repeating-linear-gradient(120deg, rgba(255, 255, 255, 0.1) 0 2px, transparent 2px 16px),
        linear-gradient(145deg, #1b6b54, #104737);
    }
    .tarjeta-espacio__media--azul {
      background: linear-gradient(145deg, #2a5d9c, #17365e);
    }
    .tarjeta-espacio__media--naranja {
      background: linear-gradient(145deg, #b66a2e, #713d17);
    }
    .tarjeta-espacio__media--rosa {
      background: linear-gradient(145deg, #9b5064, #5f2b3b);
    }
    .tarjeta-espacio__media img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .tarjeta-espacio__media-link {
      display: block;
      height: 100%;
    }
    .tarjeta-espacio__deporte {
      position: absolute;
      left: 12px;
      bottom: 12px;
      padding: 5px 9px;
      border-radius: 7px;
      background: var(--cd-color-surface);
      color: var(--cd-color-text);
      font-size: 10px;
      font-weight: 800;
    }
    .tarjeta-espacio__corazon {
      position: absolute;
      top: 12px;
      right: 12px;
      display: grid;
      place-items: center;
      width: 32px;
      height: 32px;
      border: 0;
      border-radius: 8px;
      background: color-mix(in srgb, var(--cd-color-surface) 90%, transparent);
      color: var(--cd-color-danger);
      font-size: 17px;
      cursor: pointer;
    }
    .tarjeta-espacio__corazon.activo {
      background: var(--cd-color-danger);
      color: #fff;
    }
    .tarjeta-espacio__cuerpo {
      display: grid;
      gap: 7px;
      padding: 16px 18px 18px;
      color: inherit;
      text-decoration: none;
    }
    .tarjeta-espacio__meta {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      color: var(--cd-color-text-muted);
      font-size: 11px;
    }
    .tarjeta-espacio__meta span {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
    .rating {
      color: var(--cd-color-warning);
      font-weight: 800;
    }
    .tarjeta-espacio h3 {
      margin: 0;
      font-size: 22px;
      letter-spacing: -0.03em;
    }
    .tarjeta-espacio__pie {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding-top: 10px;
      border-top: 1px solid var(--cd-color-border);
      font-size: 12px;
      color: var(--cd-color-text-muted);
    }
    .tarjeta-espacio__pie strong {
      color: var(--cd-color-text);
      font-size: 19px;
    }
    .tarjeta-espacio__pie small {
      font-size: 10px;
    }
    .flecha {
      display: grid;
      place-items: center;
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: var(--cd-color-accent);
      color: var(--cd-color-primary);
      font-size: 15px;
    }
    .por-que {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: clamp(30px, 6vw, 80px);
      align-items: center;
      padding-block: clamp(64px, 8vw, 110px);
    }
    .por-que__visual {
      position: relative;
      height: 430px;
      border-radius: 20px;
      overflow: hidden;
      background:
        radial-gradient(circle at 70% 20%, color-mix(in srgb, var(--cd-color-accent) 55%, transparent), transparent 45%),
        linear-gradient(145deg, #183b31, #0c211b);
      color: var(--cd-color-inverse-text);
    }
    .por-que__big {
      position: absolute;
      top: 30px;
      left: 30px;
      font-family: var(--cd-font-display);
      font-size: clamp(80px, 9vw, 120px);
      font-weight: 900;
      line-height: 0.9;
      letter-spacing: -0.06em;
    }
    .por-que__big span {
      color: var(--cd-color-accent);
    }
    .por-que__strip {
      position: absolute;
      left: 30px;
      right: 30px;
      bottom: 30px;
      padding: 14px 18px;
      border: 1px solid color-mix(in srgb, var(--cd-color-inverse-text) 24%, transparent);
      border-radius: 10px;
      font-size: 10px;
      font-weight: 800;
      letter-spacing: 0.14em;
      text-align: center;
    }
    .por-que h2 {
      margin: 0 0 26px;
      font-size: clamp(32px, 3.6vw, 48px);
    }
    .pasos {
      display: grid;
      gap: 0;
      margin: 0 0 28px;
      padding: 0;
      list-style: none;
    }
    .pasos li {
      display: flex;
      gap: 18px;
      padding: 16px 0;
      border-bottom: 1px solid var(--cd-color-border);
    }
    .pasos li > span {
      color: var(--cd-color-accent-strong);
      font-size: 11px;
      font-weight: 800;
      letter-spacing: 0.12em;
    }
    .pasos h3 {
      margin: 0 0 4px;
      font-size: 17px;
    }
    .pasos p {
      margin: 0;
      color: var(--cd-color-text-muted);
      font-size: 13px;
    }
    .cta-final {
      padding-block: clamp(56px, 7vw, 86px);
      background: var(--cd-color-inverse-bg);
      color: var(--cd-color-inverse-text);
    }
    .cta-final__inner {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 32px;
      flex-wrap: wrap;
    }
    .cta-final h2 {
      margin: 0 0 10px;
      font-size: clamp(30px, 3.6vw, 46px);
    }
    .cta-final p:last-child {
      max-width: 46ch;
      margin: 0;
      color: var(--cd-color-inverse-muted);
    }
    @media (max-width: 1024px) {
      .hero__grid {
        grid-template-columns: 1fr;
      }
      .hero__visual {
        min-height: 330px;
      }
      .deportes {
        grid-template-columns: repeat(3, 1fr);
      }
      .destacadas {
        grid-template-columns: repeat(2, 1fr);
      }
      .tarjeta-busqueda__grid {
        grid-template-columns: 1fr 1fr;
      }
      .buscar-btn {
        grid-column: 1 / -1;
      }
    }
    @media (max-width: 720px) {
      .hero {
        padding-bottom: 110px;
      }
      .hero h1 {
        font-size: clamp(42px, 12vw, 58px);
      }
      .tarjeta-flotante {
        left: 0;
      }
      .sello-flotante {
        display: none;
      }
      .hero__visual {
        min-height: 290px;
      }
      .cancha-art {
        height: 280px;
      }
      .busqueda {
        margin-top: -60px;
      }
      .tarjeta-busqueda__cabecera {
        display: block;
      }
      .tarjeta-busqueda__cabecera .cd-small {
        display: block;
        margin-top: 4px;
      }
      .tarjeta-busqueda__grid {
        grid-template-columns: 1fr;
      }
      .seccion__cabecera {
        align-items: start;
        flex-direction: column;
      }
      .deportes {
        grid-template-columns: repeat(2, 1fr);
      }
      .destacadas {
        grid-template-columns: 1fr;
      }
      .por-que {
        grid-template-columns: 1fr;
      }
      .por-que__visual {
        height: 320px;
      }
    }
  `,
})
export class HomeComponent implements OnInit {
  private readonly venues = inject(VenuesApi);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  protected readonly auth = inject(AuthStore);
  protected readonly favoritos = inject(FavoritosService);

  protected readonly distritos = signal<Distrito[]>([]);
  protected readonly deportes = signal<Item[]>([]);
  protected readonly destacadas = signal<Cancha[]>([]);
  protected readonly cargandoDestacadas = signal(true);
  protected readonly hoy = new Date().toISOString().slice(0, 10);

  protected readonly form = this.fb.nonNullable.group({
    idDeporte: [null as number | null],
    idDistrito: [null as number | null],
    fecha: [new Date().toISOString().slice(0, 10)],
  });

  ngOnInit(): void {
    void this.cargarDatos();
  }

  protected deportesTiles(): DeporteTile[] {
    return this.deportes().map((deporte) => ({
      id: deporte.id,
      nombre: deporte.nombre,
      emoji: EMOJIS[deporte.nombre.toLowerCase()] ?? '🏟️',
      clase: this.claseDeporteNombre(deporte.nombre),
    }));
  }

  protected buscar(): void {
    const { idDeporte, idDistrito, fecha } = this.form.getRawValue();
    void this.router.navigate(['/canchas'], {
      queryParams: {
        idDeporte: idDeporte ?? undefined,
        idDistrito: idDistrito ?? undefined,
        fecha: fecha || undefined,
      },
    });
  }

  protected buscarDeporte(idDeporte: number): void {
    void this.router.navigate(['/canchas'], { queryParams: { idDeporte } });
  }

  protected claseDeporte(cancha: Cancha): string {
    return this.claseDeporteNombre(cancha.deportes[0] ?? '');
  }

  protected async alternarFavorito(cancha: Cancha, event: Event): Promise<void> {
    event.preventDefault();
    event.stopPropagation();
    if (!this.auth.isAuthenticated()) {
      void this.router.navigate(['/auth/login'], { queryParams: { redirect: this.router.url } });
      return;
    }
    await this.favoritos.alternar(cancha.id);
  }

  protected ocultarImagen(event: Event): void {
    (event.target as HTMLImageElement).style.display = 'none';
  }

  private claseDeporteNombre(nombre: string): string {
    const normalizado = nombre.toLowerCase();
    if (normalizado.includes('pádel') || normalizado.includes('padel') || normalizado.includes('tenis')) {
      return 'azul';
    }
    if (normalizado.includes('básquet') || normalizado.includes('basquet')) {
      return 'naranja';
    }
    if (normalizado.includes('vóley') || normalizado.includes('voley')) {
      return 'rosa';
    }
    return 'verde';
  }

  private async cargarDatos(): Promise<void> {
    try {
      const [distritos, deportes] = await Promise.all([
        firstValueFrom(this.venues.distritos()),
        firstValueFrom(this.venues.deportes()),
      ]);
      this.distritos.set(distritos);
      this.deportes.set(deportes);
    } catch {
      // El catalogo sigue disponible aunque falle la carga de filtros.
    }

    try {
      const pagina = await firstValueFrom(this.venues.canchasPaginadas({ limite: 12 }));
      const ordenadas = [...pagina.items].sort(
        (a, b) =>
          b.promedioCalificacion - a.promedioCalificacion ||
          b.totalCalificaciones - a.totalCalificaciones ||
          a.precioBase - b.precioBase,
      );
      this.destacadas.set(ordenadas.slice(0, 3));
    } catch {
      this.destacadas.set([]);
    } finally {
      this.cargandoDestacadas.set(false);
    }
  }
}
