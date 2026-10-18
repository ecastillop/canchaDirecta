import { Component, input } from '@angular/core';

@Component({
  selector: 'cd-empty-state',
  template: `
    <div class="estado" role="status">
      <span class="icono" aria-hidden="true">◻</span>
      <h2 class="titulo">{{ title() }}</h2>
      @if (message()) {
        <p class="cd-muted">{{ message() }}</p>
      }
      <ng-content />
    </div>
  `,
  styles: `
    .estado {
      display: grid;
      justify-items: center;
      gap: var(--cd-space-2);
      text-align: center;
      padding: var(--cd-space-6) var(--cd-space-4);
      border: 1px dashed var(--cd-color-border);
      border-radius: var(--cd-radius-md);
      background: var(--cd-color-surface);
    }
    .icono {
      font-size: 28px;
      color: var(--cd-color-text-muted);
    }
    h2.titulo {
      font-size: var(--cd-text-base);
    }
    p {
      max-width: 44ch;
    }
  `,
})
export class CdEmptyStateComponent {
  readonly title = input.required<string>();
  readonly message = input('');
}
