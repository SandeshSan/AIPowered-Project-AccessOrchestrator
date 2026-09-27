import { Component, computed, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { forkJoin, switchMap } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { CurrentUserService } from '../../core/current-user.service';
import { LOADING, Loadable, loadable } from '../../core/loadable';
import { Project, ProjectMembership } from '../../core/models';
import { StatusChipComponent } from '../../shared/status-chip.component';

interface ProjectRow {
  project: Project;
  membership: ProjectMembership | null;
}

@Component({
  selector: 'app-projects',
  standalone: true,
  imports: [RouterLink, MatIconModule, MatProgressBarModule, MatProgressSpinnerModule, StatusChipComponent],
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>Projects</h1>
          <p>Each project has a standard access profile. Open a project to compare it with your access.</p>
        </div>
      </header>

      @if (state().error; as error) {
        <div class="error-banner" role="alert"><mat-icon>error_outline</mat-icon><span>{{ error }}</span></div>
      }
      @if (state().loading) {
        <div class="loading"><mat-spinner diameter="32" /></div>
      }

      <div class="grid">
        @for (row of rows(); track row.project.id) {
          <a class="panel project" [routerLink]="['/projects', row.project.id]">
            <div class="head">
              <span class="icon"><mat-icon>folder_open</mat-icon></span>
              <div class="titles">
                <h2>{{ row.project.projectName }}</h2>
                <span class="mono muted">{{ row.project.projectCode }}</span>
              </div>
              @if (row.membership) {
                <span class="member">Member · {{ row.membership.projectRole }}</span>
              }
            </div>
            <p class="desc">{{ row.project.description }}</p>
            @if (row.membership; as m) {
              <mat-progress-bar mode="determinate" [value]="m.access.coveragePercent"
                                [attr.aria-label]="'Access coverage ' + m.access.coveragePercent + '%'" />
              <div class="foot">
                <span class="muted">{{ m.access.grantedCount }} of {{ m.access.requiredCount }} required entitlements</span>
                @if (m.access.fullyProvisioned) {
                  <app-status-chip status="PROVISIONED" label="Fully provisioned" />
                } @else {
                  <app-status-chip status="MISSING" [label]="m.access.missingCount + ' missing'" />
                }
              </div>
            } @else {
              <div class="foot"><span class="muted">You're not assigned to this project</span>
                <span class="view">View profile <mat-icon>arrow_forward</mat-icon></span></div>
            }
          </a>
        }
      </div>
    </div>
  `,
  styles: [`
    .loading { display: grid; place-items: center; padding: 48px; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; }
    @media (max-width: 400px) { .grid { grid-template-columns: 1fr; } }
    .project { display: flex; flex-direction: column; gap: 12px; text-decoration: none; color: inherit;
      transition: border-color .15s, box-shadow .15s; }
    .project:hover, .project:focus-visible { border-color: #c5cae9; box-shadow: 0 2px 10px rgba(21, 28, 44, .06); outline: none; }
    .head { display: flex; align-items: center; gap: 12px; }
    .icon { width: 40px; height: 40px; border-radius: 10px; display: grid; place-items: center;
      background: var(--app-primary-soft); color: var(--app-primary); flex: none; }
    .titles { flex: 1; min-width: 0; }
    h2 { margin: 0; font-size: 16px; font-weight: 600; }
    .member { font-size: 12px; font-weight: 600; color: var(--app-primary); background: var(--app-primary-soft);
      padding: 2px 10px; border-radius: 999px; white-space: nowrap; }
    .desc { margin: 0; color: var(--app-muted); font-size: 13px; line-height: 1.5; }
    .foot { display: flex; justify-content: space-between; align-items: center; gap: 8px; font-size: 13px; flex-wrap: wrap; }
    .view { display: inline-flex; align-items: center; gap: 4px; color: var(--app-primary); font-weight: 500; }
    .view mat-icon { font-size: 16px; width: 16px; height: 16px; }
  `],
})
export class ProjectsComponent {
  private readonly api = inject(ApiService);
  private readonly currentUser = inject(CurrentUserService);

  protected readonly state = toSignal(
    toObservable(this.currentUser.userId).pipe(
      switchMap(id => loadable(forkJoin({ projects: this.api.projects(), mine: this.api.myProjects(id) }))),
    ),
    { initialValue: LOADING as Loadable<{ projects: Project[]; mine: ProjectMembership[] }> },
  );

  /** My projects first, then the rest alphabetically. */
  protected readonly rows = computed<ProjectRow[]>(() => {
    const data = this.state().data;
    if (!data) {
      return [];
    }
    const rows = data.projects.map(project => ({
      project,
      membership: data.mine.find(m => m.project.id === project.id) ?? null,
    }));
    return rows.sort((a, b) => Number(!!b.membership) - Number(!!a.membership)
      || a.project.projectName.localeCompare(b.project.projectName));
  });
}
