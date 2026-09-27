import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, numberAttribute } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { combineLatest, forkJoin, switchMap } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { CurrentUserService } from '../../core/current-user.service';
import { LOADING, Loadable, loadable } from '../../core/loadable';
import { AccessComparison, Project, ProjectAccess } from '../../core/models';
import { RiskBadgeComponent } from '../../shared/risk-badge.component';
import { StatusChipComponent } from '../../shared/status-chip.component';

interface Detail {
  project: Project;
  profile: ProjectAccess[];
  comparison: AccessComparison;
}

@Component({
  selector: 'app-project-detail',
  standalone: true,
  imports: [
    DatePipe, RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule, MatProgressSpinnerModule,
    StatusChipComponent, RiskBadgeComponent,
  ],
  templateUrl: './project-detail.component.html',
  styleUrl: './project-detail.component.scss',
})
export class ProjectDetailComponent {
  private readonly api = inject(ApiService);
  private readonly currentUser = inject(CurrentUserService);

  /** Route param :id (component input binding). */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly state = toSignal(
    combineLatest([toObservable(this.id), toObservable(this.currentUser.userId)]).pipe(
      switchMap(([id, userId]) => loadable(forkJoin({
        project: this.api.project(id),
        profile: this.api.projectAccessProfile(id),
        comparison: this.api.compare(userId, id),
      }))),
    ),
    { initialValue: LOADING as Loadable<Detail> },
  );

  protected readonly d = computed(() => this.state().data);
  protected readonly optional = computed(() => (this.d()?.profile ?? []).filter(p => !p.required));

  protected assistantPrompt(projectName: string): string {
    return `I just joined the ${projectName} project. Can you get me the access I need?`;
  }
}
