import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AyudaApi } from '../../core/api/ayuda.api';
import { AyudaCategoria } from '../../core/api/ayuda.models';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdIconComponent } from '../../shared/ui/icon/cd-icon.component';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';

@Component({
  selector: 'app-ayuda',
  imports: [
    RouterLink,
    CdButtonDirective,
    CdIconComponent,
    CdSkeletonComponent,
    CdEmptyStateComponent,
    CdErrorStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="hero-ayuda">
      <div class="cd-container">
        <p class="eyebrow">Centro de ayuda</p>
        <h1>¿En qué podemos <em>ayudarte?</em></h1>
        <p>Respuestas rápidas sobre reservas, pagos, tu cuenta, actividades y cupones.</p>
        <label class="buscador">
          <cd-icon name="search" [size]="18" />
          <input
            type="search"
            aria-label="Buscar en el centro de ayuda"
            placeholder="Busca por tema, por ejemplo: reembolso"
            [value]="texto()"
            (input)="buscar($event)"
          />
        </label>
      </div>
    </section>

    <div class="cd-container contenido">
      @if (cargando()) {
        <div class="cd-stack">
          <cd-skeleton height="40px" radius="10px" />
          <cd-skeleton height="120px" radius="16px" />
          <cd-skeleton height="120px" radius="16px" />
        </div>
      } @else if (error()) {
        <cd-error-state [message]="error()!" (retry)="cargar()" />
      } @else {
        <div class="layout">
          <nav class="categorias" aria-label="Categorías de ayuda">
            <button
              type="button"
              class="categoria"
              [class.categoria--activa]="categoriaActiva() === null"
              (click)="categoriaActiva.set(null)"
            >
              Todas <span>{{ totalArticulos() }}</span>
            </button>
            @for (categoria of categorias(); track categoria.id) {
              <button
                type="button"
                class="categoria"
                [class.categoria--activa]="categoriaActiva() === categoria.slug"
                (click)="categoriaActiva.set(categoria.slug)"
              >
                {{ categoria.nombre }} <span>{{ categoria.articulos.length }}</span>
              </button>
            }
          </nav>

          <div class="principal">
            <p class="resultado cd-small cd-muted" aria-live="polite">
              {{ articulosVisibles().length }}
              {{ articulosVisibles().length === 1 ? 'respuesta encontrada' : 'respuestas encontradas' }}
              @if (texto()) {
                para “{{ texto() }}”
              }
            </p>

            @if (categoriasVisibles().length === 0) {
              <cd-empty-state
                title="No encontramos una respuesta"
                message="Prueba con otra palabra o envía tu consulta al equipo de soporte."
              >
                <a routerLink="/reclamos" cdButton variant="secondary" size="sm">Contactar a soporte</a>
              </cd-empty-state>
            } @else {
              @for (categoria of categoriasVisibles(); track categoria.id) {
                <section class="bloque" [attr.aria-labelledby]="'cat-' + categoria.slug">
                  <h2 [id]="'cat-' + categoria.slug">{{ categoria.nombre }}</h2>
                  <div class="faq">
                    @for (articulo of categoria.articulos; track articulo.id) {
                      <details>
                        <summary>{{ articulo.pregunta }}</summary>
                        <p>{{ articulo.respuesta }}</p>
                      </details>
                    }
                  </div>
                </section>
              }
            }
          </div>

          <aside class="contacto">
            <div class="contacto__card">
              <span class="contacto__icono" aria-hidden="true">✉</span>
              <h2>¿Sigue sin resolverse?</h2>
              <p class="cd-small cd-muted">
                Crea un reclamo desde tu cuenta y el equipo de soporte lo revisará contigo.
              </p>
              <a routerLink="/reclamos" cdButton variant="primary" class="cd-btn--block">Crear un reclamo</a>
              <p class="cd-xs cd-muted">También puedes escribir a soporte&#64;canchadirecta.demo</p>
            </div>
            <div class="contacto__card contacto__card--suave">
              <h2>Guías rápidas</h2>
              <a routerLink="/como-funciona">Cómo reservar paso a paso</a>
              <a routerLink="/actividades">Cómo inscribirme a una actividad</a>
              <a routerLink="/canchas">Explorar espacios deportivos</a>
            </div>
          </aside>
        </div>
      }
    </div>
  `,
  styles: `
    .hero-ayuda {
      padding: clamp(52px, 7vw, 86px) 0 clamp(38px, 5vw, 54px);
      background: var(--cd-color-inverse-bg);
      color: var(--cd-color-inverse-text);
    }
    .eyebrow {
      margin: 0 0 12px;
      color: var(--cd-color-brand-light);
      font-size: 11px;
      font-weight: 800;
      letter-spacing: 0.2em;
      text-transform: uppercase;
    }
    h1 {
      margin: 0;
      font-size: clamp(38px, 5vw, 62px);
      letter-spacing: -0.05em;
    }
    h1 em {
      color: var(--cd-color-brand-light);
      font-style: normal;
    }
    .hero-ayuda p {
      max-width: 52ch;
      margin: 16px 0 0;
      color: var(--cd-color-inverse-muted);
    }
    .buscador {
      display: flex;
      align-items: center;
      gap: 10px;
      max-width: 620px;
      margin-top: 24px;
      padding: 12px 16px;
      border-radius: 999px;
      background: var(--cd-color-surface);
      color: var(--cd-color-text);
      box-shadow: var(--cd-shadow-2);
    }
    .buscador input {
      flex: 1;
      border: 0;
      background: transparent;
      color: inherit;
      font: inherit;
      outline: none;
    }
    .contenido {
      padding-block: 38px 70px;
    }
    .layout {
      display: grid;
      grid-template-columns: 220px minmax(0, 1fr) 300px;
      gap: 28px;
      align-items: start;
    }
    .categorias {
      position: sticky;
      top: 90px;
      display: grid;
      gap: 4px;
    }
    .categoria {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      padding: 10px 12px;
      border: 0;
      border-radius: 9px;
      background: transparent;
      color: var(--cd-color-text-muted);
      font: inherit;
      font-size: 13px;
      font-weight: 600;
      text-align: left;
      cursor: pointer;
    }
    .categoria span {
      color: var(--cd-color-text-muted);
      font-size: 11px;
    }
    .categoria:hover {
      background: var(--cd-color-surface-alt);
      color: var(--cd-color-text);
    }
    .categoria--activa {
      background: var(--cd-color-primary);
      color: var(--cd-color-on-primary);
    }
    .categoria--activa span {
      color: var(--cd-color-on-primary);
      opacity: 0.75;
    }
    .principal {
      display: grid;
      gap: 26px;
      min-width: 0;
    }
    .resultado {
      margin: 0;
    }
    .bloque h2 {
      margin: 0 0 12px;
      font-size: 24px;
      letter-spacing: -0.03em;
    }
    .faq {
      display: grid;
      gap: 8px;
    }
    .faq details {
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-md);
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-1);
    }
    .faq summary {
      padding: 16px 18px;
      font-weight: 700;
      cursor: pointer;
      list-style: none;
    }
    .faq summary::-webkit-details-marker {
      display: none;
    }
    .faq summary::after {
      content: '+';
      float: right;
      color: var(--cd-color-primary);
      font-size: 18px;
      line-height: 1;
    }
    .faq details[open] summary::after {
      content: '−';
    }
    .faq details[open] summary {
      border-bottom: 1px solid var(--cd-color-border);
    }
    .faq p {
      margin: 0;
      padding: 14px 18px 18px;
      color: var(--cd-color-text-muted);
      font-size: 14px;
      line-height: 1.65;
      white-space: pre-line;
    }
    .contacto {
      position: sticky;
      top: 90px;
      display: grid;
      gap: 12px;
    }
    .contacto__card {
      display: grid;
      gap: 10px;
      padding: 20px;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-lg);
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-1);
    }
    .contacto__card h2 {
      margin: 0;
      font-size: 18px;
    }
    .contacto__card p {
      margin: 0;
    }
    .contacto__icono {
      display: grid;
      place-items: center;
      width: 38px;
      height: 38px;
      border-radius: 50%;
      background: var(--cd-color-accent-soft);
      color: var(--cd-color-brand-ink);
      font-size: 18px;
    }
    .contacto__card--suave {
      background: var(--cd-color-surface-alt);
      box-shadow: none;
    }
    .contacto__card--suave a {
      color: var(--cd-color-primary);
      font-size: 13px;
      font-weight: 600;
      text-decoration: none;
    }
    .contacto__card--suave a:hover {
      text-decoration: underline;
    }
    @media (max-width: 1100px) {
      .layout {
        grid-template-columns: 190px minmax(0, 1fr);
      }
      .contacto {
        position: static;
        grid-column: 1 / -1;
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }
    @media (max-width: 760px) {
      .layout {
        grid-template-columns: 1fr;
      }
      .categorias {
        position: static;
        display: flex;
        flex-wrap: wrap;
      }
      .categoria {
        flex: 0 0 auto;
      }
      .contacto {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class AyudaComponent implements OnInit {
  private readonly api = inject(AyudaApi);

  protected readonly categorias = signal<AyudaCategoria[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly texto = signal('');
  protected readonly categoriaActiva = signal<string | null>(null);

  protected readonly totalArticulos = computed(() =>
    this.categorias().reduce((total, categoria) => total + categoria.articulos.length, 0),
  );

  protected readonly categoriasVisibles = computed(() => {
    const texto = normalizar(this.texto());
    const activa = this.categoriaActiva();
    return this.categorias()
      .filter((categoria) => activa === null || categoria.slug === activa)
      .map((categoria) => ({
        ...categoria,
        articulos: texto
          ? categoria.articulos.filter(
              (articulo) =>
                normalizar(articulo.pregunta).includes(texto) ||
                normalizar(articulo.respuesta).includes(texto),
            )
          : categoria.articulos,
      }))
      .filter((categoria) => categoria.articulos.length > 0);
  });

  protected readonly articulosVisibles = computed(() =>
    this.categoriasVisibles().flatMap((categoria) => categoria.articulos),
  );

  ngOnInit(): void {
    this.cargar();
  }

  protected buscar(event: Event): void {
    this.texto.set((event.target as HTMLInputElement).value);
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    void firstValueFrom(this.api.listar())
      .then((categorias) => this.categorias.set(categorias))
      .catch((error) => this.error.set(toApiError(error).message))
      .finally(() => this.cargando.set(false));
  }
}

function normalizar(valor: string): string {
  return valor
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim();
}
