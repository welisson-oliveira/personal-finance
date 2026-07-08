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
          {
            path: 'history',
            redirectTo: '/import',
            pathMatch: 'full',
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
      {
        path: 'transactions',
        loadComponent: () =>
          import('./feature/transactions/transaction-list/transaction-list.component').then(
            (m) => m.TransactionListComponent
          ),
      },
      {
        path: 'categories',
        loadComponent: () =>
          import('./feature/categories/category-list/category-list.component').then(
            (m) => m.CategoryListComponent
          ),
      },
      {
        path: 'budget-goals',
        loadComponent: () =>
          import('./feature/budget-goals/budget-goal-list/budget-goal-list.component').then(
            (m) => m.BudgetGoalListComponent
          ),
      },
      {
        path: 'known-persons',
        loadComponent: () =>
          import('./feature/known-persons/known-person-list/known-person-list.component').then(
            (m) => m.KnownPersonListComponent
          ),
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./feature/settings/settings.component').then((m) => m.SettingsComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
