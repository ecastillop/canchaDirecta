import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'cd-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="bar" [style.width]="width()" [style.height]="height()" [style.border-radius]="radius()"></span>`,
  styles: `
    :host {
      display: block;
    }
    .bar {
      display: block;
      background: linear-gradient(
        90deg,
        var(--cd-color-surface-alt) 25%,
        color-mix(in srgb, var(--cd-color-border) 70%, var(--cd-color-surface-alt)) 37%,
        var(--cd-color-surface-alt) 63%
      );
      background-size: 400% 100%;
      animation: cd-shimmer 1.4s ease infinite;
    }
    @keyframes cd-shimmer {
      0% {
        background-position: 100% 50%;
      }
      100% {
        background-position: 0 50%;
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .bar {
        animation: none;
      }
    }
  `,
})
export class CdSkeletonComponent {
  readonly width = input('100%');
  readonly height = input('16px');
  readonly radius = input('8px');
}
