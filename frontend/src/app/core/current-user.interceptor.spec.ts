import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { currentUserInterceptor } from './current-user.interceptor';
import { CurrentUserService, basicToken } from './current-user.service';
import { User } from './models';

describe('currentUserInterceptor', () => {
  const asha = { userId: 'NT10042', name: 'Asha' } as User;
  let http: HttpClient;
  let backend: HttpTestingController;
  let currentUser: CurrentUserService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([currentUserInterceptor])),
        provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    currentUser = TestBed.inject(CurrentUserService);
  });

  afterEach(() => {
    backend.verify();
    currentUser.signOut();
  });

  it("sends the signed-in user's Basic credential on API calls", () => {
    currentUser.signIn(asha, basicToken('NT10042', 'secret'));
    http.get('/api/me').subscribe();
    expect(backend.expectOne('/api/me').request.headers.get('Authorization')).toBe(`Basic ${btoa('NT10042:secret')}`);
  });

  it('sends nothing when signed out, and never the old demo header', () => {
    http.get('/api/me').subscribe({ error: () => undefined });
    const req = backend.expectOne('/api/me').request;
    expect(req.headers.has('Authorization')).toBeFalse();
    expect(req.headers.has('X-User-Id')).toBeFalse();
  });

  it('leaves non-API requests and explicit login attempts alone', () => {
    currentUser.signIn(asha, 'stored');
    http.get('/assets/logo.svg').subscribe();
    expect(backend.expectOne('/assets/logo.svg').request.headers.has('Authorization')).toBeFalse();
    http.get('/api/me', { headers: { Authorization: 'Basic attempt' } }).subscribe();
    expect(backend.expectOne('/api/me').request.headers.get('Authorization')).toBe('Basic attempt');
  });

  it('signs out and goes to the login page when the credential stops working', () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    currentUser.signIn(asha, 'stale');
    http.get('/api/me').subscribe({ error: () => undefined });
    backend.expectOne('/api/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(currentUser.signedIn()).toBeFalse();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { expired: 1 } });
  });

  it('encodes non-ASCII passwords as UTF-8', () => {
    expect(atob(basicToken('NT1', 'päss'))).toBe('NT1:pÃ¤ss');
  });
});
