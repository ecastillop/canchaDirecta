import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { CdButtonDirective } from '../button/cd-button.directive';

/**
 * Paginador reutilizable para listas y tablas. Trabaja en memoria: el componente
 * que lo usa le entrega el total de elementos y recibe la pagina seleccionada.
 */
@Component({
  selector: 'cd-paginator',
  imports: [CdButtonDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (totalPaginas() > 1) {
      <nav class="paginacion" [attr.aria-label]="etiqueta()">
        <span class="cd-small cd-muted rango" aria-live="polite" aria-atomic="true"
          >Mostrando {{ desde() }}–{{ hasta() }} de {{ total() }}</span
        >
        <div class="controles">
          <button
            cdButton
            variant="secondary"
            size="sm"
            type="button"
            [disabled]="pagina() === 1"
            (click)="ir(pagina() - 1)"
          >
            Anterior
          </button>
          <div class="paginas">
            @for (numero of paginas(); track $index) {
              @if (numero === null) {
                <span class="puntos" aria-hidden="true">…</span>
              } @else {
                <button
                  type="button"
                  class="pagina"
                  [class.pagina--activa]="numero === pagina()"
                  [attr.aria-current]="numero === pagina() ? 'page' : null"
                  [attr.aria-label]="'Página ' + numero"
                  (click)="ir(numero)"
                >
                  {{ numero }}
                </button>
              }
            }
          </div>
          <button
            cdButton
            variant="secondary"
            size="sm"
            type="button"
            [disabled]="pagina() === totalPaginas()"
            (click)="ir(pagina() + 1)"
          >
            Siguiente
          </button>
        </div>
      </nav>
    }
  `,
  styles: `
    .paginacion {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: center;
      gap: var(--cd-space-3);
      margin-top: var(--cd-space-4);
      min-height: 40px;
    }
    .rango {
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }
    .controles {
      display: flex;
      align-items: center;
      gap: var(--cd-space-2);
      flex-wrap: nowrap;
    }
    .paginas {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: var(--cd-space-1);
      min-inline-size: calc(7 * 36px + 6 * var(--cd-space-1));
    }
    .pagina {
      inline-size: 36px;
      min-inline-size: 36px;
      height: 34px;
      padding: 0;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-surface);
      color: var(--cd-color-text);
      font: inherit;
      font-variant-numeric: tabular-nums;
      font-weight: 600;
      cursor: pointer;
    }
    .pagina:hover {
      border-color: var(--cd-color-primary);
    }
    .pagina--activa {
      background: var(--cd-color-primary);
      border-color: var(--cd-color-primary);
      color: #fff;
    }
    .puntos {
      inline-size: 24px;
      text-align: center;
      color: var(--cd-color-text-muted);
    }
    @media (max-width: 720px) {
      .paginacion {
        grid-template-columns: minmax(0, 1fr);
      }
      .rango {
        justify-self: start;
      }
      .controles {
        flex-wrap: wrap;
      }
      .paginas {
        order: 3;
        inline-size: 100%;
        min-inline-size: 0;
      }
    }
  `,
})
export class CdPaginatorComponent {
  readonly pagina = input.required<number>();
  readonly total = input.required<number>();
  readonly tamano = input(10);
  readonly etiqueta = input('Paginación');
  readonly paginaChange = output<number>();

  protected readonly totalPaginas = computed(() =>
    Math.max(1, Math.ceil(this.total() / Math.max(1, this.tamano()))),
  );
  protected readonly desde = computed(() =>
    this.total() === 0 ? 0 : (this.pagina() - 1) * this.tamano() + 1,
  );
  protected readonly hasta = computed(() => Math.min(this.pagina() * this.tamano(), this.total()));
  protected readonly paginas = computed<Array<number | null>>(() => {
    const total = this.totalPaginas();
    const actual = this.pagina();
    if (total <= 7) {
      return Array.from({ length: total }, (_, i) => i + 1);
    }
    const numeros: Array<number | null> = [1];
    const inicio = Math.max(2, actual - 1);
    const fin = Math.min(total - 1, actual + 1);
    if (inicio > 2) {
      numeros.push(null);
    }
    for (let n = inicio; n <= fin; n++) {
      numeros.push(n);
    }
    if (fin < total - 1) {
      numeros.push(null);
    }
    numeros.push(total);
    return numeros;
  });

  protected ir(numero: number): void {
    if (numero < 1 || numero > this.totalPaginas() || numero === this.pagina()) {
      return;
    }
    this.paginaChange.emit(numero);
  }
}
