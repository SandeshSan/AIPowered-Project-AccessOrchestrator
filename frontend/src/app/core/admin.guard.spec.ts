import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';

import { adminGuard } from './admin.guard';

describe('adminGuard', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
  });

  function run(): Promise<boolean | UrlTree> {
    const result = TestBed.runInInjectionContext(
      () => adminGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)) as Observable<boolean | UrlTree>;
    return firstValueFrom(result);
  }

  it('lets admins in', async () => {
    const decision = run();
    backend.expectOne('/api/me').flush({ userId: 'NT10001', admin: true });
    expect(await decision).toBeTrue();
  });

  it('sends everyone else to the dashboard', async () => {
    const decision = run();
    backend.expectOne('/api/me').flush({ userId: 'NT10036', admin: false });
    const url = TestBed.inject(Router).serializeUrl(await decision as UrlTree);
    expect(url).toBe('/dashboard');
  });

  it('fails closed when the user cannot be resolved', async () => {
    const decision = run();
    backend.expectOne('/api/me').flush({}, { status: 404, statusText: 'Not Found' });
    expect(TestBed.inject(Router).serializeUrl(await decision as UrlTree)).toBe('/dashboard');
  });
});
