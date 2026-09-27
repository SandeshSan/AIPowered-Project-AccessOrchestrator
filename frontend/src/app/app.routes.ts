import { Routes } from '@angular/router';

import { adminGuard } from './core/admin.guard';
import { authGuard, signedOutGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Sign in',
    canActivate: [signedOutGuard],
    loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent),
  },
  {
    // Everything else needs a signed-in user
    path: '',
    canActivateChild: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
      },
      {
        path: 'assistant',
        title: 'Access Assistant',
        loadComponent: () => import('./features/assistant/assistant.component').then(m => m.AssistantComponent),
      },
      {
        path: 'my-access',
        title: 'My Access',
        loadComponent: () => import('./features/my-access/my-access.component').then(m => m.MyAccessComponent),
      },
      {
        path: 'team',
        title: 'My Team',
        loadComponent: () => import('./features/team/team.component').then(m => m.TeamComponent),
      },
      {
        path: 'requests',
        title: 'Access Requests',
        loadComponent: () => import('./features/requests/requests.component').then(m => m.RequestsComponent),
      },
      {
        path: 'projects',
        title: 'Projects',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/projects/projects.component').then(m => m.ProjectsComponent),
      },
      {
        path: 'projects/:id',
        title: 'Project',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./features/projects/project-detail.component').then(m => m.ProjectDetailComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
