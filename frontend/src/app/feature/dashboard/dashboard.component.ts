import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { DashboardService } from './dashboard.service';
import { DashboardResponse } from '../../core/models/dashboard.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatButtonModule,
    MatSelectModule,
    MatFormFieldModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  data: DashboardResponse | null = null;
  loading = true;
  errorMessage = '';

  selectedYear: number;
  selectedMonth: number;

  years: number[] = [];
  months = [
    { value: 1, label: 'Janeiro' },
    { value: 2, label: 'Fevereiro' },
    { value: 3, label: 'Março' },
    { value: 4, label: 'Abril' },
    { value: 5, label: 'Maio' },
    { value: 6, label: 'Junho' },
    { value: 7, label: 'Julho' },
    { value: 8, label: 'Agosto' },
    { value: 9, label: 'Setembro' },
    { value: 10, label: 'Outubro' },
    { value: 11, label: 'Novembro' },
    { value: 12, label: 'Dezembro' },
  ];

  constructor(private dashboardService: DashboardService) {
    const now = new Date();
    this.selectedYear = now.getFullYear();
    this.selectedMonth = now.getMonth() + 1;
    for (let y = now.getFullYear(); y >= now.getFullYear() - 3; y--) {
      this.years.push(y);
    }
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.errorMessage = '';
    this.dashboardService.getMonthly(this.selectedYear, this.selectedMonth).subscribe({
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

  monthLabel(): string {
    return this.months.find((m) => m.value === this.selectedMonth)?.label ?? '';
  }
}
