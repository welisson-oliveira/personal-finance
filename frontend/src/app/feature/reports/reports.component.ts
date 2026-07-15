import { Component, effect } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ReportService } from './report.service';
import { CategoryTotal, MonthlyPoint, TopExpense } from '../../core/models/report.model';
import { BudgetGoalService } from '../budget-goals/budget-goal.service';
import { BudgetGoal } from '../../core/models/budget-goal.model';
import { PeriodService } from '../../core/services/period.service';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatProgressSpinnerModule, MatTooltipModule],
  templateUrl: './reports.component.html',
  styleUrl: './reports.component.scss',
})
export class ReportsComponent {
  evolution: MonthlyPoint[] = [];
  breakdown: CategoryTotal[] = [];
  topExpenses: TopExpense[] = [];
  /** Budget-goal cap per category (by categoryId), for the over-cap "gargalo" signal. */
  goalsByCategory = new Map<string, BudgetGoal>();
  loadingEvolution = true;
  loadingBreakdown = true;
  loadingTop = true;

  readonly months = 6;
  readonly chartHeight = 160;

  constructor(
    private reportService: ReportService,
    private budgetGoalService: BudgetGoalService,
    private router: Router,
    public period: PeriodService
  ) {
    // Evolution is independent of the selected month (last N months)
    this.loadEvolution();
    // Breakdown, goals and top expenses all follow the global month
    effect(() => {
      this.period.period();
      this.loadBreakdown();
      this.loadGoals();
      this.loadTopExpenses();
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

  private loadGoals(): void {
    this.budgetGoalService.getAll(this.period.year(), this.period.month()).subscribe({
      next: (goals) => {
        this.goalsByCategory = new Map(goals.map((g) => [g.categoryId, g]));
      },
      error: () => (this.goalsByCategory = new Map()),
    });
  }

  private loadTopExpenses(): void {
    this.loadingTop = true;
    this.reportService.topExpenses(this.period.year(), this.period.month(), 10).subscribe({
      next: (t) => {
        this.topExpenses = t;
        this.loadingTop = false;
      },
      error: () => (this.loadingTop = false),
    });
  }

  /** Total of all categories in the breakdown, for the % share of each. */
  get breakdownTotal(): number {
    return this.breakdown.reduce((sum, c) => sum + c.total, 0);
  }

  pctOfTotal(total: number): number {
    return this.breakdownTotal > 0 ? (total / this.breakdownTotal) * 100 : 0;
  }

  abs(v: number): number {
    return Math.abs(v);
  }

  goalFor(categoryId: string): BudgetGoal | undefined {
    return this.goalsByCategory.get(categoryId);
  }

  isOverGoal(categoryId: string): boolean {
    const goal = this.goalsByCategory.get(categoryId);
    return !!goal && goal.remaining < 0;
  }

  /** Deep-links to Transactions filtered by this category + the current month. */
  goToCategory(cat: CategoryTotal): void {
    if (!cat.categoryId) return; // the "Sem categoria" bucket has no filter target
    this.router.navigate(['/transactions'], {
      queryParams: { categoryId: cat.categoryId, month: this.period.monthString() },
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
