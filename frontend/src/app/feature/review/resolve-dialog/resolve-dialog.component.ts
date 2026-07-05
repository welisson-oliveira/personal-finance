import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { ReviewQueueItem, ResolveReviewRequest } from '../../../core/models/review.model';
import { Category } from '../../../core/models/category.model';
import { CategorySelectComponent } from '../../../shared/category-select/category-select.component';

interface DialogData {
  item: ReviewQueueItem;
  categories: Category[];
}

@Component({
  selector: 'app-resolve-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    CategorySelectComponent,
  ],
  templateUrl: './resolve-dialog.component.html',
  styleUrl: './resolve-dialog.component.scss',
})
export class ResolveDialogComponent {
  type: string;
  categoryId: string | undefined;
  budgetGroup = 'NON_ESSENTIAL';
  incomeType = 'INCOME';
  merchantName: string;
  transactionNotes = '';

  budgetGroups = [
    { value: 'ESSENTIAL', label: 'Essencial (50%)' },
    { value: 'NON_ESSENTIAL', label: 'Não Essencial (30%)' },
    { value: 'INVESTMENT', label: 'Investimento (20%)' },
  ];

  incomeTypes = [
    { value: 'INCOME', label: 'Receita real' },
    { value: 'REIMBURSEMENT', label: 'Reembolso' },
    { value: 'OWN_TRANSFER', label: 'Transferência própria' },
    { value: 'INVESTMENT', label: 'Investimento' },
  ];

  constructor(
    public dialogRef: MatDialogRef<ResolveDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: DialogData
  ) {
    this.type = data.item.type || 'EXPENSE';
    this.categoryId = data.item.suggestedCategoryId;
    this.merchantName = data.item.normalizedDescription || data.item.rawDescription;
  }

  get isIncome(): boolean {
    return this.type === 'INCOME';
  }

  get valid(): boolean {
    if (!this.merchantName) return false;
    return this.isIncome ? !!this.incomeType : !!this.budgetGroup;
  }

  confirm(): void {
    if (!this.valid) return;
    const req: ResolveReviewRequest = {
      merchantName: this.merchantName,
      type: this.type,
      transactionNotes: this.transactionNotes || undefined,
      categoryId: this.isIncome ? undefined : this.categoryId || undefined,
      budgetGroup: this.isIncome ? undefined : this.budgetGroup,
      incomeType: this.isIncome ? this.incomeType : undefined,
    };
    this.dialogRef.close(req);
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
