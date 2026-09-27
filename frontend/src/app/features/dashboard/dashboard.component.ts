import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { CurrentUserService } from '../../core/current-user.service';
import { LOADING, Loadable, loadable } from '../../core/loadable';
import { Dashboard } from '../../core/models';
import { LifecycleStepperComponent } from '../../shared/lifecycle-stepper.component';
import { RiskBadgeComponent } from '../../shared/risk-badge.component';
import { StatCardComponent } from '../../shared/stat-card.component';
import { StatusChipComponent } from '../../shared/status-chip.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    DatePipe, NgTemplateOutlet, RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule, MatProgressSpinnerModule,
    StatCardComponent, StatusChipComponent, LifecycleStepperComponent, RiskBadgeComponent,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  private readonly api = inject(ApiService);
  private readonly currentUser = inject(CurrentUserService);
  protected readonly isAdmin = this.currentUser.isAdmin;

  protected readonly state = toSignal(
    toObservable(this.currentUser.userId).pipe(switchMap(id => loadable(this.api.dashboard(id)))),
    { initialValue: LOADING as Loadable<Dashboard> },
  );

  protected readonly d = computed(() => this.state().data);

  /** Missing access not yet covered by an in-progress request, per project. */
  protected readonly gaps = computed(() =>
    (this.d()?.projects ?? []).filter(p => p.access.missingCount > 0));

  protected readonly outstanding = computed(() => {
    const d = this.d();
    return d ? d.missingAccessCount - d.missingAlreadyRequestedCount : 0;
  });

  protected greeting(): string {
    const h = new Date().getHours();
    return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening';
  }

  protected assistantPrompt(projectName: string): string {
    return `I just joined the ${projectName} project. Can you get me the access I need?`;
  }
}
