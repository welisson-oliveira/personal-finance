import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { Transaction, UpdateTransactionRequest } from '../../../core/models/transaction.model';
import { Category } from '../../../core/models/category.model';
import { CurrencyMaskDirective } from '../../../shared/currency-mask/currency-mask.directive';
import { CategorySelectComponent } from '../../../shared/category-select/category-select.component';

interface DialogData {
  tx: Transaction;
  categories: Category[];
}

@Component({
  selector: 'app-transaction-edit-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    CurrencyMaskDirective,
    CategorySelectComponent,
  ],
  templateUrl: './transaction-edit-dialog.component.html',
  styleUrl: './transaction-edit-dialog.component.scss',
})
export class TransactionEditDialogComponent {
  description: string;
  amount: number | null;
  type: string;
  incomeType: string;
  budgetGroup: string;
  date: string;
  categoryId: string;

  types = [
    { value: 'INCOME', label: 'Receita' },
    { value: 'EXPENSE', label: 'Despesa' },
  ];

  incomeTypes = [
    { value: '', label: 'Nenhum' },
    { value: 'INCOME', label: 'Receita real' },
    { value: 'REIMBURSEMENT', label: 'Reembolso' },
    { value: 'OWN_TRANSFER', label: 'Transferência própria' },
    { value: 'INVESTMENT', label: 'Investimento' },
  ];

  budgetGroups = [
    { value: '', label: 'Nenhum' },
    { value: 'ESSENTIAL', label: 'Essencial (50%)' },
    { value: 'NON_ESSENTIAL', label: 'Não Essencial (30%)' },
    { value: 'INVESTMENT', label: 'Investimento (20%)' },
  ];

  constructor(
    public dialogRef: MatDialogRef<TransactionEditDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: DialogData
  ) {
    const tx = data.tx;
    this.description = tx.description;
    this.amount = tx.amount;
    this.type = tx.type;
    this.incomeType = tx.incomeType || '';
    this.budgetGroup = tx.budgetGroup || '';
    this.date = tx.date;
    this.categoryId = tx.categoryId || '';
  }

  get isIncome(): boolean {
    return this.type === 'INCOME';
  }

  get valid(): boolean {
    return !!this.description.trim() && this.amount != null && this.amount > 0 && !!this.date;
  }

  onTypeChange(): void {
    // Income has an income type but no budget group/category;
    // expense has budget group/category but no income type.
    if (this.isIncome) {
      this.budgetGroup = '';
      this.categoryId = '';
    } else {
      this.incomeType = '';
    }
  }

  confirm(): void {
    if (!this.valid) return;
    const tx = this.data.tx;
    const req: UpdateTransactionRequest = {
      description: this.description.trim(),
      amount: this.amount!,
      type: this.type,
      date: this.date,
      categoryId: this.isIncome ? undefined : this.categoryId || undefined,
      budgetGroup: this.isIncome ? undefined : this.budgetGroup || undefined,
      incomeType: this.isIncome ? this.incomeType || undefined : undefined,
      notes: tx.notes,
      shared: tx.shared,
      totalAmount: tx.totalAmount,
      userShare: tx.userShare,
    };
    this.dialogRef.close(req);
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
