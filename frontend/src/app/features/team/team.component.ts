import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { CurrentUserService } from '../../core/current-user.service';
import { LOADING, Loadable, loadable } from '../../core/loadable';
import { TeamMember } from '../../core/models';
import { RiskBadgeComponent } from '../../shared/risk-badge.component';

/** A manager's reportees (up to three levels down) with their projects and active access. Read-only. */
@Component({
  selector: 'app-team',
  standalone: true,
  imports: [
    DatePipe, FormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatIconModule, MatProgressSpinnerModule,
    RiskBadgeComponent,
  ],
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>My Team</h1>
          <p>People who report to you, their projects and their active access.</p>
        </div>
        <mat-form-field appearance="outline" subscriptSizing="dynamic" class="filter">
          <mat-icon matPrefix>search</mat-icon>
          <input matInput placeholder="Filter by name, project or entitlement" [ngModel]="filter()"
                 (ngModelChange)="filter.set($event)" aria-label="Filter team">
        </mat-form-field>
      </header>

      @if (state().error; as error) {
        <div class="error-banner" role="alert"><mat-icon>error_outline</mat-icon><span>{{ error }}</span></div>
      }
      @if (state().loading) {
        <div class="loading"><mat-spinner diameter="32" /></div>
      }

      <div class="list">
        @for (m of visible(); track m.user.userId) {
          <article class="panel member">
            <div class="head">
              <span class="avatar">{{ initials(m.user.name) }}</span>
              <div class="who">
                <h2>{{ m.user.name }} <span class="mono muted">{{ m.user.userId }}</span></h2>
                <div class="muted small">
                  {{ m.user.role }} · {{ m.user.department }}
                  · {{ m.level === 1 ? 'Direct report' : 'Reports to ' + m.user.managerName }}
                  @if (m.user.status !== 'ACTIVE') { · <span class="inactive">Inactive</span> }
                </div>
              </div>
              <div class="projects">
                @for (p of m.projects; track p.id) { <span class="chip">{{ p.projectName }}</span> }
                @empty { <span class="muted small">No projects</span> }
              </div>
            </div>

            <table class="access">
              <thead><tr><th>Entitlement</th><th>Application</th><th>Risk</th><th>Source</th><th>Granted</th></tr></thead>
              <tbody>
                @for (a of m.activeAccess; track a.entitlement.id) {
                  <tr>
                    <td><div>{{ a.entitlement.entitlementName }}</div><div class="mono muted">{{ a.entitlement.entitlementCode }}</div></td>
                    <td>{{ a.entitlement.application }}</td>
                    <td><app-risk-badge [level]="a.entitlement.riskLevel" /></td>
                    <td>{{ a.source }}</td>
                    <td class="nowrap">{{ a.grantedDate | date: 'mediumDate' }}</td>
                  </tr>
                } @empty {
                  <tr><td colspan="5" class="empty-state">No active access.</td></tr>
                }
              </tbody>
            </table>
          </article>
        } @empty {
          @if (!state().loading) {
            <div class="panel empty">
              <mat-icon>groups</mat-icon>
              <p>{{ filter() ? 'Nobody matches "' + filter() + '".' : 'Nobody reports to you.' }}</p>
            </div>
          }
        }
      </div>
      <p class="muted small hint">To add or remove people from projects, ask the <a routerLink="/assistant">Access Assistant</a>.</p>
    </div>
  `,
  styles: [`
    .filter { width: 340px; max-width: 100%; }
    .loading { display: grid; place-items: center; padding: 48px; }
    .list { display: flex; flex-direction: column; gap: 16px; }
    .member { display: flex; flex-direction: column; gap: 14px; }
    .head { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
    .avatar { width: 40px; height: 40px; border-radius: 50%; display: grid; place-items: center; flex: none;
      background: var(--app-primary-soft); color: var(--app-primary); font-weight: 600; font-size: 14px; }
    .who { flex: 1; min-width: 200px; }
    h2 { margin: 0; font-size: 16px; font-weight: 600; }
    h2 .mono { font-weight: 400; margin-left: 4px; }
    .small { font-size: 12px; }
    .inactive { color: var(--app-bad); font-weight: 600; }
    .projects { display: flex; flex-wrap: wrap; gap: 6px; }
    .chip { font-size: 12px; font-weight: 500; padding: 2px 10px; border-radius: 999px;
      background: var(--app-primary-soft); color: var(--app-primary); }
    .access { width: 100%; border-collapse: collapse; font-size: 13px; }
    .access th { text-align: left; font-weight: 500; color: var(--app-muted); font-size: 12px; padding: 8px;
      border-bottom: 1px solid var(--app-border); background: var(--app-bg); }
    .access td { padding: 9px 8px; border-bottom: 1px solid var(--app-border); vertical-align: top; }
    .access tr:last-child td { border-bottom: none; }
    .nowrap { white-space: nowrap; }
    @media (max-width: 640px) { .access th:nth-child(2), .access td:nth-child(2),
      .access th:nth-child(4), .access td:nth-child(4) { display: none; } }
    .empty { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 48px 20px; color: var(--app-muted); }
    .empty mat-icon { font-size: 40px; width: 40px; height: 40px; }
    .hint { margin-top: 16px; }
  `],
})
export class TeamComponent {
  private readonly api = inject(ApiService);
  private readonly currentUser = inject(CurrentUserService);

  protected readonly filter = signal('');
  protected readonly state = toSignal(
    toObservable(this.currentUser.userId).pipe(switchMap(() => loadable(this.api.team()))),
    { initialValue: LOADING as Loadable<TeamMember[]> },
  );

  protected readonly visible = computed(() => {
    const term = this.filter().trim().toLowerCase();
    const team = this.state().data ?? [];
    if (!term) {
      return team;
    }
    return team.filter(m => [m.user.name, m.user.userId, ...m.projects.map(p => p.projectName),
      ...m.activeAccess.flatMap(a => [a.entitlement.entitlementCode, a.entitlement.entitlementName])]
      .some(v => v.toLowerCase().includes(term)));
  });

  protected initials(name: string): string {
    return name.split(/\s+/).map(p => p[0]).join('').slice(0, 2).toUpperCase();
  }
}
