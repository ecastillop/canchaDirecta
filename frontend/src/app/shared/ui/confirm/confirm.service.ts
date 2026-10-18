import { Dialog } from '@angular/cdk/dialog';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CdConfirmDialogComponent, ConfirmData } from './cd-confirm-dialog.component';

/** Dialogo de confirmacion para acciones destructivas o irreversibles. */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private readonly dialog = inject(Dialog);

  async confirm(data: ConfirmData): Promise<boolean> {
    const ref = this.dialog.open<boolean>(CdConfirmDialogComponent, {
      data,
      width: 'min(440px, 92vw)',
      ariaLabelledBy: 'cd-confirm-title',
      autoFocus: 'first-tabbable',
    });
    const resultado = await firstValueFrom(ref.closed);
    return resultado === true;
  }
}
