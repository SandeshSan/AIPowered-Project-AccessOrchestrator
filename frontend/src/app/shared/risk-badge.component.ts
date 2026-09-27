import { TitleCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { RiskLevel } from '../core/models';

@Component({
  selector: 'app-risk-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TitleCasePipe],
  template: `<span class="risk" [attr.data-level]="level()">{{ level() | titlecase }}</span>`,
  styles: [`
    .risk { font-size: 12px; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; }
    .risk::before { content: ''; width: 8px; height: 8px; border-radius: 50%; background: currentColor; }
    .risk[data-level='LOW'] { color: var(--app-ok); }
    .risk[data-level='MEDIUM'] { color: var(--app-warn); }
    .risk[data-level='HIGH'] { color: var(--app-bad); }
  `],
})
export class RiskBadgeComponent {
  readonly level = input.required<RiskLevel>();
}
