import { DOCUMENT } from '@angular/common';
import { Injectable, InjectionToken, NgZone, effect, inject, untracked } from '@angular/core';
import { MatSnackBar, MatSnackBarRef, TextOnlySnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';

import { CurrentUserService } from './current-user.service';

export interface IdleTimeoutConfig {
  /** Signed out after this long without keyboard, mouse, touch or scroll input. */
  timeoutMs: number;
  /** A warning appears this long before the sign-out. */
  warningMs: number;
  /** How often the idle time is checked. */
  checkEveryMs: number;
  /** Clock, replaceable in tests. */
  now: () => number;
}

export const IDLE_TIMEOUT_CONFIG = new InjectionToken<IdleTimeoutConfig>('IDLE_TIMEOUT_CONFIG', {
  providedIn: 'root',
  factory: () => ({ timeoutMs: 30 * 60_000, warningMs: 60_000, checkEveryMs: 5_000, now: () => Date.now() }),
});

const STORAGE_KEY = 'pao.lastActivity';
const ACTIVITY_EVENTS = ['pointerdown', 'pointermove', 'keydown', 'wheel', 'touchstart', 'scroll'] as const;

/**
 * Signs the user out after a period without activity (30 minutes by default), with a warning shortly before.
 * Idle time is measured against the clock rather than with a countdown timer, so time spent asleep (laptop lid
 * closed, background tab) counts too. The last activity time is kept in sessionStorage so a reload doesn't
 * reset it. Frontend only: the Basic credential itself does not expire on the server.
 */
@Injectable({ providedIn: 'root' })
export class IdleTimeoutService {
  private readonly config = inject(IDLE_TIMEOUT_CONFIG);
  private readonly currentUser = inject(CurrentUserService);
  private readonly router = inject(Router);
  private readonly zone = inject(NgZone);
  private readonly snackBar = inject(MatSnackBar);
  private readonly document = inject(DOCUMENT);

  private lastActivity = 0;
  private lastStored = 0;
  private interval: ReturnType<typeof setInterval> | null = null;
  private warning: MatSnackBarRef<TextOnlySnackBar> | null = null;
  private readonly onActivity = () => this.recordActivity();
  private readonly onVisible = () => {
    if (this.document.visibilityState === 'visible') {
      this.check();
    }
  };

  constructor() {
    effect(() => {
      const signedIn = this.currentUser.signedIn();
      untracked(() => (signedIn ? this.start() : this.stop()));
    }, { allowSignalWrites: true }); // an overdue check on start signs out immediately
  }

  /** Signs out if the idle limit has passed, or shows the warning when it is close. Runs periodically. */
  check(): void {
    if (!this.interval) {
      return;
    }
    const idle = this.config.now() - this.lastActivity;
    if (idle >= this.config.timeoutMs) {
      this.zone.run(() => this.signOutForInactivity());
    } else if (idle >= this.config.timeoutMs - this.config.warningMs && !this.warning) {
      this.zone.run(() => this.showWarning());
    }
  }

  private start(): void {
    if (this.interval) {
      return;
    }
    const stored = readStored();
    this.lastActivity = stored ?? this.config.now();
    this.zone.runOutsideAngular(() => {
      // Activity events fire constantly; keep them out of change detection
      ACTIVITY_EVENTS.forEach(e => this.document.addEventListener(e, this.onActivity, { capture: true, passive: true }));
      this.document.addEventListener('visibilitychange', this.onVisible);
      this.interval = setInterval(() => this.check(), this.config.checkEveryMs);
    });
    if (stored === null) {
      this.store(this.lastActivity);
    }
    // A reload after a long absence signs out straight away
    this.check();
  }

  private stop(): void {
    ACTIVITY_EVENTS.forEach(e => this.document.removeEventListener(e, this.onActivity, { capture: true }));
    this.document.removeEventListener('visibilitychange', this.onVisible);
    if (this.interval) {
      clearInterval(this.interval);
      this.interval = null;
    }
    this.dismissWarning();
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      /* storage unavailable */
    }
  }

  private recordActivity(): void {
    const now = this.config.now();
    this.lastActivity = now;
    if (now - this.lastStored > 5_000) {
      this.store(now); // throttled: pointermove can fire hundreds of times a second
    }
    if (this.warning) {
      this.zone.run(() => this.dismissWarning());
    }
  }

  private showWarning(): void {
    const minutes = Math.max(1, Math.round(this.config.warningMs / 60_000));
    this.warning = this.snackBar.open(
      `You'll be signed out in about ${minutes} minute${minutes === 1 ? '' : 's'} because of inactivity.`,
      'Stay signed in',
      { panelClass: 'idle-warning' },
    );
    this.warning.onAction().subscribe(() => this.recordActivity());
    this.warning.afterDismissed().subscribe(() => (this.warning = null));
  }

  private dismissWarning(): void {
    this.warning?.dismiss();
    this.warning = null;
  }

  private signOutForInactivity(): void {
    // Right after a page load the router hasn't navigated yet, so take the page from the address bar
    const url = this.router.navigated
      ? this.router.url
      : this.document.location.pathname + this.document.location.search;
    const returnUrl = url.startsWith('/login') ? undefined : url;
    this.currentUser.signOut(); // stops this watcher via the effect
    this.router.navigate(['/login'], { queryParams: { reason: 'idle', ...(returnUrl ? { returnUrl } : {}) } });
  }

  private store(time: number): void {
    this.lastStored = time;
    try {
      sessionStorage.setItem(STORAGE_KEY, String(time));
    } catch {
      /* storage unavailable: idle time still counts within this page */
    }
  }
}

function readStored(): number | null {
  try {
    const value = Number(sessionStorage.getItem(STORAGE_KEY));
    return Number.isFinite(value) && value > 0 ? value : null;
  } catch {
    return null;
  }
}
