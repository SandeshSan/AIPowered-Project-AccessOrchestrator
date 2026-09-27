import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { LifecycleStage } from '../core/models';

/**
 * Horizontal request lifecycle: Request Created -> Pending Approval -> Approved -> Provisioning -> Provisioned
 * (or ... -> Rejected). Stages and their states come from the backend.
 */
@Component({
  selector: 'app-lifecycle-stepper',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule],
  template: `
    <ol class="stepper" [class.compact]="compact()" aria-label="Request lifecycle">
      @for (stage of stages(); track stage.stage) {
        <li class="step" [attr.data-state]="stage.state" [attr.aria-current]="stage.state === 'CURRENT' ? 'step' : null">
          <span class="dot">
            @switch (stage.state) {
              @case ('COMPLETED') { <mat-icon>check</mat-icon> }
              @case ('REJECTED') { <mat-icon>close</mat-icon> }
              @case ('FAILED') { <mat-icon>priority_high</mat-icon> }
              @case ('CANCELLED') { <mat-icon>remove</mat-icon> }
            }
          </span>
          <span class="label">{{ stage.label }}</span>
        </li>
      }
    </ol>
  `,
  styles: [`
    :host { display: block; }
    .stepper { list-style: none; margin: 0; padding: 0; display: flex; }
    .step { flex: 1; display: flex; flex-direction: column; align-items: center; position: relative; min-width: 0; }
    .step:not(:first-child)::before {
      content: ''; position: absolute; top: 13px; right: 50%; width: 100%; height: 2px;
      background: var(--app-border); z-index: 0;
    }
    .step[data-state='COMPLETED']::before, .step[data-state='CURRENT']::before,
    .step[data-state='REJECTED']::before, .step[data-state='FAILED']::before { background: var(--app-ok); }
    .dot {
      position: relative; z-index: 1; width: 28px; height: 28px; border-radius: 50%; display: grid; place-items: center;
      border: 2px solid var(--app-border); background: var(--app-surface); color: #fff;
    }
    .dot mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .step[data-state='COMPLETED'] .dot { background: var(--app-ok); border-color: var(--app-ok); }
    .step[data-state='CURRENT'] .dot { border-color: var(--app-primary); box-shadow: 0 0 0 4px var(--app-primary-soft); }
    .step[data-state='CURRENT'] .dot::after { content: ''; width: 10px; height: 10px; border-radius: 50%; background: var(--app-primary); }
    .step[data-state='REJECTED'] .dot, .step[data-state='FAILED'] .dot { background: var(--app-bad); border-color: var(--app-bad); }
    .label { margin-top: 8px; font-size: 12px; font-weight: 500; text-align: center; color: var(--app-text); padding: 0 4px; }
    .step[data-state='UPCOMING'] .label { color: var(--app-muted); font-weight: 400; }
    .step[data-state='CURRENT'] .label { color: var(--app-primary); font-weight: 600; }
    .step[data-state='REJECTED'] .label, .step[data-state='FAILED'] .label { color: var(--app-bad); font-weight: 600; }
    .step[data-state='CANCELLED'] .dot { background: var(--app-muted); border-color: var(--app-muted); }
    .step[data-state='CANCELLED'] .label { color: var(--app-muted); font-weight: 600; }

    .compact .dot { width: 18px; height: 18px; border-width: 2px; }
    .compact .dot mat-icon { font-size: 12px; width: 12px; height: 12px; }
    .compact .step:not(:first-child)::before { top: 8px; }
    .compact .step[data-state='CURRENT'] .dot::after { width: 6px; height: 6px; }
    .compact .step[data-state='CURRENT'] .dot { box-shadow: 0 0 0 3px var(--app-primary-soft); }
    .compact .label { font-size: 11px; margin-top: 4px; }

    @media (max-width: 560px) { .label { font-size: 10px; } }
  `],
})
export class LifecycleStepperComponent {
  readonly stages = input.required<LifecycleStage[]>();
  readonly compact = input(false);
}
