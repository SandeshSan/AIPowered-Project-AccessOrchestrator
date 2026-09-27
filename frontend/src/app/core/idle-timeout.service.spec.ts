import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';

import { CurrentUserService } from './current-user.service';
import { IDLE_TIMEOUT_CONFIG, IdleTimeoutService } from './idle-timeout.service';
import { User } from './models';

describe('IdleTimeoutService', () => {
  const MINUTE = 60_000;
  let now: number;
  let currentUser: CurrentUserService;
  let navigate: jasmine.Spy;

  beforeEach(() => {
    now = Date.UTC(2026, 8, 27, 9, 0); // realistic epoch time, so "45 minutes ago" is still positive
    sessionStorage.removeItem('pao.lastActivity');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        // checkEveryMs is huge so only explicit check() calls run
        { provide: IDLE_TIMEOUT_CONFIG, useValue: { timeoutMs: 30 * MINUTE, warningMs: MINUTE, checkEveryMs: 1e9, now: () => now } },
      ],
    });
    currentUser = TestBed.inject(CurrentUserService);
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
  });

  afterEach(() => {
    currentUser.signOut();
    TestBed.flushEffects();
  });

  function signedInIdleService(): IdleTimeoutService {
    currentUser.signIn({ userId: 'NT10036', name: 'John' } as User, 'token');
    const service = TestBed.inject(IdleTimeoutService);
    TestBed.flushEffects();
    return service;
  }

  it('signs out after 30 minutes without activity and says why', async () => {
    await TestBed.inject(Router).navigateByUrl('/');
    const service = signedInIdleService();

    now += 29 * MINUTE + 59_000;
    service.check();
    expect(currentUser.signedIn()).toBeTrue();

    now += 1_000;
    service.check();
    expect(currentUser.signedIn()).toBeFalse();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'idle', returnUrl: '/' } });
  });

  it('keyboard, mouse and scrolling reset the timer', () => {
    const service = signedInIdleService();

    now += 20 * MINUTE;
    document.dispatchEvent(new KeyboardEvent('keydown'));
    now += 20 * MINUTE;
    document.dispatchEvent(new PointerEvent('pointerdown'));
    now += 20 * MINUTE;
    service.check();

    expect(currentUser.signedIn()).toBeTrue();
  });

  it('warns a minute before, and activity dismisses the warning', () => {
    const service = signedInIdleService();
    const snackBar = TestBed.inject(MatSnackBar);
    const open = spyOn(snackBar, 'open').and.callThrough();

    now += 29 * MINUTE;
    service.check();
    expect(open).toHaveBeenCalledOnceWith(
      "You'll be signed out in about 1 minute because of inactivity.", 'Stay signed in', jasmine.any(Object));
    service.check();
    expect(open).toHaveBeenCalledTimes(1); // not repeated every check

    const dismiss = spyOn(snackBar._openedSnackBarRef!, 'dismiss').and.callThrough();
    document.dispatchEvent(new PointerEvent('pointermove'));
    expect(dismiss).toHaveBeenCalled();
    now += 29 * MINUTE;
    service.check();
    expect(currentUser.signedIn()).toBeTrue();
  });

  it('counts time away across a reload (e.g. laptop asleep)', () => {
    sessionStorage.setItem('pao.lastActivity', String(now - 45 * MINUTE));
    signedInIdleService();

    expect(currentUser.signedIn()).toBeFalse();
    // Before the router's first navigation the page comes from the address bar
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'idle', returnUrl: location.pathname + location.search } });
  });

  it('does nothing while signed out, and forgets the last activity on sign-out', () => {
    const service = signedInIdleService();
    expect(sessionStorage.getItem('pao.lastActivity')).toBe(String(now));

    currentUser.signOut();
    TestBed.flushEffects();
    expect(sessionStorage.getItem('pao.lastActivity')).toBeNull();

    now += 60 * MINUTE;
    service.check();
    expect(navigate).not.toHaveBeenCalled();
  });
});
