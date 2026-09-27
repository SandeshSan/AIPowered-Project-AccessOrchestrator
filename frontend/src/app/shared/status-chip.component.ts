import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { statusLabel, statusTone } from '../core/format';

/** Coloured pill for request / item / access statuses. */
@Component({
  selector: 'app-status-chip',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="chip" [attr.data-tone]="tone()">{{ label() || text() }}</span>`,
  styles: [`
    .chip {
      display: inline-flex; align-items: center; gap: 4px; padding: 2px 10px; border-radius: 999px;
      font-size: 12px; font-weight: 600; line-height: 20px; white-space: nowrap;
      background: #eef1f5; color: var(--app-muted);
    }
    .chip[data-tone='ok'] { background: var(--app-ok-soft); color: var(--app-ok); }
    .chip[data-tone='warn'] { background: var(--app-warn-soft); color: var(--app-warn); }
    .chip[data-tone='bad'] { background: var(--app-bad-soft); color: var(--app-bad); }
    .chip[data-tone='info'] { background: var(--app-info-soft); color: var(--app-info); }
  `],
})
export class StatusChipComponent {
  readonly status = input.required<string>();
  /** Optional display text overriding the derived label. */
  readonly label = input<string | null>(null);

  protected readonly tone = computed(() => statusTone(this.status()));
  protected readonly text = computed(() => statusLabel(this.status()));
}
