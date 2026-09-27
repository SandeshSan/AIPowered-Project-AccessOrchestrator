import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';

import { CurrentUserService } from '../../core/current-user.service';
import { User } from '../../core/models';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  const mei = { userId: 'NT10020', name: 'Mei', admin: false } as User;
  let fixture: ComponentFixture<LoginComponent>;
  let navigate: jasmine.Spy;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    }).compileComponents();
    navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
  });

  afterEach(() => TestBed.inject(CurrentUserService).signOut());

  async function signIn(userId: string, password: string) {
    const el: HTMLElement = fixture.nativeElement;
    const [id, pw] = Array.from(el.querySelectorAll('input')) as HTMLInputElement[];
    id.value = userId;
    id.dispatchEvent(new Event('input'));
    pw.value = password;
    pw.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    (el.querySelector('button[type=submit]') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  it('checks the credentials with Basic auth and signs in', async () => {
    fixture.componentRef.setInput('returnUrl', '/team');
    await signIn(' NT10020 ', 'secret');
    const req = TestBed.inject(HttpTestingController).expectOne('/api/me');
    expect(req.request.headers.get('Authorization')).toBe(`Basic ${btoa('NT10020:secret')}`);
    req.flush(mei);

    const currentUser = TestBed.inject(CurrentUserService);
    expect(currentUser.userId()).toBe('NT10020');
    expect(currentUser.session()?.token).toBe(btoa('NT10020:secret'));
    expect(navigate).toHaveBeenCalledWith('/team');
  });

  it('shows an error for wrong credentials and stays signed out', async () => {
    await signIn('NT10020', 'wrong');
    TestBed.inject(HttpTestingController).expectOne('/api/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain('Incorrect employee ID or password');
    expect(TestBed.inject(CurrentUserService).signedIn()).toBeFalse();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('ignores return URLs that leave the app', async () => {
    fixture.componentRef.setInput('returnUrl', '//evil.example');
    await signIn('NT10020', 'secret');
    TestBed.inject(HttpTestingController).expectOne('/api/me').flush(mei);
    expect(navigate).toHaveBeenCalledWith('/dashboard');
  });
});
