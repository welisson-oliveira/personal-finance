import { Component, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DashboardService } from './dashboard.service';
import { DashboardResponse } from '../../core/models/dashboard.model';
import { PeriodService } from '../../core/services/period.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  data: DashboardResponse | null = null;
  loading = true;
  errorMessage = '';

  constructor(
    private dashboardService: DashboardService,
    public period: PeriodService
  ) {
    effect(() => {
      this.period.period();
      this.load();
    });
  }

  load(): void {
    this.loading = true;
    this.errorMessage = '';
    this.dashboardService.getMonthly(this.period.year(), this.period.month()).subscribe({
      next: (d) => {
        this.data = d;
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'Não foi possível carregar o dashboard.';
        this.loading = false;
      },
    });
  }

  fmt(val: number | undefined): string {
    if (val == null) return 'R$ 0,00';
    return val.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  clamp(val: number): number {
    return Math.min(100, Math.max(0, val ?? 0));
  }

  hasBase(): boolean {
    return (this.data?.rendaBase ?? 0) > 0;
  }

  baseLabel(): string {
    if (!this.data) return '';
    return this.data.receitaReal > 0 ? 'renda do mês' : 'salário configurado';
  }
}
