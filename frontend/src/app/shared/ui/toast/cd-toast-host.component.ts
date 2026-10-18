import { Component, inject } from '@angular/core';
import { ToastService } from './toast.service';

@Component({
  selector: 'cd-toast-host',
  template: `
    <div class="region" aria-live="polite" aria-atomic="false">
      @for (toast of toasts(); track toast.id) {
        <div class="toast" [class]="'toast toast--' + toast.variant" role="status">
          <span>{{ toast.message }}</span>
          <button type="button" class="cerrar" aria-label="Cerrar aviso" (click)="dismiss(toast.id)">×</button>
        </div>
      }
    </div>
  `,
  styles: `
    .region {
      position: fixed;
      z-index: 900;
      right: var(--cd-space-4);
      bottom: var(--cd-space-4);
      left: var(--cd-space-4);
      display: grid;
      gap: var(--cd-space-2);
      justify-items: end;
      pointer-events: none;
    }
    @media (min-width: 768px) {
      .region {
        left: auto;
        width: min(420px, 92vw);
      }
    }
    .toast {
      pointer-events: auto;
      display: flex;
      align-items: flex-start;
      gap: var(--cd-space-3);
      width: 100%;
      padding: var(--cd-space-3) var(--cd-space-4);
      border-radius: var(--cd-radius-md);
      border: 1px solid var(--cd-color-border);
      background: var(--cd-color-surface);
      box-shadow: var(--cd-shadow-2);
      font-size: var(--cd-text-sm);
      animation: entrar var(--cd-duration-base) var(--cd-ease);
    }
    .toast--success {
      border-left: 4px solid var(--cd-color-success);
    }
    .toast--danger {
      border-left: 4px solid var(--cd-color-danger);
    }
    .toast--warning {
      border-left: 4px solid var(--cd-color-warning);
    }
    .toast--info {
      border-left: 4px solid var(--cd-color-info);
    }
    span {
      flex: 1;
    }
    .cerrar {
      border: 0;
      background: transparent;
      cursor: pointer;
      font-size: 18px;
      line-height: 1;
      color: var(--cd-color-text-muted);
      padding: 0 2px;
    }
    @keyframes entrar {
      from {
        opacity: 0;
        transform: translateY(8px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }
  `,
})
export class CdToastHostComponent {
  private readonly service = inject(ToastService);
  readonly toasts = this.service.toasts;

  dismiss(id: number): void {
    this.service.dismiss(id);
  }
}
