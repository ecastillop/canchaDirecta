import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type IconName =
  | 'pin'
  | 'star'
  | 'heart'
  | 'arrow'
  | 'calendar'
  | 'clock'
  | 'check'
  | 'users'
  | 'compare'
  | 'search'
  | 'filter'
  | 'sort'
  | 'shield';

/** Iconos SVG inline, sin dependencias externas. */
@Component({
  selector: 'cd-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      [attr.width]="size()"
      [attr.height]="size()"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      @switch (name()) {
        @case ('pin') {
          <path d="M12 21s7-5.4 7-11a7 7 0 1 0-14 0c0 5.6 7 11 7 11z" />
          <circle cx="12" cy="10" r="2.6" />
        }
        @case ('star') {
          <path d="m12 3.5 2.6 5.3 5.9.9-4.3 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z" />
        }
        @case ('heart') {
          <path d="M20.8 8.7c0 5.2-8.8 10-8.8 10s-8.8-4.8-8.8-10a4.6 4.6 0 0 1 8.8-1.9 4.6 4.6 0 0 1 8.8 1.9z" />
        }
        @case ('arrow') {
          <path d="M5 12h14M13 6l6 6-6 6" />
        }
        @case ('calendar') {
          <rect x="3" y="5" width="18" height="16" rx="2" />
          <path d="M16 3v4M8 3v4M3 10h18" />
        }
        @case ('clock') {
          <circle cx="12" cy="12" r="9" />
          <path d="M12 7v5l3 2" />
        }
        @case ('check') {
          <path d="m4 12 5 5L20 6" />
        }
        @case ('users') {
          <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
          <circle cx="9" cy="7" r="4" />
          <path d="M22 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8" />
        }
        @case ('compare') {
          <rect x="3" y="5" width="7" height="14" rx="1.5" />
          <rect x="14" y="5" width="7" height="14" rx="1.5" />
        }
        @case ('search') {
          <circle cx="11" cy="11" r="7" />
          <path d="m20 20-3.5-3.5" />
        }
        @case ('filter') {
          <path d="M4 5h16M7 12h10M10 19h4" />
        }
        @case ('sort') {
          <path d="M7 4v16M3.5 7.5 7 4l3.5 3.5" />
          <path d="M17 20V4M13.5 16.5 17 20l3.5-3.5" />
        }
        @case ('shield') {
          <path d="M12 3l7 3v6c0 4.2-3 7.6-7 9-4-1.4-7-4.8-7-9V6l7-3z" />
          <path d="m9 12 2 2 4-4" />
        }
      }
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex: 0 0 auto;
    }
  `,
})
export class CdIconComponent {
  readonly name = input.required<IconName>();
  readonly size = input(16);
}
