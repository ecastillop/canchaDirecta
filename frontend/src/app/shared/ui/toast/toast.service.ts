import { Injectable, signal } from '@angular/core';

export type ToastVariant = 'info' | 'success' | 'warning' | 'danger';

export interface Toast {
  id: number;
  message: string;
  variant: ToastVariant;
}

let siguienteId = 1;

/** Avisos breves anunciados por `aria-live` en el host global. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly _toasts = signal<Toast[]>([]);
  readonly toasts = this._toasts.asReadonly();

  show(message: string, variant: ToastVariant = 'info', duration = 4500): void {
    const toast: Toast = { id: siguienteId++, message, variant };
    this._toasts.update((lista) => [...lista, toast]);
    if (duration > 0) {
      setTimeout(() => this.dismiss(toast.id), duration);
    }
  }

  success(message: string): void {
    this.show(message, 'success');
  }

  error(message: string): void {
    this.show(message, 'danger', 7000);
  }

  dismiss(id: number): void {
    this._toasts.update((lista) => lista.filter((toast) => toast.id !== id));
  }
}
