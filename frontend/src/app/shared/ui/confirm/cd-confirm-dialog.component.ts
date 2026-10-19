import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { Component, inject } from '@angular/core';
import { CdButtonDirective } from '../../ui/button/cd-button.directive';

export interface ConfirmData {
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  danger?: boolean;
}

@Component({
  selector: 'cd-confirm-dialog',
  imports: [CdButtonDirective],
  template: `
    <div class="contenido" role="document">
      <h2 id="cd-confirm-title">{{ data.title }}</h2>
      <p>{{ data.message }}</p>
      <div class="acciones">
        <button cdButton variant="ghost" type="button" (click)="cerrar(false)">
          {{ data.cancelText ?? 'Cancelar' }}
        </button>
        <button
          cdButton
          [variant]="data.danger ? 'danger' : 'primary'"
          type="button"
          (click)="cerrar(true)"
        >
          {{ data.confirmText ?? 'Confirmar' }}
        </button>
      </div>
    </div>
  `,
  styles: `
    .contenido {
      display: grid;
      gap: var(--cd-space-3);
      padding: var(--cd-space-5);
      background: var(--cd-color-surface);
      border-radius: var(--cd-radius-md);
    }
    h2 {
      font-size: var(--cd-text-lg);
    }
    p {
      color: var(--cd-color-text-muted);
    }
    .acciones {
      display: flex;
      justify-content: flex-end;
      gap: var(--cd-space-2);
      flex-wrap: wrap;
      margin-top: var(--cd-space-2);
    }
  `,
})
export class CdConfirmDialogComponent {
  protected readonly data = inject<ConfirmData>(DIALOG_DATA);
  private readonly ref = inject<DialogRef<boolean>>(DialogRef);

  cerrar(resultado: boolean): void {
    this.ref.close(resultado);
  }
}
