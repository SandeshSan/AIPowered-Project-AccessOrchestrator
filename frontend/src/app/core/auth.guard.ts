import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { CurrentUserService } from './current-user.service';

/** Pages that need a signed-in user; everyone else goes to the login page and comes back afterwards. */
export const authGuard: CanActivateFn = (_route, state) => {
  if (inject(CurrentUserService).signedIn()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** The login page itself: already signed-in users go straight to the dashboard. */
export const signedOutGuard: CanActivateFn = () =>
  inject(CurrentUserService).signedIn() ? inject(Router).parseUrl('/dashboard') : true;
