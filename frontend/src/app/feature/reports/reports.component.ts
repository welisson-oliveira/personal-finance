import { Component, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ReportService } from './report.service';
import { CategoryTotal, MonthlyPoint } from '../../core/models/report.model';
import { PeriodService } from '../../core/services/period.service';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './reports.component.html',
  styleUrl: './reports.component.scss',
})
export class ReportsComponent {
  evolution: MonthlyPoint[] = [];
  breakdown: CategoryTotal[] = [];
  loadingEvolution = true;
  loadingBreakdown = true;

  readonly months = 6;
  readonly chartHeight = 160;

  constructor(
    private reportService: ReportService,
    public period: PeriodService
  ) {
    // Evolution is independent of the selected month (last N months)
    this.loadEvolution();
    // Breakdown follows the global month
    effect(() => {
      this.period.period();
      this.loadBreakdown();
    });
  }

  private loadEvolution(): void {
    this.loadingEvolution = true;
    this.reportService.monthlyEvolution(this.months).subscribe({
      next: (e) => {
        this.evolution = e;
        this.loadingEvolution = false;
      },
      error: () => (this.loadingEvolution = false),
    });
  }

  private loadBreakdown(): void {
    this.loadingBreakdown = true;
    this.reportService.categoryBreakdown(this.period.year(), this.period.month()).subscribe({
      next: (b) => {
        this.breakdown = b;
        this.loadingBreakdown = false;
      },
      error: () => (this.loadingBreakdown = false),
    });
  }

  monthLabel(p: MonthlyPoint): string {
    return this.period.monthLabels[p.month - 1].slice(0, 3);
  }

  /** Max of income/expense across all months, used to scale the bars. */
  get evolutionMax(): number {
    const values = this.evolution.flatMap((p) => [p.receita, p.despesa]);
    return Math.max(1, ...values);
  }

  barHeight(value: number): number {
    return (Math.max(0, value) / this.evolutionMax) * this.chartHeight;
  }

  get breakdownMax(): number {
    return Math.max(1, ...this.breakdown.map((c) => c.total));
  }

  breakdownWidth(total: number): number {
    return (total / this.breakdownMax) * 100;
  }

  fmt(value: number | undefined): string {
    if (value == null) return 'R$ 0,00';
    return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  fmtShort(value: number): string {
    if (Math.abs(value) >= 1000) return 'R$ ' + (value / 1000).toFixed(1) + 'k';
    return 'R$ ' + value.toFixed(0);
  }
}
