import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ThemeService } from '../../../core/theme/theme.service';
import { PortalIconComponent } from '../portal-icon/portal-icon.component';

/**
 * Boton de modo claro/oscuro para las cabeceras de los portales (ADR-0046).
 *
 * <p>Usa el mismo `ThemeService` que la topbar publica, asi que la preferencia es una sola:
 * cambiar el modo dentro del panel tambien lo cambia en el sitio y queda en `localStorage`
 * (`cd.modo`). Muestra el modo activo y anuncia la accion en `aria-label`, igual que la topbar.</p>
 */
@Component({
  selector: 'cd-theme-toggle',
  imports: [PortalIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      class="tema"
      (click)="theme.alternarModo()"
      [attr.aria-pressed]="theme.oscuro()"
      [attr.aria-label]="theme.oscuro() ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'"
      [title]="theme.oscuro() ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'"
    >
      <cd-portal-icon [name]="theme.oscuro() ? 'moon' : 'sun'" [size]="19" />
    </button>
  `,
  styles: `
    .tema {
      display: grid;
      place-items: center;
      width: 36px;
      height: 36px;
      padding: 0;
      border: 0;
      border-radius: 10px;
      background: transparent;
      color: var(--cd-color-text-muted);
      cursor: pointer;
    }
    .tema:hover {
      background: var(--cd-color-surface-alt);
      color: var(--cd-color-text);
    }
    .tema:focus-visible {
      outline: 3px solid var(--cd-color-primary);
      outline-offset: 2px;
    }
  `,
})
export class CdThemeToggleComponent {
  protected readonly theme = inject(ThemeService);
}
