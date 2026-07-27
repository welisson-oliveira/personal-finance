import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../core/auth/auth.service';
import { MonthSelectorComponent } from './month-selector/month-selector.component';

interface NavItem {
  label: string;
  icon: string;
  route: string;
}

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MonthSelectorComponent,
  ],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.scss',
})
export class LayoutComponent {
  navItems: NavItem[] = [
    { label: 'Dashboard', icon: 'dashboard', route: '/dashboard' },
    { label: 'Relatórios', icon: 'insights', route: '/reports' },
    { label: 'Transações', icon: 'receipt_long', route: '/transactions' },
    { label: 'Importar', icon: 'upload_file', route: '/import' },
    { label: 'Categorias', icon: 'category', route: '/categories' },
    { label: 'Metas', icon: 'savings', route: '/budget-goals' },
    { label: 'Regras', icon: 'rule', route: '/merchant-rules' },
    { label: 'Pessoas Conhecidas', icon: 'people', route: '/known-persons' },
    { label: 'Configurações', icon: 'settings', route: '/settings' },
  ];

  constructor(
    public auth: AuthService,
    private router: Router
  ) {}

  logout(): void {
    this.auth.logout();
  }
}
