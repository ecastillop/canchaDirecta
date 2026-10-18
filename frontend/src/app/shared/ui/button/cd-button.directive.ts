import { Directive, computed, input } from '@angular/core';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'accent';

/** Boton nativo con variantes del sistema de diseno. */
@Directive({
  selector: '[cdButton]',
  host: {
    class: 'cd-btn',
    '[class.cd-btn--primary]': 'variant() === "primary"',
    '[class.cd-btn--secondary]': 'variant() === "secondary"',
    '[class.cd-btn--ghost]': 'variant() === "ghost"',
    '[class.cd-btn--danger]': 'variant() === "danger"',
    '[class.cd-btn--accent]': 'variant() === "accent"',
    '[class.cd-btn--sm]': 'size() === "sm"',
    '[class.cd-btn--block]': 'block()',
    '[class.is-loading]': 'loading()',
    '[attr.aria-busy]': 'loading() ? "true" : null',
  },
})
export class CdButtonDirective {
  readonly variant = input<ButtonVariant>('primary');
  readonly size = input<'md' | 'sm'>('md');
  readonly loading = input(false);
  readonly block = input(false);

  readonly etiqueta = computed(() => (this.loading() ? 'Procesando' : null));
}
