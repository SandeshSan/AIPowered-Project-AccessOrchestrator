import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

export type StatTone = 'primary' | 'ok' | 'warn' | 'bad' | 'info';

/** KPI tile: icon, big number, label and an optional hint; links to the page with the details. */
@Component({
  selector: 'app-stat-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule, RouterLink],
  template: `
    <a class="stat" [routerLink]="link()" [attr.data-tone]="tone()" [attr.aria-label]="label() + ': ' + value()">
      <span class="icon"><mat-icon>{{ icon() }}</mat-icon></span>
      <span class="body">
        <span class="label">{{ label() }}</span>
        <span class="value">{{ value() }}</span>
        @if (hint()) { <span class="hint">{{ hint() }}</span> }
      </span>
    </a>
  `,
  styles: [`
    .stat {
      display: flex; gap: 16px; align-items: flex-start; padding: 20px; height: 100%;
      background: var(--app-surface); border: 1px solid var(--app-border); border-radius: var(--app-radius);
      text-decoration: none; color: inherit; transition: box-shadow .15s, border-color .15s;
    }
    .stat:hover, .stat:focus-visible { border-color: #c5cae9; box-shadow: 0 2px 10px rgba(21, 28, 44, .06); outline: none; }
    .icon {
      flex: none; width: 44px; height: 44px; border-radius: 10px; display: grid; place-items: center;
      background: var(--app-primary-soft); color: var(--app-primary);
    }
    [data-tone='ok'] .icon { background: var(--app-ok-soft); color: var(--app-ok); }
    [data-tone='warn'] .icon { background: var(--app-warn-soft); color: var(--app-warn); }
    [data-tone='bad'] .icon { background: var(--app-bad-soft); color: var(--app-bad); }
    [data-tone='info'] .icon { background: var(--app-info-soft); color: var(--app-info); }
    .body { display: flex; flex-direction: column; min-width: 0; }
    .label { font-size: 13px; color: var(--app-muted); font-weight: 500; }
    .value { font-size: 30px; font-weight: 700; line-height: 1.2; letter-spacing: -0.02em; font-variant-numeric: tabular-nums; }
    .hint { font-size: 12px; color: var(--app-muted); margin-top: 2px; }
    @media (max-width: 560px) {
      .stat { flex-direction: column; gap: 10px; padding: 14px; }
      .icon { width: 36px; height: 36px; }
      .value { font-size: 26px; }
      .hint { display: none; }
    }
  `],
})
export class StatCardComponent {
  readonly label = input.required<string>();
  readonly value = input.required<number | string>();
  readonly icon = input.required<string>();
  /** null renders a non-navigating tile */
  readonly link = input<string | null>('/');
  readonly tone = input<StatTone>('primary');
  readonly hint = input<string | null>(null);
}
