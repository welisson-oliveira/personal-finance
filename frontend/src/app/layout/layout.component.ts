import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../core/auth/auth.service';

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
  ],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.scss',
})
export class LayoutComponent {
  navItems: NavItem[] = [
    { label: 'Dashboard', icon: 'dashboard', route: '/dashboard' },
    { label: 'Transactions', icon: 'receipt_long', route: '/transactions' },
    { label: 'Import', icon: 'upload_file', route: '/import' },
    { label: 'Review Queue', icon: 'rate_review', route: '/review' },
    { label: 'Categories', icon: 'category', route: '/categories' },
    { label: 'Known Persons', icon: 'people', route: '/known-persons' },
  ];

  constructor(
    public auth: AuthService,
    private router: Router
  ) {}

  logout(): void {
    this.auth.logout();
  }
}
