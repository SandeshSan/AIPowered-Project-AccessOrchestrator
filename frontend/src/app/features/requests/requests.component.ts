import { DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { Subject, forkJoin, merge, switchMap, timer } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { CurrentUserService } from '../../core/current-user.service';
import { errorMessage, isOpen, requestStatusLabel } from '../../core/format';
import { Loadable, loadable } from '../../core/loadable';
import { AccessRequest } from '../../core/models';
import { LifecycleStepperComponent } from '../../shared/lifecycle-stepper.component';
import { StatusChipComponent } from '../../shared/status-chip.component';

type Filter = 'all' | 'open' | 'closed' | 'startedByMe';

interface Lists {
  mine: AccessRequest[];
  /** Requests this user started for other people, e.g. removals as a manager. */
  startedByMe: AccessRequest[];
}

const POLL_MS = 3000;

@Component({
  selector: 'app-requests',
  standalone: true,
  imports: [
    DatePipe, RouterLink, MatButtonModule, MatButtonToggleModule, MatIconModule, MatProgressSpinnerModule,
    MatSnackBarModule, MatTooltipModule, StatusChipComponent, LifecycleStepperComponent,
  ],
  templateUrl: './requests.component.html',
  styleUrl: './requests.component.scss',
})
export class RequestsComponent {
  private readonly api = inject(ApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly currentUser = inject(CurrentUserService);

  protected readonly filter = signal<Filter>('all');
  protected readonly state = signal<Loadable<Lists>>({ loading: true });
  protected readonly busy = signal<string | null>(null);

  private readonly refresh$ = new Subject<void>();

  protected readonly requests = computed(() => this.state().data?.mine ?? []);
  protected readonly startedByMe = computed(() => this.state().data?.startedByMe ?? []);
  protected readonly openCount = computed(() => this.requests().filter(r => isOpen(r.status)).length);
  private readonly anyOpen = computed(() => [...this.requests(), ...this.startedByMe()].some(r => isOpen(r.status)));
  protected readonly visible = computed(() => {
    const f = this.filter();
    if (f === 'startedByMe') {
      return this.startedByMe();
    }
    return this.requests().filter(r =>
      f === 'all' || (f === 'open' ? isOpen(r.status) || r.status === 'DRAFT' : !isOpen(r.status) && r.status !== 'DRAFT'));
  });

  constructor() {
    const destroyRef = inject(DestroyRef);

    // Load on user change (with spinner) and on manual refresh (silently)
    toObservable(this.currentUser.userId).pipe(
      switchMap(id => {
        const load = () => forkJoin({ mine: this.api.requests(id), startedByMe: this.api.requestsCreatedBy(id) });
        return merge(loadable(load()), this.refresh$.pipe(switchMap(() => loadable(load(), false))));
      }),
      takeUntilDestroyed(destroyRef),
    ).subscribe(s => this.state.set(s));

    // Live lifecycle: poll while anything is still moving through the IGA (the backend syncs with the IGA)
    effect(onCleanup => {
      if (!this.anyOpen()) {
        return;
      }
      const sub = timer(POLL_MS, POLL_MS).subscribe(() => this.refresh$.next());
      onCleanup(() => sub.unsubscribe());
    });
  }

  protected sync(r: AccessRequest): void {
    this.busy.set(r.requestId);
    this.api.syncRequest(r.requestId).subscribe({
      next: () => { this.busy.set(null); this.refresh$.next(); },
      error: err => { this.busy.set(null); this.snackBar.open(errorMessage(err), 'Dismiss', { duration: 6000 }); },
    });
  }

  protected submit(r: AccessRequest): void {
    this.busy.set(r.requestId);
    this.api.submitRequest(r.requestId).subscribe({
      next: updated => {
        this.busy.set(null);
        this.snackBar.open(`Submitted for approval as ${updated.igaRequestId}`, undefined, { duration: 4000 });
        this.refresh$.next();
      },
      error: err => { this.busy.set(null); this.snackBar.open(errorMessage(err), 'Dismiss', { duration: 6000 }); },
    });
  }

  protected isOpen = isOpen;
  protected statusLabel = requestStatusLabel;
}
