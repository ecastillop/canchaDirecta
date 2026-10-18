import { Directive } from '@angular/core';

/** Aplica el estilo de control a inputs, selects y textareas nativos. */
@Directive({
  selector: 'input[cdInput], select[cdInput], textarea[cdInput]',
  host: { class: 'cd-input' },
})
export class CdInputDirective {}
