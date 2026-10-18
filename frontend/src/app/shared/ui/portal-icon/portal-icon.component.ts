import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';

const PATHS: Record<string, string> = {
  search: '<circle cx="11" cy="11" r="7"/><path d="m16 16 5 5"/>',
  calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v4M17 3v4M3 10h18"/>',
  pin: '<path d="M20 10c0 5-8 12-8 12S4 15 4 10a8 8 0 1 1 16 0Z"/><circle cx="12" cy="10" r="2.4"/>',
  star: '<path d="m12 2 3.1 6.4 7.1 1-5.1 5 1.2 7.1-6.3-3.3-6.3 3.3 1.2-7.1-5.1-5 7.1-1Z"/>',
  arrow: '<path d="M5 12h14m-7-7 7 7-7 7"/>',
  chevron: '<path d="m9 18 6-6-6-6"/>',
  chevdown: '<path d="m6 9 6 6 6-6"/>',
  check: '<path d="m5 12 5 5L20 7"/>',
  close: '<path d="M5 5 19 19M19 5 5 19"/>',
  heart: '<path d="M20.8 4.7a5.4 5.4 0 0 0-7.7 0L12 5.8l-1.1-1.1a5.4 5.4 0 0 0-7.7 7.7L12 21l8.8-8.6a5.4 5.4 0 0 0 0-7.7Z"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
  users: '<circle cx="9" cy="8" r="3"/><path d="M3 20a6 6 0 0 1 12 0M17 6a3 3 0 0 1 0 6m0 2a6 6 0 0 1 4 6"/>',
  grid: '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
  ball: '<circle cx="12" cy="12" r="9"/><path d="m12 5-4 3 1.5 5h5L16 8Zm-2.5 8L6 17m8.5-4 3.5 4M12 5V3M6 8 4 7M18 8l2-1"/>',
  credit: '<rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20M6 15h4"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 6v6l4 2"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M12 2v3m0 14v3M2 12h3m14 0h3M4.9 4.9 7 7m10 10 2.1 2.1M19.1 4.9 17 7M7 17l-2.1 2.1"/>',
  chart: '<path d="M4 20V4M4 20h17M8 16l4-5 3 2 5-7"/>',
  building: '<rect x="4" y="3" width="16" height="18" rx="2"/><path d="M9 7h2m3 0h2M9 11h2m3 0h2M9 15h2m3 0h2M10 21v-4h4v4"/>',
  ticket: '<path d="M3 8a2 2 0 0 0 0 4v5h18v-5a2 2 0 0 1 0-4V3H3Z"/><path d="M13 3v3m0 3v3m0 3v2"/>',
  receipt: '<path d="M5 3h14v18l-3-2-4 2-4-2-3 2Z"/><path d="M9 8h6M9 12h6"/>',
  shield: '<path d="M12 2 21 6v6c0 5-4 8-9 10-5-2-9-5-9-10V6Z"/><path d="m8 12 3 3 5-5"/>',
  bell: '<path d="M18 8a6 6 0 0 0-12 0c0 8-3 8-3 10h18c0-2-3-2-3-10M10 21h4"/>',
  menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  minus: '<path d="M5 12h14"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  edit: '<path d="m4 17-.5 4L8 20 20 8l-4-4L4 16Zm9-10 4 4"/>',
  trash: '<path d="M4 7h16M9 7V4h6v3M6 7l1 14h10l1-14M10 11v6m4-6v6"/>',
  logout: '<path d="M10 4H4v16h6M14 7l5 5-5 5M8 12h11"/>',
  filter: '<path d="M3 5h18M7 12h10m-7 7h4"/>',
  help: '<circle cx="12" cy="12" r="9"/><path d="M9 9a3 3 0 1 1 5 2c-2 1-2 2-2 3m0 4h.01"/>',
  lock: '<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/>',
  spark: '<path d="m12 2 2.1 6 6 2.1-6 2.1L12 18l-2.1-5.9L4 10l5.9-2ZM20 17l.8 2.2L23 20l-2.2.8L20 23l-.8-2.2L17 20l2.2-.8Z"/>',
  wallet: '<rect x="3" y="5" width="18" height="15" rx="2"/><path d="M3 9h18M16 14h2"/>',
  alert: '<path d="m12 3 10 18H2Z"/><path d="M12 9v5m0 3h.01"/>',
  list: '<path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M2 12h2m16 0h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
  moon: '<path d="M21 12.8A8.5 8.5 0 1 1 11.2 3a6.8 6.8 0 0 0 9.8 9.8Z"/>',
};

/** Iconos de línea del portal, con los mismos trazos del prototipo standalone. */
@Component({
  selector: 'cd-portal-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span
    class="portal-icono"
    aria-hidden="true"
    [style.--icono.px]="size()"
    [innerHTML]="contenido()"
  ></span>`,
  styles: `
    .portal-icono {
      display: inline-flex;
      flex-shrink: 0;
      width: var(--icono, 19px);
      height: var(--icono, 19px);
    }
    .portal-icono ::ng-deep svg {
      width: 100%;
      height: 100%;
      display: block;
    }
  `,
})
export class PortalIconComponent {
  private readonly sanitizer = inject(DomSanitizer);

  readonly name = input('grid');
  readonly size = input(19);

  protected readonly contenido = computed(() => {
    const cuerpo = PATHS[this.name()] ?? PATHS['grid'];
    const tamano = this.size();
    return this.sanitizer.bypassSecurityTrustHtml(
      `<svg width="${tamano}" height="${tamano}" viewBox="0 0 24 24" fill="none" ` +
        `stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" ` +
        `stroke-width="1.8" aria-hidden="true">${cuerpo}</svg>`,
    );
  });
}
