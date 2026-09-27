import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { CurrentUserService } from './current-user.service';

/**
 * Sends the signed-in user's Basic credential on API calls. A 401 on a call made with that credential means it
 * no longer works (password changed, user deactivated), so the user is signed out and sent to the login page.
 * Calls that set their own Authorization header (the login attempt) are left alone.
 */
export const currentUserInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/') || req.headers.has('Authorization')) {
    return next(req);
  }
  const currentUser = inject(CurrentUserService);
  const router = inject(Router);
  const token = currentUser.session()?.token;
  if (!token) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { Authorization: `Basic ${token}` } })).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && currentUser.session()?.token === token) {
        currentUser.signOut();
        router.navigate(['/login'], { queryParams: { expired: 1 } });
      }
      return throwError(() => error);
    }),
  );
};
