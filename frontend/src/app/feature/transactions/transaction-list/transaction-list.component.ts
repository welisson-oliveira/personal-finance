import { Component, OnInit, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TransactionService } from '../transaction.service';
import {
  Page,
  Transaction,
  UpdateTransactionRequest,
} from '../../../core/models/transaction.model';
import { Category } from '../../../core/models/category.model';
import { CategoryService } from '../../../core/services/category.service';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { TransactionEditDialogComponent } from '../transaction-edit-dialog/transaction-edit-dialog.component';
import { CategoryFormDialogComponent } from '../../categories/category-form-dialog/category-form-dialog.component';
import { PeriodService } from '../../../core/services/period.service';

@Component({
  selector: 'app-transaction-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatMenuModule,
    MatDividerModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatDialogModule,
    MatTooltipModule,
  ],
  templateUrl: './transaction-list.component.html',
  styleUrl: './transaction-list.component.scss',
})
export class TransactionListComponent implements OnInit {
  page: Page<Transaction> | null = null;
  loading = true;
  categories: Category[] = [];

  filterType = '';
  filterCategoryId = '';
  pendingOnly = false;
  pageIndex = 0;
  pageSize = 20;

  displayedColumns = ['date', 'description', 'type', 'category', 'group', 'amount', 'actions'];

  typeLabels: Record<string, string> = {
    INCOME: 'Receita',
    EXPENSE: 'Despesa',
    INVESTMENT: 'Investimento',
  };

  typeOptions = [
    { value: '', label: 'Todos' },
    { value: 'INCOME', label: 'Receita' },
    { value: 'EXPENSE', label: 'Despesa' },
    { value: 'INVESTMENT', label: 'Investimento' },
  ];

  editTypeOptions = [
    { value: 'INCOME', label: 'Receita' },
    { value: 'EXPENSE', label: 'Despesa' },
    { value: 'INVESTMENT', label: 'Investimento' },
  ];

  budgetGroupOptions = [
    { value: 'ESSENTIAL', label: 'Essencial' },
    { value: 'NON_ESSENTIAL', label: 'Não Essencial' },
  ];

  directionOptions = [
    { value: 'CONTRIBUTION', label: 'Aporte' },
    { value: 'REDEMPTION', label: 'Resgate' },
  ];

  budgetGroupLabels: Record<string, string> = {
    ESSENTIAL: 'Essencial',
    NON_ESSENTIAL: 'Não Essencial',
  };

  directionLabels: Record<string, string> = {
    CONTRIBUTION: 'Aporte',
    REDEMPTION: 'Resgate',
  };

  // Inline apelido editing
  editingId: string | null = null;
  editingNotes = '';

  constructor(
    private txService: TransactionService,
    private categoryService: CategoryService,
    private route: ActivatedRoute,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    public period: PeriodService
  ) {
    effect(() => {
      this.period.period();
      this.pageIndex = 0;
      this.load();
    });
  }

  ngOnInit(): void {
    const monthParam = this.route.snapshot.queryParams['month'];
    if (monthParam) {
      this.period.setFromMonthString(monthParam);
    }
    this.categoryService.getAll().subscribe((cats) => (this.categories = cats));
  }

  load(): void {
    this.loading = true;
    this.txService
      .findAll({
        month: this.period.monthString(),
        type: this.filterType || undefined,
        categoryId: this.filterCategoryId || undefined,
        needsReview: this.pendingOnly || undefined,
        page: this.pageIndex,
        size: this.pageSize,
      })
      .subscribe({
        next: (p) => {
          this.page = p;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
        },
      });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.load();
  }

  applyFilters(): void {
    this.pageIndex = 0;
    this.load();
  }

  clearFilters(): void {
    this.filterType = '';
    this.filterCategoryId = '';
    this.pendingOnly = false;
    this.applyFilters();
  }

  confirmDelete(tx: Transaction): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: { message: `Excluir "${tx.description}"?` },
      width: '360px',
    });
    ref.afterClosed().subscribe((confirmed: boolean) => {
      if (confirmed) this.delete(tx.id);
    });
  }

  openEditDialog(tx: Transaction): void {
    const ref = this.dialog.open(TransactionEditDialogComponent, {
      data: { tx, categories: this.categories },
      width: '520px',
    });
    ref.afterClosed().subscribe((req: UpdateTransactionRequest | undefined) => {
      if (!req) return;
      this.txService.update(tx.id, req).subscribe({
        next: () => {
          this.snackBar.open('Transação atualizada.', 'Fechar', { duration: 2500 });
          this.load();
        },
        error: (err) => {
          this.snackBar.open(err.error?.message || 'Erro ao atualizar transação.', 'Fechar', {
            duration: 4000,
          });
        },
      });
    });
  }

  // --- Inline apelido ---
  startEdit(tx: Transaction): void {
    this.editingId = tx.id;
    this.editingNotes = tx.notes || '';
  }

  cancelEdit(): void {
    this.editingId = null;
    this.editingNotes = '';
  }

  saveNotes(tx: Transaction): void {
    const notes = this.editingNotes.trim();
    this.txService.updateNotes(tx.id, notes).subscribe({
      next: () => {
        this.editingId = null;
        this.editingNotes = '';
        this.snackBar.open('Apelido salvo.', 'Fechar', { duration: 2500 });
        this.load();
      },
      error: () => {
        this.snackBar.open('Erro ao salvar apelido.', 'Fechar', { duration: 3000 });
      },
    });
  }

  // --- Per-field quick edit (each datum on its own, via a small menu) ---
  onTypePick(tx: Transaction, value: string): void {
    if (value !== tx.type) this.quickUpdate(tx, { type: value });
  }

  onCategoryPick(tx: Transaction, value: string | undefined): void {
    if (value !== tx.categoryId) this.quickUpdate(tx, { categoryId: value });
  }

  /** Opens the category dialog, persists the new category and assigns it to this transaction. */
  createCategoryFor(tx: Transaction): void {
    this.dialog
      .open(CategoryFormDialogComponent, { data: null, width: '440px' })
      .afterClosed()
      .subscribe((result) => {
        if (!result) return;
        this.categoryService.create(result).subscribe({
          next: (created) => {
            this.categories = [...this.categories, created];
            this.snackBar.open(`Categoria "${created.name}" criada.`, 'Fechar', { duration: 2500 });
            this.onCategoryPick(tx, created.id);
          },
          error: (err) => {
            this.snackBar.open(err.error?.message || 'Erro ao criar categoria.', 'Fechar', {
              duration: 4000,
            });
          },
        });
      });
  }

  onGroupPick(tx: Transaction, value: string): void {
    if (tx.type === 'EXPENSE') this.quickUpdate(tx, { budgetGroup: value });
    else if (tx.type === 'INVESTMENT') this.quickUpdate(tx, { investmentDirection: value });
  }

  /** Applies a single-field change, normalizing the fields that don't apply to the (new) type. */
  private quickUpdate(
    tx: Transaction,
    patch: Partial<{
      type: string;
      categoryId?: string;
      budgetGroup?: string;
      investmentDirection?: string;
      ignored: boolean;
    }>
  ): void {
    const type = patch.type ?? tx.type;
    const isExpense = type === 'EXPENSE';
    const isInvestment = type === 'INVESTMENT';
    const req: UpdateTransactionRequest = {
      description: tx.description,
      amount: tx.amount,
      type,
      date: tx.date,
      categoryId: isExpense ? (patch.categoryId ?? tx.categoryId) : undefined,
      budgetGroup: isExpense ? (patch.budgetGroup ?? tx.budgetGroup) : undefined,
      investmentDirection: isInvestment
        ? (patch.investmentDirection ?? tx.investmentDirection)
        : undefined,
      ignored: patch.ignored ?? tx.ignored,
      notes: tx.notes,
      shared: tx.shared,
      totalAmount: tx.totalAmount,
      userShare: tx.userShare,
    };
    this.txService.update(tx.id, req).subscribe({
      next: () => {
        this.snackBar.open('Transação atualizada.', 'Fechar', { duration: 2000 });
        this.load();
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Erro ao salvar.', 'Fechar', { duration: 4000 });
      },
    });
  }

  private delete(id: string): void {
    this.txService.delete(id).subscribe({
      next: () => {
        this.snackBar.open('Transação excluída.', 'Fechar', { duration: 3000 });
        this.load();
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Erro ao excluir.', 'Fechar', { duration: 4000 });
      },
    });
  }

  fmt(val: number): string {
    return val?.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }
}
