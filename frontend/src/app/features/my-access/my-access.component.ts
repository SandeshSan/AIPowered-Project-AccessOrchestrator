import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal, viewChild } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { switchMap } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { CurrentUserService } from '../../core/current-user.service';
import { LOADING, Loadable, loadable } from '../../core/loadable';
import { UserAccess } from '../../core/models';
import { RiskBadgeComponent } from '../../shared/risk-badge.component';
import { StatusChipComponent } from '../../shared/status-chip.component';

@Component({
  selector: 'app-my-access',
  standalone: true,
  imports: [
    DatePipe, FormsModule, MatTableModule, MatSortModule, MatFormFieldModule, MatInputModule, MatIconModule,
    MatProgressSpinnerModule, RiskBadgeComponent, StatusChipComponent,
  ],
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>My Access</h1>
          <p>Entitlements currently active for {{ currentUser.user()?.name ?? currentUser.userId() }}.</p>
        </div>
        <mat-form-field appearance="outline" subscriptSizing="dynamic" class="filter">
          <mat-icon matPrefix>search</mat-icon>
          <input matInput placeholder="Filter by application, name or code" [ngModel]="filter()"
                 (ngModelChange)="filter.set($event)" aria-label="Filter entitlements">
        </mat-form-field>
      </header>

      @if (state().error; as error) {
        <div class="error-banner" role="alert"><mat-icon>error_outline</mat-icon><span>{{ error }}</span></div>
      }

      <section class="panel table-panel">
        @if (state().loading) {
          <div class="loading"><mat-spinner diameter="32" /></div>
        }
        <div class="table-scroll">
          <table mat-table [dataSource]="dataSource" matSort matSortActive="application" matSortDirection="asc">
            <ng-container matColumnDef="application">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Application</th>
              <td mat-cell *matCellDef="let a">{{ a.entitlement.application }}</td>
            </ng-container>
            <ng-container matColumnDef="entitlement">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Entitlement</th>
              <td mat-cell *matCellDef="let a">
                <div class="name">{{ a.entitlement.entitlementName }}</div>
                <div class="mono muted">{{ a.entitlement.entitlementCode }}</div>
              </td>
            </ng-container>
            <ng-container matColumnDef="environment">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Environment</th>
              <td mat-cell *matCellDef="let a">{{ a.entitlement.environment }}</td>
            </ng-container>
            <ng-container matColumnDef="risk">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Risk</th>
              <td mat-cell *matCellDef="let a"><app-risk-badge [level]="a.entitlement.riskLevel" /></td>
            </ng-container>
            <ng-container matColumnDef="granted">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Granted</th>
              <td mat-cell *matCellDef="let a" class="nowrap">{{ a.grantedDate | date: 'mediumDate' }}</td>
            </ng-container>
            <ng-container matColumnDef="source">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>Source</th>
              <td mat-cell *matCellDef="let a">{{ a.source }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let a"><app-status-chip [status]="a.status" /></td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell empty-state" [attr.colspan]="columns.length">
                {{ filter() ? 'No entitlements match "' + filter() + '".' : 'No active access.' }}
              </td>
            </tr>
          </table>
        </div>
        <div class="footer muted">{{ count() }} active entitlement(s)</div>
      </section>
    </div>
  `,
  styles: [`
    .filter { width: 340px; max-width: 100%; }
    .table-panel { padding: 0; overflow: hidden; }
    .table-scroll { overflow-x: auto; }
    table { width: 100%; min-width: 720px; }
    .name { font-weight: 500; }
    .nowrap { white-space: nowrap; }
    .loading { display: grid; place-items: center; padding: 32px; }
    .footer { padding: 12px 16px; font-size: 12px; border-top: 1px solid var(--app-border); }
  `],
})
export class MyAccessComponent {
  private readonly api = inject(ApiService);
  protected readonly currentUser = inject(CurrentUserService);

  protected readonly columns = ['application', 'entitlement', 'environment', 'risk', 'granted', 'source', 'status'];
  protected readonly filter = signal('');
  protected readonly dataSource = new MatTableDataSource<UserAccess>([]);

  private readonly sort = viewChild(MatSort);

  protected readonly state = toSignal(
    toObservable(this.currentUser.userId).pipe(switchMap(id => loadable(this.api.myAccess(id)))),
    { initialValue: LOADING as Loadable<UserAccess[]> },
  );
  protected readonly count = computed(() => this.state().data?.length ?? 0);

  constructor() {
    this.dataSource.sortingDataAccessor = (a, column) => {
      switch (column) {
        case 'application': return a.entitlement.application;
        case 'entitlement': return a.entitlement.entitlementName;
        case 'environment': return a.entitlement.environment;
        case 'risk': return ['LOW', 'MEDIUM', 'HIGH'].indexOf(a.entitlement.riskLevel);
        case 'granted': return a.grantedDate ?? '';
        case 'source': return a.source;
        default: return '';
      }
    };
    this.dataSource.filterPredicate = (a, term) =>
      [a.entitlement.application, a.entitlement.entitlementName, a.entitlement.entitlementCode, a.entitlement.environment]
        .some(v => v?.toLowerCase().includes(term));

    effect(() => { this.dataSource.data = this.state().data ?? []; });
    effect(() => { this.dataSource.filter = this.filter().trim().toLowerCase(); });
    effect(() => {
      const sort = this.sort();
      if (sort) {
        this.dataSource.sort = sort;
      }
    });
  }
}
