import { Component, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { BudgetGoalService } from '../budget-goal.service';
import { BudgetGoal, CreateBudgetGoalRequest } from '../../../core/models/budget-goal.model';
import { Category } from '../../../core/models/category.model';
import { CategoryService } from '../../../core/services/category.service';
import { PeriodService } from '../../../core/services/period.service';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { BudgetGoalFormDialogComponent } from '../budget-goal-form-dialog/budget-goal-form-dialog.component';

@Component({
  selector: 'app-budget-goal-list',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatDialogModule,
  ],
  templateUrl: './budget-goal-list.component.html',
  styleUrl: './budget-goal-list.component.scss',
})
export class BudgetGoalListComponent {
  goals: BudgetGoal[] = [];
  categories: Category[] = [];
  loading = true;

  constructor(
    private goalService: BudgetGoalService,
    private categoryService: CategoryService,
    public period: PeriodService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {
    this.categoryService.getAll().subscribe((cats) => (this.categories = cats));
    effect(() => {
      this.period.period();
      this.load();
    });
  }

  load(): void {
    this.loading = true;
    this.goalService.getAll(this.period.year(), this.period.month()).subscribe({
      next: (g) => {
        this.goals = g;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  openCreate(): void {
    const ref = this.dialog.open(BudgetGoalFormDialogComponent, {
      data: {
        goal: null,
        categories: this.categories,
        usedCategoryIds: this.goals.map((g) => g.categoryId),
      },
      width: '440px',
    });
    ref.afterClosed().subscribe((req: CreateBudgetGoalRequest | undefined) => {
      if (!req) return;
      this.goalService.create(req).subscribe({
        next: () => {
          this.snackBar.open('Meta criada.', 'Fechar', { duration: 2500 });
          this.load();
        },
        error: (err) =>
          this.snackBar.open(err.error?.message || 'Erro ao criar meta.', 'Fechar', {
            duration: 4000,
          }),
      });
    });
  }

  openEdit(goal: BudgetGoal): void {
    const ref = this.dialog.open(BudgetGoalFormDialogComponent, {
      data: { goal, categories: this.categories, usedCategoryIds: [] },
      width: '440px',
    });
    ref.afterClosed().subscribe((req: CreateBudgetGoalRequest | undefined) => {
      if (!req) return;
      this.goalService.update(goal.id, req).subscribe({
        next: () => {
          this.snackBar.open('Meta atualizada.', 'Fechar', { duration: 2500 });
          this.load();
        },
        error: () => this.snackBar.open('Erro ao atualizar meta.', 'Fechar', { duration: 4000 }),
      });
    });
  }

  confirmDelete(goal: BudgetGoal): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: { message: `Excluir a meta de "${goal.categoryName}"?` },
      width: '360px',
    });
    ref.afterClosed().subscribe((confirmed: boolean) => {
      if (!confirmed) return;
      this.goalService.delete(goal.id).subscribe({
        next: () => {
          this.snackBar.open('Meta excluída.', 'Fechar', { duration: 2500 });
          this.load();
        },
        error: () => this.snackBar.open('Erro ao excluir meta.', 'Fechar', { duration: 4000 }),
      });
    });
  }

  clamp(value: number): number {
    return Math.min(100, Math.max(0, value ?? 0));
  }

  barColor(pct: number): 'primary' | 'accent' | 'warn' {
    if (pct > 100) return 'warn';
    if (pct >= 80) return 'accent';
    return 'primary';
  }

  fmt(value: number | undefined): string {
    if (value == null) return 'R$ 0,00';
    return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }
}
