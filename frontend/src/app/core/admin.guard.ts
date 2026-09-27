import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { ApiService } from './api.service';

/**
 * Admin-only pages. Asks the backend who the signed-in user is (so it works on a fresh page load before the
 * user directory has arrived) and sends everyone else to the dashboard. The API enforces the same rule.
 */
export const adminGuard: CanActivateFn = () => {
  const router = inject(Router);
  return inject(ApiService).me().pipe(
    map(user => (user.admin ? true : router.parseUrl('/dashboard'))),
    catchError(() => of(router.parseUrl('/dashboard'))),
  );
};
