import { A11yModule } from '@angular/cdk/a11y';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AdminUsuario } from '../../core/api/admin.api';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';

export interface RolesDialogData {
  usuario: AdminUsuario;
  disponibles: string[];
}

const DESCRIPCIONES: Record<string, string> = {
  USER: 'Puede buscar canchas, reservar y reclamar.',
  OWNER: 'Puede publicar y administrar sus propios establecimientos.',
  PLATFORM_ADMIN: 'Administra propietarios, reclamos, usuarios y la operación.',
};

@Component({
  selector: 'cd-admin-roles-dialog',
  imports: [A11yModule, CdButtonDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="contenido" role="document">
      <header>
        <h2 id="roles-dialog-title">Roles de {{ data.usuario.nombreCompleto }}</h2>
        <p class="cd-small cd-muted">{{ data.usuario.correo }}</p>
      </header>

      <fieldset class="roles">
        <legend class="cd-visually-hidden">Roles disponibles</legend>
        @for (rol of data.disponibles; track rol) {
          <label class="rol" [class.rol--activo]="seleccionados().includes(rol)">
            <input
              type="checkbox"
              [checked]="seleccionados().includes(rol)"
              (change)="alternar(rol, $event)"
            />
            <span>
              <strong>{{ rol }}</strong>
              <span class="cd-xs cd-muted">{{ descripcion(rol) }}</span>
            </span>
          </label>
        }
      </fieldset>

      @if (error()) {
        <p class="cd-small error" role="alert">{{ error() }}</p>
      }

      <footer>
        <button cdButton variant="ghost" type="button" (click)="cerrar()">Cancelar</button>
        <button cdButton type="button" (click)="guardar()" cdkFocusInitial>Guardar roles</button>
      </footer>
    </div>
  `,
  styles: `
    .contenido {
      display: grid;
      gap: var(--cd-space-4);
      padding: var(--cd-space-5);
      background: var(--cd-color-surface);
      border-radius: var(--cd-radius-md);
      min-width: min(440px, 90vw);
    }
    h2 {
      font-size: var(--cd-text-lg);
    }
    .roles {
      display: grid;
      gap: var(--cd-space-2);
      border: 0;
      padding: 0;
      margin: 0;
    }
    .rol {
      display: flex;
      gap: var(--cd-space-3);
      align-items: flex-start;
      padding: var(--cd-space-3);
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-sm);
      cursor: pointer;
    }
    .rol--activo {
      border-color: var(--cd-color-primary);
      background: var(--cd-color-primary-soft, color-mix(in srgb, var(--cd-color-primary) 8%, transparent));
    }
    .rol input {
      margin-top: 3px;
    }
    .rol span {
      display: grid;
      gap: 2px;
    }
    .error {
      color: var(--cd-color-danger);
    }
    footer {
      display: flex;
      justify-content: flex-end;
      gap: var(--cd-space-2);
      flex-wrap: wrap;
    }
  `,
})
export class AdminRolesDialogComponent {
  protected readonly data = inject<RolesDialogData>(DIALOG_DATA);
  private readonly ref = inject<DialogRef<string[] | undefined>>(DialogRef);

  protected readonly seleccionados = signal<string[]>([...this.data.usuario.roles]);
  protected readonly error = signal<string | null>(null);

  protected descripcion(rol: string): string {
    return DESCRIPCIONES[rol] ?? '';
  }

  protected alternar(rol: string, event: Event): void {
    const activo = (event.target as HTMLInputElement).checked;
    this.seleccionados.update((roles) =>
      activo ? (roles.includes(rol) ? roles : [...roles, rol]) : roles.filter((r) => r !== rol),
    );
    this.error.set(null);
  }

  protected guardar(): void {
    if (this.seleccionados().length === 0) {
      this.error.set('El usuario debe conservar al menos un rol.');
      return;
    }
    this.ref.close(this.seleccionados());
  }

  protected cerrar(): void {
    this.ref.close(undefined);
  }
}
