import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule, MatIconRegistry } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, map, of, switchMap } from 'rxjs';

import { ApiService } from './core/api.service';
import { AppTitleStrategy } from './core/app-title.strategy';
import { CurrentUserService } from './core/current-user.service';
import { IdleTimeoutService } from './core/idle-timeout.service';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  adminOnly?: boolean;
  /** Only for people who have reportees. */
  managersOnly?: boolean;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatToolbarModule, MatIconModule, MatButtonModule,
    MatMenuModule,
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  protected readonly currentUser = inject(CurrentUserService);
  protected readonly pageName = inject(AppTitleStrategy).page;
  private readonly api = inject(ApiService);

  protected readonly nav: NavItem[] = [
    { path: '/dashboard', label: 'Dashboard', icon: 'space_dashboard' },
    { path: '/assistant', label: 'Access Assistant', icon: 'auto_awesome' },
    { path: '/my-access', label: 'My Access', icon: 'verified_user' },
    { path: '/requests', label: 'Access Requests', icon: 'assignment' },
    { path: '/team', label: 'My Team', icon: 'groups', managersOnly: true },
    { path: '/projects', label: 'Projects', icon: 'folder_open', adminOnly: true },
  ];

  /** Whether the signed-in user has reportees (drives the "My Team" menu item). */
  private readonly hasReports = toSignal(
    toObservable(this.currentUser.userId).pipe(switchMap(id => !id ? of(false) : this.api.capabilities().pipe(
      map(c => c.reports.length > 0), catchError(() => of(false))))),
    { initialValue: false },
  );

  protected readonly visibleNav = computed(() => this.nav.filter(i =>
    (!i.adminOnly || this.currentUser.isAdmin()) && (!i.managersOnly || this.hasReports())));

  protected readonly isHandset = toSignal(
    inject(BreakpointObserver).observe('(max-width: 959px)').pipe(map(r => r.matches)),
    { initialValue: false },
  );

  protected readonly initials = computed(() => {
    const name = this.currentUser.user()?.name ?? this.currentUser.userId();
    return name.split(/\s+/).map(p => p[0]).join('').slice(0, 2).toUpperCase();
  });

  private readonly router = inject(Router);

  constructor() {
    inject(MatIconRegistry).setDefaultFontSetClass('material-icons-outlined');
    inject(IdleTimeoutService); // signs out after 30 minutes without activity
  }

  protected signOut(): void {
    this.currentUser.signOut();
    this.router.navigateByUrl('/login');
  }
}
