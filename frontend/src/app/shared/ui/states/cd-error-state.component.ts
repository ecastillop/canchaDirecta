import { Component, input, output } from '@angular/core';
import { CdButtonDirective } from '../button/cd-button.directive';

@Component({
  selector: 'cd-error-state',
  imports: [CdButtonDirective],
  template: `
    <div class="estado" role="alert">
      <h3>{{ title() }}</h3>
      <p class="cd-muted">{{ message() }}</p>
      @if (retryable()) {
        <button cdButton variant="secondary" type="button" (click)="retry.emit()">Reintentar</button>
      }
    </div>
  `,
  styles: `
    .estado {
      display: grid;
      justify-items: center;
      gap: var(--cd-space-2);
      text-align: center;
      padding: var(--cd-space-6) var(--cd-space-4);
      border: 1px solid var(--cd-color-danger-soft);
      border-radius: var(--cd-radius-md);
      background: var(--cd-color-surface);
    }
    h3 {
      font-size: var(--cd-text-base);
      color: var(--cd-color-danger);
    }
    p {
      max-width: 52ch;
    }
  `,
})
export class CdErrorStateComponent {
  readonly title = input('No se pudo cargar la información');
  readonly message = input('Intenta nuevamente en unos segundos.');
  readonly retryable = input(true);
  readonly retry = output<void>();
}
