import { ApplicationConfig } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { TitleStrategy, provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { AppTitleStrategy } from './core/app-title.strategy';
import { currentUserInterceptor } from './core/current-user.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes, withComponentInputBinding()),
    { provide: TitleStrategy, useExisting: AppTitleStrategy },
    provideHttpClient(withInterceptors([currentUserInterceptor])),
    provideAnimationsAsync(),
  ],
};
