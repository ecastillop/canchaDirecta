import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, HostListener, OnInit, computed, inject, input, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ComparacionService } from '../../core/api/comparacion.service';
import { EngagementApi, CalificacionesCancha } from '../../core/api/engagement.api';
import { VenuesApi } from '../../core/api/venues.api';
import { CanchaDetalle, FranjaDisponible, HorarioCancha } from '../../core/api/venues.models';
import { APP_CONFIG } from '../../core/config/app-config';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { CdIconComponent } from '../../shared/ui/icon/cd-icon.component';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import {
  DIAS,
  DIAS_CORTOS,
  MESES_LARGOS,
  etiquetaDeSemana,
  horaDeMinutos,
  horaLima,
  hoyLima,
  lunesDe,
  minutosDe,
  partesDeFecha,
  sumarDias,
} from '../../shared/util/fecha-lima';

const FOTO_FALLBACK =
  'data:image/svg+xml;charset=UTF-8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 600"><rect width="800" height="600" fill="#e8edf4"/><path d="M120 420l160-180 120 135 90-105 190 220z" fill="#b7c3d3"/><circle cx="600" cy="150" r="55" fill="#b7c3d3"/><text x="400" y="520" text-anchor="middle" font-family="Segoe UI,Arial" font-size="28" fill="#5b6b80">Foto no disponible</text></svg>',
  );

interface DiaSemana {
  fecha: string;
  corta: string;
  larga: string;
  numero: number;
  esHoy: boolean;
  esPasado: boolean;
  /** La cancha tiene agenda configurada y ese dia de la semana no esta en ella. */
  cerrado: boolean;
  franjas: FranjaDisponible[];
}

@Component({
  selector: 'app-cancha-detalle',
  imports: [
    RouterLink,
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    CdButtonDirective,
    CdIconComponent,
    CdInputDirective,
    CdSkeletonComponent,
    CdEmptyStateComponent,
    CdErrorStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cd-container cd-page">
      @if (cargando()) {
        <div class="cd-stack">
          <cd-skeleton height="220px" radius="16px" />
          <cd-skeleton width="50%" height="28px" />
          <cd-skeleton width="80%" height="16px" />
        </div>
      } @else if (error()) {
        <cd-error-state [message]="error()!" (retry)="cargar()" />
      } @else if (cancha(); as c) {
        @if (desdePanel) {
          <div class="preview-bar" role="status">
            <div>
              <strong>Vista previa</strong>
              <span class="cd-small">Así verán esta cancha tus clientes.</span>
            </div>
            <a
              [routerLink]="['/panel']"
              [queryParams]="{ editarCancha: c.id }"
              class="cd-btn cd-btn--primary cd-btn--sm"
            >
              Volver a configurar
            </a>
          </div>
        }

        <nav class="migas cd-small cd-muted" aria-label="Ruta de navegación">
          <a routerLink="/canchas">Canchas</a> <span aria-hidden="true">/</span> <span>{{ c.nombre }}</span>
        </nav>

        <div class="cabecera">
          <div class="cabecera__texto">
            <div class="cd-row cabecera__meta">
              <span class="cd-badge cd-badge--accent">{{ c.deportes[0]?.nombre ?? 'Cancha' }}</span>
              <span class="cd-badge" [class.cd-badge--success]="c.estado === 'ACTIVA'">
                {{ c.estado === 'ACTIVA' ? 'Disponible' : c.estado }}
              </span>
              <span class="cd-badge">Para {{ c.capacidad }} personas</span>
            </div>
            <h1 class="cd-page__title">{{ c.nombre }}</h1>
            <p class="ubicacion">
              <cd-icon name="pin" [size]="15" />
              {{ c.establecimiento }} · {{ c.distrito.nombre }}, {{ c.distrito.provincia }}
            </p>
            <p class="cd-small cd-muted">{{ c.direccion }}@if (c.referencia) { · {{ c.referencia }} }</p>
          </div>
          <div class="puntuacion" [attr.aria-label]="'Reputación ' + c.reputacion.promedio + ' de 5'">
            <span class="puntuacion__valor"><cd-icon name="star" [size]="17" /> {{ c.reputacion.promedio | number: '1.1-1' }}</span>
            <span class="cd-xs cd-muted">
              {{ c.reputacion.total ? c.reputacion.total + ' calificaciones' : 'Nuevo en CanchaDirecta' }}
            </span>
          </div>
        </div>

        <div class="galeria">
          @if (c.fotos.length > 0) {
            <button
              class="galeria__principal"
              type="button"
              (click)="abrirVisor()"
              [attr.aria-label]="'Ampliar foto de ' + c.nombre"
            >
              @if (fotoActual(); as foto) {
                <img [src]="foto.url" [alt]="'Foto de ' + c.nombre" (error)="usarFallback($event)" />
              }
              <span class="galeria__contador">{{ indiceFoto() + 1 }} / {{ c.fotos.length }}</span>
            </button>
            <div class="galeria__miniaturas" aria-label="Miniaturas de fotos">
              @for (foto of c.fotos; track foto.id; let i = $index) {
                <button
                  type="button"
                  class="miniatura"
                  [class.miniatura--activa]="i === indiceFoto()"
                  (click)="seleccionarFoto(i)"
                  [attr.aria-label]="'Ver foto ' + (i + 1) + ' de ' + c.fotos.length"
                  [attr.aria-current]="i === indiceFoto() ? 'true' : null"
                >
                  <img
                    [src]="foto.url"
                    [alt]="'Miniatura ' + (i + 1) + ' de ' + c.nombre"
                    loading="lazy"
                    (error)="usarFallback($event)"
                  />
                </button>
              }
            </div>
            @if (c.fotos.length > 1) {
              <button class="galeria__flecha galeria__flecha--prev" type="button" aria-label="Foto anterior" (click)="anteriorFoto($event)">
                ‹
              </button>
              <button class="galeria__flecha galeria__flecha--next" type="button" aria-label="Foto siguiente" (click)="siguienteFoto($event)">
                ›
              </button>
            }
          } @else {
            <div class="galeria__vacia" role="img" [attr.aria-label]="'Sin fotos de ' + c.nombre">
              <svg viewBox="0 0 120 80" aria-hidden="true">
                <rect x="4" y="4" width="112" height="72" rx="6" />
                <path d="M60 4v72" />
                <circle cx="60" cy="40" r="14" />
                <path d="M4 22h18v36H4M116 22h-18v36h18" />
              </svg>
              <strong>Fotos próximamente</strong>
              <span class="cd-small">El establecimiento aún no cargó imágenes de esta cancha.</span>
            </div>
          }
        </div>

        @if (visorAbierto() && fotoActual(); as foto) {
          <div class="visor" role="dialog" aria-modal="true" aria-label="Fotos de la cancha" (click)="cerrarVisor()">
            <button class="visor__cerrar" type="button" aria-label="Cerrar fotos" (click)="cerrarVisor()">✕</button>
            @if (c.fotos.length > 1) {
              <button class="visor__nav visor__nav--prev" type="button" aria-label="Foto anterior" (click)="anteriorFoto($event)">
                ‹
              </button>
            }
            <img [src]="foto.url" [alt]="'Foto de ' + c.nombre" (click)="$event.stopPropagation()" (error)="usarFallback($event)" />
            @if (c.fotos.length > 1) {
              <button class="visor__nav visor__nav--next" type="button" aria-label="Foto siguiente" (click)="siguienteFoto($event)">
                ›
              </button>
            }
            <span class="visor__contador">{{ indiceFoto() + 1 }} / {{ c.fotos.length }}</span>
          </div>
        }

        <div class="columnas">
          <article class="contenido">
            <section class="bloque">
              <h2 class="seccion">Sobre la cancha</h2>
              @if (c.descripcion) {
                <p class="descripcion">{{ c.descripcion }}</p>
              } @else {
                <p class="cd-small cd-muted">
                  Cancha de {{ c.deportes[0]?.nombre ?? 'deporte' }} en {{ c.distrito.nombre }}, con capacidad
                  para {{ c.capacidad }} personas.
                </p>
              }
              <dl class="hechos">
                <div>
                  <dt>Establecimiento</dt>
                  <dd>
                    <a class="enlace-espacio" [routerLink]="['/establecimientos', c.idEstablecimiento]">
                      {{ c.establecimiento }} <span aria-hidden="true">→</span>
                    </a>
                  </dd>
                </div>
                <div>
                  <dt>Distrito</dt>
                  <dd>{{ c.distrito.nombre }}</dd>
                </div>
                <div>
                  <dt>Deportes</dt>
                  <dd>{{ c.deportes.length ? c.deportes[0].nombre : 'No especificado' }}</dd>
                </div>
                <div>
                  <dt>Capacidad</dt>
                  <dd>{{ c.capacidad }} personas</dd>
                </div>
              </dl>
            </section>

            <section class="bloque">
              <h2 class="seccion">Servicios incluidos</h2>
              @if (c.caracteristicas.length) {
                <ul class="caracteristicas">
                  @for (car of c.caracteristicas; track car.nombre) {
                    <li><span class="check" aria-hidden="true"><cd-icon name="check" [size]="12" /></span><span><strong>{{ car.nombre }}</strong>@if (car.valor) { · {{ car.valor }}}</span></li>
                  }
                </ul>
              } @else {
                <p class="cd-small cd-muted">El propietario todavía no registró servicios para esta cancha.</p>
              }
            </section>

            <section class="bloque calendario" aria-labelledby="calendario-titulo">
              <div class="calendario__cabecera">
                <div>
                  <h2 id="calendario-titulo" class="seccion">Disponibilidad semanal</h2>
                  <p class="cd-small cd-muted">
                    Estas son las franjas del horario configurado: elige una libre para reservar. Las
                    franjas ocupadas o bloqueadas no tienen cupo.
                  </p>
                </div>
                <div class="leyenda cd-xs cd-muted">
                  <span><i class="punto punto--libre" aria-hidden="true"></i> Libre</span>
                  <span><i class="punto punto--ocupado" aria-hidden="true"></i> Sin cupo</span>
                  <span><i class="punto punto--cerrado" aria-hidden="true"></i> Cerrado</span>
                </div>
              </div>
              <p class="cd-xs cd-muted solo-movil">Desliza horizontalmente para ver toda la semana.</p>
    
              @if (cargandoSemana()) {
                <cd-skeleton height="220px" radius="12px" />
              } @else if (errorSemana()) {
                <cd-error-state [message]="errorSemana()!" (retry)="recargarSemana()" />
              } @else if (horasSemana().length === 0) {
                <cd-empty-state
                  title="Sin disponibilidad esta semana"
                  message="Prueba con otra semana o cambia la duración de la reserva."
                >
                  <button cdButton variant="secondary" size="sm" type="button" (click)="semanaSiguiente()">
                    Ver semana siguiente
                  </button>
                </cd-empty-state>
              } @else {
                <div class="calendario__scroll">
                  <table class="calendario__tabla">
                    <caption class="cd-visually-hidden">
                      Disponibilidad de la cancha por hora y día de la semana
                    </caption>
                    <thead>
                      <tr>
                        <th scope="col" class="col-hora">Hora</th>
                        @for (dia of diasSemana(); track dia.fecha) {
                          <th scope="col" [class.col-hoy]="dia.esHoy" [class.col-activa]="fechaSeleccionada() === dia.fecha">
                            <button type="button" class="cabecera-dia" (click)="seleccionarDia(dia)" [disabled]="dia.esPasado">
                              <span>{{ dia.corta }}</span>
                              <strong>{{ dia.numero }}</strong>
                              <small>{{ dia.cerrado ? 'Cerrado' : textoCupos(dia.franjas.length) }}</small>
                            </button>
                          </th>
                        }
                      </tr>
                    </thead>
                    <tbody>
                      @for (hora of horasSemana(); track hora) {
                        <tr>
                          <th scope="row" class="col-hora">{{ hora }}</th>
                          @for (dia of diasSemana(); track dia.fecha) {
                            @if (franjaDe(dia, hora); as franja) {
                              <td>
                                <button
                                  type="button"
                                  class="celda celda--libre"
                                  [class.celda--activa]="seleccionada()?.inicio === franja.inicio"
                                  [attr.aria-label]="dia.larga + ' a las ' + hora + ', ' + (franja.precio | currency: 'PEN' : 'symbol-narrow' : '1.2-2')"
                                  (click)="seleccionarCelda(dia, franja)"
                                >
                                  {{ franja.precio | currency: 'PEN' : 'symbol-narrow' : '1.0-0' }}
                                </button>
                              </td>
                            } @else {
                              <td>
                                @if (dia.cerrado) {
                                  <span class="celda celda--cerrada" aria-label="Cerrado">—</span>
                                } @else {
                                  <span class="celda celda--ocupada" aria-label="Sin cupo">—</span>
                                }
                              </td>
                            }
                          }
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              }
            </section>

            <section class="bloque">
              <h2 class="seccion">Opiniones recientes</h2>
              @if (calificaciones()?.calificaciones?.length) {
                <ul class="opiniones">
                  @for (calificacion of calificaciones()!.calificaciones.slice(0, 5); track calificacion.id) {
                    <li>
                      <div class="cd-row cd-row--between">
                        <span class="estrellas-texto" [attr.aria-label]="calificacion.puntaje + ' de 5'">
                          {{ '★'.repeat(calificacion.puntaje) }}{{ '☆'.repeat(5 - calificacion.puntaje) }}
                        </span>
                        <span class="cd-xs cd-muted">{{ calificacion.fechaRegistro | date: 'd MMM yyyy' }}</span>
                      </div>
                      @if (calificacion.comentario) {
                        <p class="cd-small">{{ calificacion.comentario }}</p>
                      }
                    </li>
                  }
                </ul>
              } @else {
                <p class="cd-small cd-muted">Aún no hay opiniones de esta cancha.</p>
              }
            </section>
          </article>

          <aside class="reserva" aria-labelledby="reserva-titulo">
            <div class="reserva__cabecera">
              <div>
                <p class="cd-xs reserva__eyebrow">Precio por hora</p>
                <p class="precio">
                  {{ c.precioBase | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}
                  <span class="cd-small cd-muted">/ hora</span>
                </p>
              </div>
              <span class="cd-badge cd-badge--success">Reserva online</span>
            </div>
            <h2 id="reserva-titulo" class="reserva__titulo">Reserva tu horario</h2>

            <div class="semana" aria-live="polite">
              <div class="semana__nav">
                <button
                  type="button"
                  class="semana__flecha"
                  aria-label="Semana anterior"
                  [disabled]="esSemanaActual()"
                  (click)="semanaAnterior()"
                >
                  ‹
                </button>
                <span class="semana__titulo">{{ etiquetaSemana() }}</span>
                <button
                  type="button"
                  class="semana__flecha"
                  aria-label="Semana siguiente"
                  [disabled]="esUltimaSemana()"
                  (click)="semanaSiguiente()"
                >
                  ›
                </button>
              </div>

              <div class="semana__dias" role="tablist" aria-label="Días de la semana">
                @for (dia of diasSemana(); track dia.fecha) {
                  <button
                    type="button"
                    role="tab"
                    class="dia"
                    [class.dia--activo]="fechaSeleccionada() === dia.fecha"
                    [class.dia--hoy]="dia.esHoy"
                    [class.dia--pasado]="dia.esPasado"
                    [attr.aria-selected]="fechaSeleccionada() === dia.fecha"
                    [attr.aria-label]="textoDia(dia)"
                    [disabled]="dia.esPasado"
                    (click)="seleccionarDia(dia)"
                  >
                    <span class="dia__nombre">{{ dia.corta }}</span>
                    <strong class="dia__numero">{{ dia.numero }}</strong>
                    <span class="dia__cupos" aria-hidden="true">{{ dia.franjas.length }}</span>
                  </button>
                }
              </div>

              <div class="semana__acciones">
                <label class="cd-field mes">
                  <span class="cd-field__label">Ir a mes</span>
                  <input cdInput type="month" [value]="mesSeleccionado()" [min]="mesActual" [max]="mesMax" (change)="cambiarMes($event)" />
                </label>
                <button cdButton variant="ghost" size="sm" type="button" (click)="irAHoy()">Hoy</button>
              </div>
            </div>

            <label class="cd-field">
              <span class="cd-field__label">Duración</span>
              <select cdInput [value]="duracion()" (change)="cambiarDuracion($event)">
                <option value="60">60 minutos</option>
                <option value="90">90 minutos</option>
                <option value="120">120 minutos</option>
              </select>
            </label>

            <div class="franjas" aria-live="polite">
              @if (cargandoSemana()) {
                <cd-skeleton height="40px" />
                <cd-skeleton height="40px" />
              } @else if (errorSemana()) {
                <p class="cd-small cd-field__error">{{ errorSemana() }}</p>
                <button cdButton variant="secondary" size="sm" type="button" (click)="recargarSemana()">Reintentar</button>
              } @else if (diaSeleccionado(); as dia) {
                @if (dia.franjas.length) {
                  <p class="cd-small cd-muted franjas__titulo">
                    Horarios del {{ dia.larga }} · {{ textoCupos(dia.franjas.length) }}
                  </p>
                  @for (franja of dia.franjas; track franja.inicio) {
                    <button
                      type="button"
                      class="franja"
                      [class.franja--activa]="seleccionada()?.inicio === franja.inicio"
                      [attr.aria-pressed]="seleccionada()?.inicio === franja.inicio"
                      (click)="seleccionada.set(franja)"
                    >
                      <span>{{ hora(franja.inicio) }} - {{ hora(franja.fin) }}</span>
                      <strong>{{ franja.precio | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</strong>
                    </button>
                  }
                } @else {
                  <p class="cd-small cd-muted">No hay franjas libres ese día. Elige otro del calendario.</p>
                }
              } @else {
                <p class="cd-small cd-muted">Elige un día para ver sus horarios.</p>
              }
            </div>

            @if (seleccionada(); as franja) {
              <p class="cd-small resumen">
                {{ diaSeleccionado()?.larga }} · {{ hora(franja.inicio) }} a
                {{ hora(franja.fin) }} ·
                <strong>{{ franja.precio | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</strong>
              </p>
            }

            <button
              cdButton
              variant="accent"
              [block]="true"
              [disabled]="!seleccionada()"
              (click)="reservar()"
            >
              Reservar
            </button>
            <button cdButton variant="ghost" size="sm" [block]="true" (click)="alternarComparacion()">
              {{ comparacion.tiene(c.id) ? 'Quitar de la comparación' : 'Agregar a comparar' }}
            </button>
            <p class="cd-xs cd-muted nota-pago">
              Pago simulado. Puedes cancelar sin costo hasta 2 horas antes del inicio.
            </p>
          </aside>
        </div>


      }
    </div>
  `,
  styles: `
    .migas {
      margin-bottom: var(--cd-space-3);
    }
    .cabecera {
      display: flex;
      justify-content: space-between;
      align-items: start;
      gap: var(--cd-space-3);
      flex-wrap: wrap;
      margin-bottom: var(--cd-space-4);
    }
    .puntuacion {
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }
    .estrella {
      color: var(--cd-color-warning);
      font-size: 20px;
    }
    .galeria {
      display: grid;
      gap: var(--cd-space-2);
      margin-bottom: var(--cd-space-5);
    }
    .galeria__principal {
      width: 100%;
      height: clamp(200px, 38vw, 380px);
      object-fit: cover;
      border-radius: var(--cd-radius-lg);
      background: var(--cd-color-surface-alt);
    }
    .galeria__miniaturas {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: var(--cd-space-2);
    }
    .galeria__miniaturas img {
      width: 100%;
      height: 80px;
      object-fit: cover;
      border-radius: var(--cd-radius-sm);
    }
    .galeria__vacia {
      display: grid;
      place-items: center;
      height: 220px;
      border-radius: var(--cd-radius-lg);
      background: var(--cd-color-surface-alt);
      color: var(--cd-color-text-muted);
      font-weight: 800;
      letter-spacing: 0.04em;
    }
    .columnas {
      display: grid;
      gap: var(--cd-space-5);
    }
    .seccion {
      font-size: var(--cd-text-base);
      margin-bottom: var(--cd-space-2);
    }
    .caracteristicas {
      display: grid;
      gap: 6px;
      padding-left: var(--cd-space-4);
      font-size: var(--cd-text-sm);
    }
    .opiniones {
      list-style: none;
      padding: 0;
      display: grid;
      gap: var(--cd-space-3);
    }
    .opiniones li {
      display: grid;
      gap: 4px;
      padding-bottom: var(--cd-space-2);
      border-bottom: 1px solid var(--cd-color-border);
    }
    .estrellas-texto {
      color: var(--cd-color-warning);
    }
    .reserva {
      display: grid;
      gap: var(--cd-space-3);
      align-content: start;
      padding: var(--cd-space-4);
      background: var(--cd-color-surface);
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-md);
      box-shadow: var(--cd-shadow-1);
      height: fit-content;
    }
    .precio {
      font-size: var(--cd-text-xl);
      font-weight: 800;
      color: var(--cd-color-primary);
    }
    .franjas {
      display: grid;
      gap: var(--cd-space-2);
      max-height: 300px;
      overflow-y: auto;
      padding-right: 4px;
    }
    .franja {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--cd-space-2);
      padding: 10px 12px;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-surface);
      cursor: pointer;
      font-size: var(--cd-text-sm);
    }
    .franja:hover {
      border-color: var(--cd-color-accent);
    }
    .franja--activa {
      border-color: var(--cd-color-accent-strong);
      background: color-mix(in srgb, var(--cd-color-accent) 14%, transparent);
    }
    .resumen {
      padding: var(--cd-space-2);
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-surface-alt);
    }
    .semana {
      display: grid;
      gap: var(--cd-space-2);
      padding: var(--cd-space-3);
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-md);
      background: var(--cd-color-surface-alt);
    }
    .semana__nav {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--cd-space-2);
    }
    .semana__titulo {
      font-size: var(--cd-text-sm);
      font-weight: 700;
      text-align: center;
    }
    .semana__flecha {
      width: 34px;
      height: 34px;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-surface);
      cursor: pointer;
      font-size: 20px;
      line-height: 1;
      color: var(--cd-color-text);
    }
    .semana__flecha:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }
    .semana__dias {
      display: grid;
      grid-template-columns: repeat(7, minmax(0, 1fr));
      gap: 4px;
    }
    .dia {
      display: grid;
      justify-items: center;
      gap: 1px;
      padding: 6px 2px;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-surface);
      cursor: pointer;
      color: var(--cd-color-text);
      min-width: 0;
    }
    .dia:hover:not(:disabled) {
      border-color: var(--cd-color-accent);
    }
    .dia__nombre {
      font-size: 10px;
      text-transform: uppercase;
      letter-spacing: 0.03em;
      color: var(--cd-color-text-muted);
    }
    .dia__numero {
      font-size: var(--cd-text-base);
      line-height: 1.1;
    }
    .dia__cupos {
      font-size: 10px;
      color: var(--cd-color-text-muted);
    }
    .dia--hoy {
      border-color: var(--cd-color-accent);
      box-shadow: inset 0 0 0 1px var(--cd-color-accent);
    }
    .dia--activo {
      background: var(--cd-color-primary);
      border-color: var(--cd-color-primary);
      color: var(--cd-color-on-primary);
    }
    .dia--activo .dia__nombre,
    .dia--activo .dia__cupos {
      color: color-mix(in srgb, var(--cd-color-on-primary) 82%, transparent);
    }
    .dia--pasado {
      opacity: 0.45;
      cursor: not-allowed;
    }
    .semana__acciones {
      display: flex;
      align-items: end;
      justify-content: space-between;
      gap: var(--cd-space-2);
    }
    .mes {
      max-width: 150px;
    }
    .calendario {
      margin-top: var(--cd-space-5);
    }
    .calendario__cabecera {
      display: flex;
      justify-content: space-between;
      align-items: start;
      gap: var(--cd-space-3);
      flex-wrap: wrap;
      margin-bottom: var(--cd-space-3);
    }
    .leyenda {
      display: flex;
      gap: var(--cd-space-3);
      align-items: center;
    }
    .leyenda span {
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }
    .punto {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      display: inline-block;
    }
    .punto--libre {
      background: var(--cd-color-success);
    }
    .punto--ocupado {
      border: 1px solid var(--cd-color-border);
      background: var(--cd-color-surface);
    }
    .punto--cerrado {
      border: 1px dashed var(--cd-color-border);
      background: var(--cd-color-bg);
    }
    .calendario__scroll {
      overflow-x: auto;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-md);
    }
    .solo-movil {
      display: none;
    }
    @media (max-width: 767px) {
      .solo-movil {
        display: block;
        margin-bottom: var(--cd-space-2);
      }
    }
    .calendario__tabla {
      width: 100%;
      min-width: 720px;
      border-collapse: collapse;
      font-size: var(--cd-text-xs);
      background: var(--cd-color-surface);
    }
    .calendario__tabla th,
    .calendario__tabla td {
      border-bottom: 1px solid var(--cd-color-border);
      padding: 4px;
      text-align: center;
    }
    .calendario__tabla thead th {
      position: sticky;
      top: 0;
      background: var(--cd-color-surface-alt);
      z-index: 1;
    }
    .calendario__tabla .col-hora {
      width: 64px;
      position: sticky;
      left: 0;
      background: var(--cd-color-surface);
      font-weight: 700;
      z-index: 2;
    }
    .calendario__tabla thead .col-hora {
      background: var(--cd-color-surface-alt);
      z-index: 3;
    }
    .calendario__tabla .col-hoy {
      background: color-mix(in srgb, var(--cd-color-accent) 12%, var(--cd-color-surface-alt));
    }
    .calendario__tabla .col-activa {
      background: color-mix(in srgb, var(--cd-color-accent) 22%, var(--cd-color-surface-alt));
    }
    .cabecera-dia {
      display: grid;
      justify-items: center;
      gap: 1px;
      width: 100%;
      padding: 6px 2px;
      border: 0;
      background: transparent;
      cursor: pointer;
      color: var(--cd-color-text);
      font: inherit;
    }
    .cabecera-dia:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
    .cabecera-dia span {
      text-transform: uppercase;
      font-size: 10px;
      letter-spacing: 0.03em;
      color: var(--cd-color-text-muted);
    }
    .cabecera-dia strong {
      font-size: var(--cd-text-base);
    }
    .cabecera-dia small {
      font-size: 10px;
      color: var(--cd-color-text-muted);
    }
    .celda {
      display: grid;
      place-items: center;
      width: 100%;
      min-height: 32px;
      border-radius: 6px;
      font-size: 11px;
      font-weight: 600;
    }
    .celda--libre {
      border: 1px solid var(--cd-color-border);
      background: var(--cd-color-success-soft);
      color: var(--cd-color-success);
      cursor: pointer;
    }
    .celda--libre:hover {
      border-color: var(--cd-color-accent-strong);
    }
    .celda--activa {
      outline: 2px solid var(--cd-color-accent-strong);
      outline-offset: -2px;
    }
    .celda--ocupada {
      color: var(--cd-color-text-muted);
      background: var(--cd-color-bg);
    }
    .celda--cerrada {
      color: var(--cd-color-border);
      background: transparent;
    }
    @media (min-width: 1024px) {
      .columnas {
        grid-template-columns: 1.4fr 1fr;
        align-items: start;
      }
      .reserva {
        position: sticky;
        top: calc(var(--cd-topbar-height) + var(--cd-space-4));
      }
    }

    /* --- Distribución y jerarquía del detalle --- */
    .migas {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-bottom: var(--cd-space-4);
    }
    .cabecera {
      align-items: end;
      margin-bottom: var(--cd-space-4);
    }
    .cabecera__texto {
      display: grid;
      gap: 2px;
    }
    .cabecera__texto h1 {
      font-size: clamp(1.6rem, 2.6vw, 2.15rem);
      margin-block: 2px;
    }
    .cabecera__meta {
      margin-bottom: 2px;
    }
    .ubicacion {
      display: flex;
      align-items: center;
      gap: 5px;
      color: var(--cd-color-text);
      font-weight: 600;
      font-size: var(--cd-text-sm);
    }
    .puntuacion {
      display: grid;
      justify-items: end;
      gap: 2px;
      padding: 8px 14px;
      background: var(--cd-color-surface);
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-md);
      box-shadow: var(--cd-shadow-1);
    }
    .puntuacion__valor {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: var(--cd-text-lg);
      font-weight: 800;
      color: var(--cd-color-primary);
    }
    .estrella {
      font-size: 18px;
      color: var(--cd-color-warning);
    }
    .galeria {
      position: relative;
      display: block;
      overflow: hidden;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-lg);
      background: var(--cd-color-surface-alt);
      margin-bottom: var(--cd-space-5);
    }
    .galeria__principal {
      display: block;
      margin: 0;
      height: clamp(230px, 32vw, 390px);
    }
    .galeria__principal img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }
    .galeria__miniaturas {
      position: absolute;
      right: 12px;
      bottom: 12px;
      display: flex;
      gap: 8px;
    }
    .galeria__miniaturas img {
      width: 76px;
      height: 56px;
      object-fit: cover;
      border-radius: 8px;
      border: 2px solid rgba(255, 255, 255, 0.9);
      box-shadow: 0 2px 10px rgba(15, 23, 42, 0.28);
    }
    .galeria__badges {
      position: absolute;
      top: 12px;
      left: 12px;
      display: flex;
      gap: 6px;
      flex-wrap: wrap;
    }
    .galeria__badges .cd-badge {
      background: color-mix(in srgb, var(--cd-color-surface) 94%, transparent);
      color: var(--cd-color-primary);
      box-shadow: var(--cd-shadow-1);
    }
    .galeria__vacia {
      display: grid;
      place-content: center;
      justify-items: center;
      gap: 8px;
      min-height: clamp(220px, 28vw, 330px);
      padding: var(--cd-space-5);
      text-align: center;
      background:
        radial-gradient(circle at 20% 20%, color-mix(in srgb, var(--cd-color-accent) 16%, transparent), transparent 45%),
        radial-gradient(circle at 80% 80%, color-mix(in srgb, var(--cd-color-primary) 10%, transparent), transparent 45%),
        var(--cd-color-surface-alt);
      color: var(--cd-color-text-muted);
      font-weight: 400;
      letter-spacing: 0;
    }
    .galeria__vacia svg {
      width: 130px;
      height: 86px;
      fill: none;
      stroke: var(--cd-color-accent-strong);
      stroke-width: 2.5;
      opacity: 0.55;
    }
    .galeria__vacia strong {
      color: var(--cd-color-text);
      font-size: var(--cd-text-lg);
    }
    .galeria__vacia span {
      max-width: 38ch;
    }
    .columnas {
      gap: var(--cd-space-5);
      grid-template-columns: minmax(0, 1fr);
    }
    .contenido {
      display: grid;
      gap: var(--cd-space-3);
      min-width: 0;
    }
    .contenido > * {
      min-width: 0;
    }
    .bloque {
      background: var(--cd-color-surface);
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-md);
      box-shadow: var(--cd-shadow-1);
      padding: var(--cd-space-5);
    }
    .seccion {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: var(--cd-space-3);
      font-size: var(--cd-text-base);
    }
    .seccion::before {
      content: '';
      width: 4px;
      height: 16px;
      border-radius: 2px;
      background: var(--cd-color-accent);
      flex: 0 0 auto;
    }
    .descripcion {
      line-height: 1.65;
      margin-bottom: var(--cd-space-3);
    }
    .hechos {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 8px;
      margin: 0;
    }
    .hechos div {
      background: var(--cd-color-surface-alt);
      border-radius: var(--cd-radius-sm);
      padding: 8px 10px;
    }
    .hechos dt {
      font-size: var(--cd-text-xs);
      color: var(--cd-color-text-muted);
    }
    .hechos dd {
      margin: 2px 0 0;
      font-size: var(--cd-text-sm);
      font-weight: 700;
    }
    .enlace-espacio {
      color: var(--cd-color-primary);
      text-decoration: none;
    }
    .enlace-espacio:hover {
      text-decoration: underline;
    }
    .caracteristicas {
      grid-template-columns: repeat(2, minmax(0, 1fr));
      padding-left: 0;
      list-style: none;
    }
    .caracteristicas li {
      background: var(--cd-color-surface-alt);
      border-radius: var(--cd-radius-sm);
      padding: 8px 10px;
    }
    .caracteristicas li::before {
      content: '✓';
      margin-right: 6px;
      color: var(--cd-color-success);
      font-weight: 800;
    }
    .opiniones li {
      background: var(--cd-color-surface-alt);
      border-bottom: 0;
      border-radius: var(--cd-radius-sm);
      padding: var(--cd-space-3);
    }
    .reserva {
      padding: var(--cd-space-5);
      gap: var(--cd-space-3);
      border-radius: var(--cd-radius-lg);
      box-shadow: var(--cd-shadow-2);
    }
    .reserva__cabecera {
      display: flex;
      align-items: start;
      justify-content: space-between;
      gap: var(--cd-space-3);
    }
    .reserva__eyebrow {
      margin: 0;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      font-weight: 700;
      color: var(--cd-color-text-muted);
    }
    .reserva__titulo {
      margin: 0;
      font-size: var(--cd-text-lg);
    }
    .precio {
      margin: 2px 0 0;
      font-size: 1.65rem;
      line-height: 1.1;
    }
    .reserva .semana {
      padding: 10px;
    }
    .reserva .dia {
      padding: 5px 2px;
    }
    .franjas {
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 6px;
      max-height: 218px;
      padding-right: 2px;
    }
    .franja {
      padding: 8px 9px;
      font-size: var(--cd-text-xs);
    }
    .franja strong {
      font-size: 11px;
    }
    .resumen {
      padding: 10px 12px;
      border-left: 3px solid var(--cd-color-accent-strong);
      background: color-mix(in srgb, var(--cd-color-accent) 12%, var(--cd-color-surface));
    }
    .nota-pago {
      text-align: center;
      margin-top: 2px;
    }
    .calendario {
      margin-top: 0;
    }
    .calendario__scroll {
      max-height: 390px;
      max-width: 100%;
      overflow: auto;
    }
    @media (max-width: 767px) {
      .bloque {
        padding: var(--cd-space-4);
      }
      .hechos,
      .caracteristicas {
        grid-template-columns: 1fr;
      }
      .puntuacion {
        justify-items: start;
      }
      .reserva {
        order: -1;
      }
      .franjas {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }
    .calendario__tabla {
      min-width: 620px;
    }
    .franjas__titulo {
      grid-column: 1 / -1;
      margin-bottom: 2px;
    }
    @media (min-width: 960px) {
      .columnas {
        grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.85fr);
        align-items: start;
      }
      .reserva {
        position: sticky;
        top: calc(var(--cd-topbar-height) + var(--cd-space-4));
      }
    }

    /* Vista previa del propietario y galeria completa */
    .preview-bar {
      position: sticky;
      top: calc(var(--cd-topbar-height) + var(--cd-space-2));
      z-index: 5;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--cd-space-3);
      flex-wrap: wrap;
      padding: var(--cd-space-3) var(--cd-space-4);
      margin-bottom: var(--cd-space-4);
      border: 1px solid var(--cd-color-accent-strong);
      border-radius: var(--cd-radius-md);
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-1);
    }
    .preview-bar div {
      display: grid;
      gap: 2px;
    }
    .preview-bar .cd-btn {
      text-decoration: none;
    }
    .galeria {
      position: relative;
      display: block;
      overflow: hidden;
      gap: 0;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-lg);
      background: var(--cd-color-surface-alt);
      margin-bottom: var(--cd-space-5);
    }
    .galeria__principal {
      position: relative;
      display: block;
      width: 100%;
      height: clamp(230px, 34vw, 400px);
      margin: 0;
      padding: 0;
      border: 0;
      background: var(--cd-color-surface-alt);
      cursor: zoom-in;
    }
    .galeria__principal img {
      display: block;
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .galeria__contador {
      position: absolute;
      left: 12px;
      bottom: 12px;
      padding: 4px 10px;
      border-radius: var(--cd-radius-pill);
      background: rgba(15, 23, 42, 0.72);
      color: #fff;
      font-size: var(--cd-text-xs);
      font-weight: 700;
    }
    .galeria__miniaturas {
      position: static;
      display: flex;
      gap: 8px;
      padding: 8px;
      overflow-x: auto;
      border-top: 1px solid var(--cd-color-border);
      background: var(--cd-color-surface);
    }
    .miniatura {
      flex: 0 0 auto;
      width: 84px;
      height: 60px;
      padding: 0;
      overflow: hidden;
      border: 2px solid transparent;
      border-radius: var(--cd-radius-sm);
      background: none;
      cursor: pointer;
    }
    .miniatura img {
      display: block;
      width: 100%;
      height: 100%;
      object-fit: cover;
      border-radius: 6px;
    }
    .miniatura--activa {
      border-color: var(--cd-color-accent-strong);
    }
    .miniatura:focus-visible,
    .galeria__principal:focus-visible,
    .visor button:focus-visible {
      outline: 3px solid var(--cd-color-focus);
      outline-offset: 2px;
    }
    .galeria__flecha {
      position: absolute;
      top: clamp(90px, 15vw, 160px);
      z-index: 4;
      display: grid;
      place-items: center;
      width: 40px;
      height: 52px;
      border: 1px solid rgba(255, 255, 255, 0.45);
      border-radius: var(--cd-radius-sm);
      background: rgba(15, 25, 30, 0.55);
      color: #fff;
      font-size: 32px;
      line-height: 1;
      cursor: pointer;
      backdrop-filter: blur(4px);
    }
    .galeria__flecha--prev {
      left: 12px;
    }
    .galeria__flecha--next {
      right: 12px;
    }
    .galeria__flecha:hover {
      background: var(--cd-color-inverse-bg);
      border-color: var(--cd-color-accent);
    }
    .visor {
      position: fixed;
      inset: 0;
      z-index: 1000;
      display: grid;
      place-items: center;
      padding: var(--cd-space-5);
      background: rgba(2, 6, 23, 0.92);
    }
    .visor img {
      max-width: min(1100px, 92vw);
      max-height: 82vh;
      object-fit: contain;
      border-radius: var(--cd-radius-sm);
    }
    .visor__cerrar {
      position: absolute;
      top: 16px;
      right: 16px;
      width: 40px;
      height: 40px;
      border: 1px solid rgba(255, 255, 255, 0.5);
      border-radius: 50%;
      background: rgba(15, 23, 42, 0.7);
      color: #fff;
      font-size: 20px;
      cursor: pointer;
    }
    .visor__nav {
      position: absolute;
      top: 50%;
      transform: translateY(-50%);
      width: 44px;
      height: 64px;
      border: 1px solid rgba(255, 255, 255, 0.4);
      border-radius: var(--cd-radius-sm);
      background: rgba(15, 23, 42, 0.65);
      color: #fff;
      font-size: 38px;
      line-height: 1;
      cursor: pointer;
    }
    .visor__nav--prev {
      left: 16px;
    }
    .visor__nav--next {
      right: 16px;
    }
    .visor__contador {
      position: absolute;
      bottom: 18px;
      left: 50%;
      transform: translateX(-50%);
      color: #fff;
      font-size: var(--cd-text-sm);
      font-weight: 700;
    }
    @media (max-width: 640px) {
      .galeria__principal {
        height: 240px;
      }
      .preview-bar {
        position: static;
      }
    }

    /* Fusion skin: detalle editorial */
    .cd-page {
      padding-top: var(--cd-space-4);
    }
    .migas {
      margin-bottom: var(--cd-space-4);
      color: var(--cd-color-text-muted);
      font-size: 11px;
    }
    .migas a {
      color: var(--cd-color-text-muted);
      text-decoration: none;
    }
    .cabecera {
      margin-bottom: var(--cd-space-5);
    }
    .cabecera__texto h1 {
      font-size: clamp(36px, 4.6vw, 58px);
      letter-spacing: -0.05em;
    }
    .ubicacion {
      font-size: 14px;
    }
    .puntuacion {
      border-radius: var(--cd-radius-md);
    }
    .galeria {
      box-shadow: var(--cd-shadow-2);
    }
    .columnas {
      gap: clamp(26px, 4vw, 48px);
    }
    .bloque {
      padding: 26px 0;
      border: 0;
      border-bottom: 1px solid var(--cd-color-border);
      border-radius: 0;
      background: transparent;
      box-shadow: none;
    }
    .bloque:first-child {
      padding-top: 0;
    }
    .bloque:last-child {
      border-bottom: 0;
    }
    .bloque h2.seccion {
      margin-bottom: 14px;
      font-size: clamp(24px, 2.5vw, 31px);
    }
    .bloque h2.seccion::before {
      display: none;
    }
    .caracteristicas {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 8px;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .caracteristicas li {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 12px;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-surface-alt);
      font-size: 13px;
    }
    .check {
      display: grid;
      place-items: center;
      width: 20px;
      height: 20px;
      flex: 0 0 auto;
      border-radius: 50%;
      background: var(--cd-color-success-soft);
      color: var(--cd-color-success);
      font-size: 11px;
      font-weight: 900;
    }
    .reserva {
      position: sticky;
      top: calc(var(--cd-topbar-height) + var(--cd-space-4));
      padding: 24px;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-lg);
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-2);
    }
    .reserva__eyebrow {
      color: var(--cd-color-teal, #167a70);
      font-size: 10px;
      font-weight: 800;
      letter-spacing: 0.14em;
      text-transform: uppercase;
    }
    .reserva .precio {
      margin: 4px 0 0;
      font-family: var(--cd-font-display);
      font-size: 34px;
      font-weight: 800;
      letter-spacing: -0.04em;
    }
    .reserva__titulo {
      margin: 18px 0 14px;
      font-size: 24px;
    }
    .semana__nav {
      padding: 8px 10px;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-md);
      background: var(--cd-color-surface-alt);
    }
    .semana__titulo {
      font-size: 11px;
      font-weight: 800;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }
    .semana__dias {
      display: grid;
      grid-template-columns: repeat(7, minmax(0, 1fr));
      gap: 5px;
      margin-top: 10px;
    }
    .dia {
      display: grid;
      gap: 2px;
      padding: 8px 2px;
      border: 1px solid var(--cd-color-border);
      border-radius: 10px;
      background: var(--cd-color-surface);
      color: var(--cd-color-text);
      text-align: center;
      cursor: pointer;
    }
    .dia__nombre {
      color: var(--cd-color-text-muted);
      font-size: 9px;
      font-weight: 700;
      text-transform: uppercase;
    }
    .dia__numero {
      font-family: var(--cd-font-display);
      font-size: 17px;
    }
    .dia__cupos {
      color: var(--cd-color-text-muted);
      font-size: 8px;
    }
    .dia--activo {
      border-color: var(--cd-color-primary);
      background: var(--cd-color-primary);
      color: var(--cd-color-on-primary);
    }
    .dia--activo .dia__nombre,
    .dia--activo .dia__cupos {
      color: color-mix(in srgb, var(--cd-color-on-primary) 75%, transparent);
    }
    .dia--hoy {
      box-shadow: inset 0 0 0 2px var(--cd-color-accent);
    }
    .dia--pasado {
      opacity: 0.4;
    }
    .franjas {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 7px;
      max-height: 270px;
      overflow-y: auto;
      padding-right: 2px;
    }
    .franja {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      padding: 10px 11px;
      border: 1px solid var(--cd-color-border);
      border-radius: 10px;
      background: var(--cd-color-surface);
      color: var(--cd-color-text);
      font-size: 11px;
      text-align: left;
      cursor: pointer;
    }
    .franja strong {
      color: var(--cd-color-teal, #167a70);
      font-size: 12px;
    }
    .franja:hover {
      border-color: var(--cd-color-primary);
    }
    .franja--activa {
      border-color: var(--cd-color-primary);
      background: var(--cd-color-accent);
      color: var(--cd-color-primary);
    }
    .franja--activa strong {
      color: var(--cd-color-primary);
    }
    .resumen {
      margin-top: 12px;
      padding: 10px 12px;
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-surface-alt);
    }
    .reserva .cd-btn--block {
      margin-top: 12px;
    }
  `,
})
export class CanchaDetalleComponent implements OnInit {
  private readonly api = inject(VenuesApi);
  private readonly engagement = inject(EngagementApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly comparacion = inject(ComparacionService);

  readonly id = input.required<string>();

  protected readonly desdePanel = this.route.snapshot.queryParamMap.get('from') === 'panel';
  protected readonly indiceFoto = signal(0);
  protected readonly visorAbierto = signal(false);

  protected readonly cancha = signal<CanchaDetalle | null>(null);
  protected readonly horarios = signal<HorarioCancha[]>([]);
  protected readonly calificaciones = signal<CalificacionesCancha | null>(null);
  protected readonly diasSemana = signal<DiaSemana[]>([]);
  protected readonly inicioSemana = signal(lunesDe(hoyLima()));
  protected readonly mesSeleccionado = signal(hoyLima().slice(0, 7));
  protected readonly fechaSeleccionada = signal(hoyLima());
  protected readonly seleccionada = signal<FranjaDisponible | null>(null);
  protected readonly cargando = signal(true);
  protected readonly cargandoSemana = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly errorSemana = signal<string | null>(null);
  protected readonly duracion = signal(60);

  protected readonly mesActual = hoyLima().slice(0, 7);
  protected readonly fechaMaxima = sumarDias(hoyLima(), APP_CONFIG.diasAnticipacionMax);
  protected readonly mesMax = this.fechaMaxima.slice(0, 7);
  protected readonly etiquetaSemana = computed(() => etiquetaDeSemana(this.inicioSemana()));
  protected readonly esSemanaActual = computed(() => this.inicioSemana() === lunesDe(hoyLima()));
  protected readonly esUltimaSemana = computed(() => sumarDias(this.inicioSemana(), 7) > this.fechaMaxima);
  protected readonly diaSeleccionado = computed(
    () => this.diasSemana().find((dia) => dia.fecha === this.fechaSeleccionada()) ?? null,
  );
  /**
   * Filas del calendario: las franjas que define el horario configurado, de modo que un
   * bloqueo o una reserva se vean como "sin cupo" en la tabla y no hagan desaparecer la
   * hora. Si la cancha no tiene agenda, las filas salen de las franjas libres que devuelve
   * el backend (que aplica la ventana por defecto).
   */
  protected readonly horasSemana = computed(() => {
    const horas = new Set<string>();
    const paso = this.duracion();
    for (const horario of this.horarios()) {
      let minuto = minutosDe(horario.horaInicio);
      const fin = minutosDe(horario.horaFin);
      while (minuto + paso <= fin) {
        horas.add(horaDeMinutos(minuto));
        minuto += paso;
      }
    }
    for (const dia of this.diasSemana()) {
      for (const franja of dia.franjas) {
        horas.add(this.hora(franja.inicio));
      }
    }
    return [...horas].sort();
  });
  protected readonly fotoActual = computed(() => {
    const fotos = this.cancha()?.fotos ?? [];
    return fotos[this.indiceFoto()] ?? fotos[0] ?? null;
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    const id = Number(this.id());
    if (!Number.isFinite(id)) {
      this.error.set('Cancha inválida.');
      this.cargando.set(false);
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    void Promise.all([
      firstValueFrom(this.api.detalle(id)),
      firstValueFrom(this.api.horarios(id)).catch(() => [] as HorarioCancha[]),
      firstValueFrom(this.engagement.calificacionesCancha(id)).catch(() => null),
    ])
      .then(([detalle, horarios, calificaciones]) => {
        this.indiceFoto.set(0);
        this.visorAbierto.set(false);
        this.cancha.set(detalle);
        this.horarios.set(horarios);
        this.calificaciones.set(calificaciones);
        this.cargando.set(false);
        void this.cargarSemana();
      })
      .catch((error) => {
        this.error.set(toApiError(error).message);
        this.cargando.set(false);
      });
  }

  protected cambiarDuracion(event: Event): void {
    this.duracion.set(Number((event.target as HTMLSelectElement).value));
    this.seleccionada.set(null);
    void this.cargarSemana();
  }

  protected semanaAnterior(): void {
    const anterior = sumarDias(this.inicioSemana(), -7);
    const actual = lunesDe(hoyLima());
    this.cambiarSemana(anterior < actual ? actual : anterior);
  }

  protected semanaSiguiente(): void {
    if (this.esUltimaSemana()) {
      return;
    }
    this.cambiarSemana(sumarDias(this.inicioSemana(), 7));
  }

  protected cambiarMes(event: Event): void {
    const valor = (event.target as HTMLInputElement).value;
    const mes = valor && valor >= this.mesActual ? (valor > this.mesMax ? this.mesMax : valor) : this.mesActual;
    (event.target as HTMLInputElement).value = mes;
    const base = mes === this.mesActual ? hoyLima() : `${mes}-01`;
    this.cambiarSemana(lunesDe(base));
  }

  protected irAHoy(): void {
    const hoy = hoyLima();
    this.inicioSemana.set(lunesDe(hoy));
    this.fechaSeleccionada.set(hoy);
    this.mesSeleccionado.set(hoy.slice(0, 7));
    this.seleccionada.set(null);
    void this.cargarSemana();
  }

  protected recargarSemana(): void {
    void this.cargarSemana();
  }

  protected seleccionarDia(dia: DiaSemana): void {
    if (dia.esPasado) {
      return;
    }
    this.fechaSeleccionada.set(dia.fecha);
    this.seleccionada.set(null);
  }

  protected seleccionarCelda(dia: DiaSemana, franja: FranjaDisponible): void {
    if (dia.esPasado) {
      return;
    }
    this.fechaSeleccionada.set(dia.fecha);
    this.seleccionada.set(franja);
  }

  protected franjaDe(dia: DiaSemana, hora: string): FranjaDisponible | null {
    if (dia.esPasado) {
      return null;
    }
    return dia.franjas.find((franja) => this.hora(franja.inicio) === hora) ?? null;
  }

  protected seleccionarFoto(indice: number): void {
    this.indiceFoto.set(indice);
  }

  protected abrirVisor(): void {
    if (this.fotoActual()) {
      this.visorAbierto.set(true);
    }
  }

  protected cerrarVisor(): void {
    this.visorAbierto.set(false);
  }

  protected anteriorFoto(event: Event): void {
    event.stopPropagation();
    const total = this.cancha()?.fotos.length ?? 0;
    if (total > 0) {
      this.indiceFoto.update((indice) => (indice - 1 + total) % total);
    }
  }

  protected siguienteFoto(event: Event): void {
    event.stopPropagation();
    const total = this.cancha()?.fotos.length ?? 0;
    if (total > 0) {
      this.indiceFoto.update((indice) => (indice + 1) % total);
    }
  }

  /** Evita miniaturas rotas cuando una URL externa deja de estar disponible. */
  protected usarFallback(event: Event): void {
    const img = event.target as HTMLImageElement;
    if (img.dataset['fallback'] === 'true') {
      return;
    }
    img.dataset['fallback'] = 'true';
    img.src = FOTO_FALLBACK;
  }

  @HostListener('document:keydown.escape')
  protected cerrarVisorConEscape(): void {
    if (this.visorAbierto()) {
      this.visorAbierto.set(false);
    }
  }

  protected hora(inicio: string): string {
    return horaLima(inicio);
  }

  protected textoCupos(cantidad: number): string {
    return `${cantidad} ${cantidad === 1 ? 'libre' : 'libres'}`;
  }

  /** Etiqueta accesible de un dia del selector: informa cupos o el cierre. */
  protected textoDia(dia: DiaSemana): string {
    return dia.cerrado ? `${dia.larga}, cerrado` : `${dia.larga}, ${this.textoCupos(dia.franjas.length)}`;
  }

  protected reservar(): void {
    const franja = this.seleccionada();
    if (!franja) {
      return;
    }
    void this.router.navigate(['/reservar', this.id()], {
      queryParams: { inicio: franja.inicio, fin: franja.fin, precio: franja.precio },
    });
  }

  protected alternarComparacion(): void {
    const cancha = this.cancha();
    if (cancha) {
      this.comparacion.toggle(cancha.id);
    }
  }

  private cambiarSemana(inicio: string): void {
    this.inicioSemana.set(inicio);
    this.mesSeleccionado.set(inicio.slice(0, 7));
    this.seleccionada.set(null);
    void this.cargarSemana();
  }

  private async cargarSemana(): Promise<void> {
    const id = Number(this.id());
    if (!Number.isFinite(id)) {
      return;
    }
    const fechas = Array.from({ length: 7 }, (_, i) => sumarDias(this.inicioSemana(), i));
    const hoy = hoyLima();
    const fechaMaxima = this.fechaMaxima;
    this.cargandoSemana.set(true);
    this.errorSemana.set(null);
    const resultados = await Promise.allSettled(
      fechas.map((fecha) =>
        fecha < hoy || fecha > fechaMaxima
          ? Promise.resolve({ franjas: [] as FranjaDisponible[] })
          : firstValueFrom(this.api.disponibilidad(id, fecha, this.duracion())),
      ),
    );

    const dias = fechas.map((fecha, i) => {
      const resultado = resultados[i];
      const franjas = resultado.status === 'fulfilled' ? resultado.value.franjas : [];
      return this.crearDia(fecha, franjas);
    });
    this.diasSemana.set(dias);

    const consultables = fechas.filter((fecha) => fecha >= hoy && fecha <= fechaMaxima);
    const fallidas = resultados.filter(
      (resultado, i) => resultado.status === 'rejected' && fechas[i] >= hoy && fechas[i] <= fechaMaxima,
    );
    if (consultables.length > 0 && fallidas.length === consultables.length) {
      const primera = fallidas[0];
      const mensaje = primera?.status === 'rejected' ? toApiError(primera.reason).message : null;
      this.errorSemana.set(mensaje ?? 'No se pudo cargar la disponibilidad.');
      this.cargandoSemana.set(false);
      return;
    }

    const anterior = this.fechaSeleccionada();
    const conservar = dias.find((dia) => dia.fecha === anterior && !dia.esPasado);
    const elegido =
      conservar && (conservar.franjas.length > 0 || !dias.some((dia) => !dia.esPasado && dia.franjas.length))
        ? conservar
        : (dias.find((dia) => dia.fecha === hoyLima() && dia.franjas.length > 0 && !dia.esPasado) ??
          dias.find((dia) => !dia.esPasado && dia.franjas.length > 0) ??
          dias.find((dia) => !dia.esPasado) ??
          dias[0]);
    this.fechaSeleccionada.set(elegido.fecha);
    this.cargandoSemana.set(false);
  }

  private crearDia(fecha: string, franjas: FranjaDisponible[]): DiaSemana {
    const partes = partesDeFecha(fecha);
    const hoy = hoyLima();
    const agenda = this.horarios();
    return {
      fecha,
      corta: DIAS_CORTOS[partes.indice],
      larga: `${DIAS[partes.indice + 1]} ${partes.numero} de ${MESES_LARGOS[partes.mes]}`,
      numero: partes.numero,
      esHoy: fecha === hoy,
      esPasado: fecha < hoy,
      // Solo se marca cerrado si el propietario definio agenda: sin agenda el backend
      // ofrece la ventana por defecto y el dia nunca esta "cerrado" por configuracion.
      cerrado: agenda.length > 0 && !agenda.some((horario) => horario.diaSemana === partes.indice + 1),
      franjas,
    };
  }
}
