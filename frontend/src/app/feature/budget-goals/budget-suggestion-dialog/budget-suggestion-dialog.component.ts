import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';
import {
  BudgetSuggestion,
  BulkBudgetGoalItem,
  CategorySuggestion,
  InvestmentSuggestion,
} from '../../../core/models/budget-goal.model';

interface Row {
  item: CategorySuggestion;
  selected: boolean;
  amount: number;
}

interface BucketVM {
  label: string;
  targetPct: number;
  cap: number;
  overCap: boolean;
  rows: Row[];
}

@Component({
  selector: 'app-budget-suggestion-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatCheckboxModule,
  ],
  templateUrl: './budget-suggestion-dialog.component.html',
  styleUrl: './budget-suggestion-dialog.component.scss',
})
export class BudgetSuggestionDialogComponent {
  rendaBase: number;
  investimentos: InvestmentSuggestion;
  buckets: BucketVM[];
  hasBase: boolean;
  hasSuggestions: boolean;

  constructor(
    public dialogRef: MatDialogRef<BudgetSuggestionDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: BudgetSuggestion
  ) {
    this.rendaBase = data.rendaBase;
    this.investimentos = data.investimentos;
    this.hasBase = data.rendaBase > 0;
    this.buckets = data.buckets.map((b) => ({
      label: b.group === 'ESSENTIAL' ? 'Essenciais' : 'Não essenciais',
      targetPct: b.group === 'ESSENTIAL' ? 50 : 30,
      cap: b.cap,
      overCap: b.overCap,
      rows: b.categories.map((c) => ({ item: c, selected: true, amount: c.suggestedAmount })),
    }));
    this.hasSuggestions = this.buckets.some((b) => b.rows.length > 0);
  }

  selectedTotal(b: BucketVM): number {
    return b.rows.filter((r) => r.selected).reduce((sum, r) => sum + (+r.amount || 0), 0);
  }

  bucketOverCap(b: BucketVM): boolean {
    return b.cap > 0 && this.selectedTotal(b) > b.cap;
  }

  selectedCount(): number {
    return this.buckets.reduce(
      (n, b) => n + b.rows.filter((r) => r.selected && r.amount > 0).length,
      0
    );
  }

  fmt(value: number | undefined): string {
    if (value == null) return 'R$ 0,00';
    return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  apply(): void {
    const goals: BulkBudgetGoalItem[] = this.buckets.flatMap((b) =>
      b.rows
        .filter((r) => r.selected && r.amount > 0)
        .map((r) => ({ categoryId: r.item.categoryId, amount: +r.amount }))
    );
    if (goals.length === 0) return;
    this.dialogRef.close(goals);
  }
}
