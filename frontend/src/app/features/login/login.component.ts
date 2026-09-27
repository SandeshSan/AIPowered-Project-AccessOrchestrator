import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';

import { ApiService } from '../../core/api.service';
import { CurrentUserService, basicToken } from '../../core/current-user.service';

/** Sign in with employee ID and password (checked by the backend over HTTP Basic). */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <div class="login-page">
      <form class="card" (ngSubmit)="submit()" #form="ngForm" aria-labelledby="login-title">
        <div class="brand">
          <span class="logo"><mat-icon>shield_person</mat-icon></span>
          <div>
            <h1 id="login-title">Access Orchestrator</h1>
            <p>Sign in to manage your project access</p>
          </div>
        </div>

        @if (reason() === 'idle' && !error()) {
          <div class="notice" role="status"><mat-icon>schedule</mat-icon><span>You were signed out after 30 minutes of inactivity.</span></div>
        } @else if (expired() && !error()) {
          <div class="notice" role="status"><mat-icon>info</mat-icon><span>Your session ended. Please sign in again.</span></div>
        }
        @if (error(); as message) {
          <div class="error-banner" role="alert"><mat-icon>error_outline</mat-icon><span>{{ message }}</span></div>
        }

        <mat-form-field appearance="outline">
          <mat-label>Employee ID</mat-label>
          <mat-icon matPrefix>badge</mat-icon>
          <input matInput name="userId" [(ngModel)]="userId" required autocomplete="username"
                 placeholder="e.g. NT10036" autofocus>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Password</mat-label>
          <mat-icon matPrefix>lock</mat-icon>
          <input matInput name="password" [(ngModel)]="password" required autocomplete="current-password"
                 [type]="showPassword() ? 'text' : 'password'">
          <button mat-icon-button matSuffix type="button" (click)="showPassword.set(!showPassword())"
                  [attr.aria-label]="showPassword() ? 'Hide password' : 'Show password'">
            <mat-icon>{{ showPassword() ? 'visibility_off' : 'visibility' }}</mat-icon>
          </button>
        </mat-form-field>

        <button mat-flat-button color="primary" type="submit" class="submit" [disabled]="busy() || form.invalid">
          @if (busy()) { <mat-spinner diameter="18" /> } @else { Sign in }
        </button>

        <p class="footnote">
          <mat-icon>lock</mat-icon>
          Approvals and provisioning are handled by the IGA system.
        </p>
      </form>
    </div>
  `,
  styles: [`
    .login-page { min-height: 100vh; display: grid; place-items: center; padding: 24px 16px; background:
      radial-gradient(circle at 20% 0%, var(--app-primary-soft), transparent 55%), var(--app-bg); }
    .card { width: 100%; max-width: 400px; background: var(--app-surface); border: 1px solid var(--app-border);
      border-radius: 14px; padding: 32px 28px 20px; display: flex; flex-direction: column; gap: 6px;
      box-shadow: 0 12px 32px rgba(21, 28, 44, .08); }
    .brand { display: flex; align-items: center; gap: 14px; margin-bottom: 18px; }
    .logo { width: 44px; height: 44px; border-radius: 12px; display: grid; place-items: center; flex: none;
      background: var(--app-primary); color: #fff; }
    h1 { margin: 0; font-size: 20px; font-weight: 600; }
    .brand p { margin: 2px 0 0; color: var(--app-muted); font-size: 13px; }
    .error-banner, .notice { margin-bottom: 12px; }
    .notice { display: flex; align-items: center; gap: 8px; padding: 10px 12px; border-radius: var(--app-radius);
      background: var(--app-info-soft); color: var(--app-info); font-size: 13px; }
    .submit { height: 44px; margin-top: 4px; }
    .submit mat-spinner { margin: 0 auto; }
    .footnote { display: flex; align-items: center; justify-content: center; gap: 6px; margin: 18px 0 0;
      color: var(--app-muted); font-size: 12px; }
    .footnote mat-icon { font-size: 16px; width: 16px; height: 16px; }
  `],
})
export class LoginComponent {
  private readonly api = inject(ApiService);
  private readonly currentUser = inject(CurrentUserService);
  private readonly router = inject(Router);

  /** Where to go after signing in (set by the auth guard). */
  readonly returnUrl = input<string>();
  /** Set when the interceptor signed the user out because their credential stopped working. */
  readonly expired = input<string>();
  /** Why the user was signed out, e.g. 'idle' from the inactivity timeout. */
  readonly reason = input<string>();

  protected userId = '';
  protected password = '';
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showPassword = signal(false);

  protected submit(): void {
    const userId = this.userId.trim();
    if (!userId || !this.password || this.busy()) {
      return;
    }
    const token = basicToken(userId, this.password);
    this.busy.set(true);
    this.error.set(null);
    this.api.login(token).subscribe({
      next: user => {
        this.currentUser.signIn(user, token);
        this.password = '';
        this.router.navigateByUrl(safeReturnUrl(this.returnUrl()));
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.password = '';
        this.error.set(e instanceof HttpErrorResponse && e.status === 401
          ? 'Incorrect employee ID or password, or the account is inactive.'
          : 'Could not reach the server. Please try again.');
      },
    });
  }
}

/** Only same-app paths, so a crafted link cannot send you to another site after signing in. */
function safeReturnUrl(url: string | undefined): string {
  return url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/login') ? url : '/dashboard';
}
