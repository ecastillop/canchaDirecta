import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';

@Component({
  selector: 'app-no-encontrado',
  imports: [ReactiveFormsModule, RouterLink, CdButtonDirective, CdInputDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cd-container estado">
      <p class="codigo" aria-hidden="true">404</p>
      <h1>No encontramos esa página</h1>
      <p class="cd-muted">El enlace puede haber cambiado o ya no existir. Puedes buscar una cancha.</p>
      <form class="buscador" (ngSubmit)="buscar()" role="search">
        <label class="cd-field">
          <span class="cd-visually-hidden">Buscar canchas</span>
          <input cdInput [formControl]="q" type="search" placeholder="Distrito, deporte o cancha" />
        </label>
        <button cdButton type="submit">Buscar</button>
      </form>
      <a routerLink="/" class="volver">Volver al inicio</a>
    </div>
  `,
  styles: `
    .estado {
      display: grid;
      gap: var(--cd-space-3);
      justify-items: start;
      padding-block: var(--cd-space-8);
      max-width: 620px;
    }
    .codigo {
      font-size: 64px;
      font-weight: 800;
      color: var(--cd-color-accent);
      line-height: 1;
    }
    .buscador {
      display: flex;
      flex-wrap: wrap;
      gap: var(--cd-space-2);
      width: 100%;
    }
    .buscador .cd-field {
      flex: 1 1 220px;
    }
    .volver {
      font-size: var(--cd-text-sm);
    }
  `,
})
export class NoEncontradoComponent {
  private readonly router = inject(Router);
  protected readonly q = new FormControl('', { nonNullable: true });

  protected buscar(): void {
    const termino = this.q.value.trim();
    void this.router.navigate(['/canchas'], { queryParams: termino ? { q: termino } : {} });
  }
}
