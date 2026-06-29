import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatBadgeModule } from '@angular/material/badge';
import { ImportPreviewResponse, ParsedTransaction } from '../../../core/models/import.model';
import { Category } from '../../../core/models/category.model';
import { ImportService } from '../import.service';
import { CategoryService } from '../../../core/services/category.service';

@Component({
  selector: 'app-preview',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatChipsModule,
    MatProgressBarModule,
    MatSnackBarModule,
    MatBadgeModule,
  ],
  templateUrl: './preview.component.html',
  styleUrl: './preview.component.scss',
})
export class PreviewComponent implements OnInit {
  preview: ImportPreviewResponse | null = null;
  categories: Category[] = [];
  loading = false;

  displayedColumns = [
    'date',
    'description',
    'amount',
    'type',
    'incomeType',
    'budgetGroup',
    'category',
    'notes',
  ];

  incomeTypes = [
    { value: 'INCOME', label: 'Income' },
    { value: 'REIMBURSEMENT', label: 'Reimbursement' },
    { value: 'OWN_TRANSFER', label: 'Own Transfer' },
    { value: 'INVESTMENT', label: 'Investment' },
  ];

  budgetGroups = [
    { value: 'ESSENTIAL', label: 'Essential' },
    { value: 'NON_ESSENTIAL', label: 'Non-Essential' },
    { value: 'INVESTMENT', label: 'Investment' },
  ];

  constructor(
    private router: Router,
    private importService: ImportService,
    private categoryService: CategoryService,
    private snackBar: MatSnackBar
  ) {
    const nav = this.router.getCurrentNavigation();
    const state = nav?.extras?.state as { preview: ImportPreviewResponse } | undefined;
    if (state?.preview) {
      this.preview = state.preview;
    }
  }

  ngOnInit(): void {
    if (!this.preview) {
      this.router.navigate(['/import']);
      return;
    }
    this.categoryService.getAll().subscribe({ next: (cats) => (this.categories = cats) });
  }

  confirm(): void {
    if (!this.preview) return;
    this.loading = true;
    this.importService.confirm(this.preview.sessionId).subscribe({
      next: () => {
        this.snackBar.open('Import confirmed successfully!', 'Close', { duration: 3000 });
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Failed to confirm import', 'Close', {
          duration: 4000,
        });
        this.loading = false;
      },
    });
  }

  cancel(): void {
    if (!this.preview) return;
    this.importService.cancel(this.preview.sessionId).subscribe({
      complete: () => this.router.navigate(['/import']),
    });
  }

  trackByIndex(index: number): number {
    return index;
  }

  getTypeChipColor(type: string): string {
    return type === 'INCOME' ? 'accent' : 'warn';
  }

  needsReviewCount(): number {
    return this.preview?.transactions.filter((t) => t.needsReview).length ?? 0;
  }

  formatAmount(tx: ParsedTransaction): string {
    const sign = tx.type === 'EXPENSE' ? '-' : '+';
    return `${sign} R$ ${Math.abs(tx.amount).toFixed(2)}`;
  }
}
