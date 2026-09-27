import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';

import { AppComponent } from './app.component';
import { CurrentUserService } from './core/current-user.service';
import { User } from './core/models';

describe('AppComponent', () => {
  const john: User = { userId: 'NT10036', name: 'John', role: 'Developer', email: '', department: '', status: 'ACTIVE', admin: false };
  const grace: User = { userId: 'NT10001', name: 'Grace', role: 'Access Administrator', email: '', department: '', status: 'ACTIVE', admin: true };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    }).compileComponents();
  });

  afterEach(() => TestBed.inject(CurrentUserService).signOut());

  function render(user: User, reports: { userId: string }[] = []) {
    TestBed.inject(CurrentUserService).signIn(user, 'token');
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('/api/me/capabilities').flush({ admin: user.admin, reports });
    fixture.detectChanges();
    return fixture;
  }

  function navLabels(el: HTMLElement): (string | undefined)[] {
    return Array.from(el.querySelectorAll('nav .nav-item > span') as NodeListOf<HTMLElement>).map(s => s.textContent?.trim());
  }

  it('shows only the login outlet when signed out', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('mat-sidenav-container')).toBeNull();
    TestBed.inject(HttpTestingController).expectNone('/api/me/capabilities');
  });

  it('hides Projects from employees', () => {
    const fixture = render(john);
    expect(navLabels(fixture.nativeElement)).toEqual(['Dashboard', 'Access Assistant', 'My Access', 'Access Requests']);
    expect(fixture.nativeElement.querySelector('.avatar').textContent.trim()).toBe('J');
  });

  it('shows Projects to admins', () => {
    const fixture = render(grace, [{ userId: 'NT10020' }]);
    expect(navLabels(fixture.nativeElement)).toEqual(
      ['Dashboard', 'Access Assistant', 'My Access', 'Access Requests', 'My Team', 'Projects']);
    expect(TestBed.inject(CurrentUserService).isAdmin()).toBeTrue();
  });

  it('shows My Team only to people with reportees', () => {
    const fixture = render(john, [{ userId: 'NT10042' }]);
    expect(navLabels(fixture.nativeElement)).toContain('My Team');
    expect(navLabels(fixture.nativeElement)).not.toContain('Projects');
  });

  it('signs out to the login page', () => {
    const fixture = render(john);
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    (fixture.componentInstance as unknown as { signOut(): void }).signOut();
    fixture.detectChanges();
    expect(TestBed.inject(CurrentUserService).signedIn()).toBeFalse();
    expect(navigate).toHaveBeenCalledWith('/login');
    expect(fixture.nativeElement.querySelector('mat-sidenav-container')).toBeNull();
  });
});
