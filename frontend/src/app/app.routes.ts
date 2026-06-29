import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'auth',
    children: [
      {
        path: 'login',
        loadComponent: () =>
          import('./feature/auth/login/login.component').then((m) => m.LoginComponent),
      },
      {
        path: 'register',
        loadComponent: () =>
          import('./feature/auth/register/register.component').then((m) => m.RegisterComponent),
      },
      { path: '', redirectTo: 'login', pathMatch: 'full' },
    ],
  },
  {
    path: '',
    loadComponent: () => import('./layout/layout.component').then((m) => m.LayoutComponent),
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./feature/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'import',
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./feature/import/upload/upload.component').then((m) => m.UploadComponent),
          },
          {
            path: 'preview',
            loadComponent: () =>
              import('./feature/import/preview/preview.component').then((m) => m.PreviewComponent),
          },
        ],
      },
      {
        path: 'review',
        loadComponent: () =>
          import('./feature/review/review-queue/review-queue.component').then(
            (m) => m.ReviewQueueComponent
          ),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
