import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatRadioModule } from '@angular/material/radio';
import { Transaction, UpdateTransactionRequest } from '../../../core/models/transaction.model';
import { Category } from '../../../core/models/category.model';
import { CurrencyMaskDirective } from '../../../shared/currency-mask/currency-mask.directive';
import { CategorySelectComponent } from '../../../shared/category-select/category-select.component';

interface DialogData {
  tx: Transaction;
  categories: Category[];
  /** Hides the "apply classification to…" propagate section (import preview: nothing persisted yet). */
  hidePropagate?: boolean;
  /** Optional dialog title override (e.g. "Editar linha da importação"). */
  title?: string;
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
    MatCheckboxModule,
    MatRadioModule,
    CurrencyMaskDirective,
    CategorySelectComponent,
    MatTooltipModule,
  ],
  templateUrl: './transaction-edit-dialog.component.html',
  styleUrl: './transaction-edit-dialog.component.scss',
})
export class TransactionEditDialogComponent {
  description: string;
  amount: number | null;
  type: string;
  budgetGroup: string;
  investmentDirection: string;
  ignored: boolean;
  reimbursement: boolean;
  date: string;
  /** Competence as a yyyy-MM value for <input type="month">; only the month (with year) matters. */
  competenceMonth: string;
  categoryId: string;
  propagate: 'ALL' | 'FUTURE' | 'CURRENT' = 'CURRENT';

  types = [
    { value: 'INCOME', label: 'Receita' },
    { value: 'EXPENSE', label: 'Despesa' },
    { value: 'INVESTMENT', label: 'Investimento' },
  ];

  budgetGroups = [
    {
      value: 'ESSENTIAL',
      label: 'Essencial (50%)',
      tooltip: 'Gastos necessários: moradia, alimentação, transporte, saúde. Meta: 50%',
    },
    {
      value: 'NON_ESSENTIAL',
      label: 'Não Essencial (30%)',
      tooltip: 'Lazer, assinaturas, compras não prioritárias. Meta: 30%',
    },
  ];

  directions = [
    { value: 'CONTRIBUTION', label: 'Aporte', tooltip: 'Dinheiro aplicado em investimentos' },
    {
      value: 'REDEMPTION',
      label: 'Resgate',
      tooltip: 'Resgate de aplicação. Não conta como receita',
    },
  ];

  constructor(
    public dialogRef: MatDialogRef<TransactionEditDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: DialogData
  ) {
    const tx = data.tx;
    this.description = tx.description;
    this.amount = tx.amount;
    this.type = tx.type;
    this.budgetGroup = tx.budgetGroup || '';
    this.investmentDirection = tx.investmentDirection || '';
    this.ignored = tx.ignored;
    this.reimbursement = tx.reimbursement;
    this.date = tx.date;
    // Keep year+month (yyyy-MM); the day is irrelevant for the monthly Dashboard/Reports window.
    this.competenceMonth = (tx.competenceDate || tx.date).slice(0, 7);
    this.categoryId = tx.categoryId || '';
  }

  get isExpense(): boolean {
    return this.type === 'EXPENSE';
  }

  get isIncome(): boolean {
    return this.type === 'INCOME';
  }

  get isInvestment(): boolean {
    return this.type === 'INVESTMENT';
  }

  get valid(): boolean {
    return !!this.description.trim() && this.amount != null && this.amount > 0 && !!this.date;
  }

  /** A reimbursement (income offsetting an expense) carries a budget group like an expense does. */
  get showBudgetGroup(): boolean {
    return this.isExpense || (this.isIncome && this.reimbursement);
  }

  onTypeChange(): void {
    // Category applies to expense + income; budget group to expense (and income reimbursements);
    // direction only to investment. Investment carries no category. Reimbursement is income-only.
    if (!this.isIncome) {
      this.reimbursement = false;
    }
    if (!this.showBudgetGroup) {
      this.budgetGroup = '';
    }
    if (!this.isInvestment) {
      this.investmentDirection = '';
    }
    if (this.isInvestment) {
      this.categoryId = '';
    }
  }

  onReimbursementChange(): void {
    if (!this.reimbursement) {
      this.budgetGroup = '';
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
      // First day of the chosen month; the aggregation window only cares about the month (with year).
      competenceDate: this.competenceMonth ? `${this.competenceMonth}-01` : this.date,
      categoryId: this.isInvestment ? undefined : this.categoryId || undefined,
      budgetGroup: this.showBudgetGroup ? this.budgetGroup || undefined : undefined,
      investmentDirection: this.isInvestment ? this.investmentDirection || undefined : undefined,
      ignored: this.ignored,
      reimbursement: this.isIncome && this.reimbursement,
      notes: tx.notes,
      shared: tx.shared,
      totalAmount: tx.totalAmount,
      userShare: tx.userShare,
      propagate: this.propagate,
    };
    this.dialogRef.close(req);
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
