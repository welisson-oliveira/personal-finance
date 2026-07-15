import { Component, OnInit, effect } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
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
import {
  PropagateScopeDialogComponent,
  PropagateScope,
} from '../propagate-scope-dialog/propagate-scope-dialog.component';
import { CategoryFormDialogComponent } from '../../categories/category-form-dialog/category-form-dialog.component';
import { AutofocusDirective } from '../../../shared/autofocus/autofocus.directive';
import { PeriodService } from '../../../core/services/period.service';

/** Single-field change applied inline in the list (via {@link TransactionListComponent.quickUpdate}). */
type QuickPatch = Partial<{
  type: string;
  categoryId?: string;
  budgetGroup?: string;
  investmentDirection?: string;
  ignored: boolean;
}>;

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
    MatSortModule,
    MatMenuModule,
    MatDividerModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatSlideToggleModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatDialogModule,
    MatTooltipModule,
    AutofocusDirective,
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
  filterSearch = '';
  filterBudgetGroup = '';
  pendingOnly = false;
  showIgnored = false;
  pageIndex = 0;
  pageSize = 20;

  sortActive = 'date';
  sortDirection: 'asc' | 'desc' = 'desc';

  private searchInput$ = new Subject<void>();

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
    this.searchInput$.pipe(debounceTime(350), takeUntilDestroyed()).subscribe(() => {
      this.applyFilters();
    });
  }

  ngOnInit(): void {
    const params = this.route.snapshot.queryParams;
    if (params['month']) {
      this.period.setFromMonthString(params['month']);
    }
    // Deep-link from the Reports "onde vai seu dinheiro" chart: pre-filter by category.
    if (params['categoryId']) {
      this.filterCategoryId = params['categoryId'];
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
        search: this.filterSearch.trim() || undefined,
        budgetGroup: this.filterBudgetGroup || undefined,
        includeIgnored: this.showIgnored || undefined,
        sort: `${this.sortActive},${this.sortDirection}`,
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

  onSortChange(sort: Sort): void {
    // Falling back to date/desc keeps a stable order when the user clears the sort.
    this.sortActive = sort.direction ? sort.active : 'date';
    this.sortDirection = sort.direction || 'desc';
    this.pageIndex = 0;
    this.load();
  }

  onSearchInput(): void {
    this.searchInput$.next();
  }

  applyFilters(): void {
    this.pageIndex = 0;
    this.load();
  }

  clearFilters(): void {
    this.filterType = '';
    this.filterCategoryId = '';
    this.filterSearch = '';
    this.filterBudgetGroup = '';
    this.pendingOnly = false;
    this.showIgnored = false;
    this.applyFilters();
  }

  /** Replaces a single row in place (new array ref so mat-table re-renders) — no full reload. */
  private patchRow(updated: Transaction): void {
    if (!this.page) return;
    const content = this.page.content.map((t) => (t.id === updated.id ? updated : t));
    this.page = { ...this.page, content };
  }

  private removeRow(id: string): void {
    if (!this.page) return;
    const content = this.page.content.filter((t) => t.id !== id);
    this.page = { ...this.page, content, totalElements: Math.max(0, this.page.totalElements - 1) };
  }

  /** Effective name = the apelido key: normalized description, or the raw one when absent. */
  private effectiveName(tx: Transaction): string {
    return tx.normalizedDescription || tx.description;
  }

  /**
   * The apelido (notes) propagates on the backend to every transaction with the same effective
   * name; mirror that on all visible rows so it shows without a reload.
   */
  private patchNotesForEffectiveName(source: Transaction, notes: string | undefined): void {
    if (!this.page) return;
    const name = this.effectiveName(source);
    const content = this.page.content.map((t) =>
      this.effectiveName(t) === name ? { ...t, notes } : t
    );
    this.page = { ...this.page, content };
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
        next: (updated) => {
          // Propagated edits (ALL/FUTURE) change sibling rows — refresh instead of patching one.
          if (req.propagate && req.propagate !== 'CURRENT') this.load();
          else this.patchRow(updated);
          this.snackBar.open('Transação atualizada.', 'Fechar', { duration: 2500 });
        },
        error: (err) => {
          this.snackBar.open(err.error?.message || 'Erro ao atualizar transação.', 'Fechar', {
            duration: 4000,
          });
        },
      });
    });
  }

  /** Explicitly confirms the pending review for a row (does not touch its classification). */
  confirmReview(tx: Transaction): void {
    this.txService.confirmReview(tx.id).subscribe({
      next: (updated) => {
        // Under the "pending only" filter the resolved row leaves the list; otherwise just drop
        // the chip in place.
        if (this.pendingOnly) this.removeRow(tx.id);
        else this.patchRow(updated);
        this.snackBar.open('Revisão confirmada.', 'Fechar', { duration: 2000 });
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Erro ao confirmar revisão.', 'Fechar', {
          duration: 4000,
        });
      },
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
      next: (updated) => {
        this.editingId = null;
        this.editingNotes = '';
        // Reflects on every row sharing the effective name (backend propagates the apelido).
        this.patchNotesForEffectiveName(updated, updated.notes);
        this.snackBar.open('Apelido salvo.', 'Fechar', { duration: 2500 });
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

  /** A "Pagamento de fatura" row (extrato), which is ignored by default to avoid double-counting. */
  isBillPayment(tx: Transaction): boolean {
    return (tx.description || '').toLowerCase().startsWith('pagamento de fatura');
  }

  /** Setup/transition month: count this bill payment as a real expense (un-ignore). */
  countBillPayment(tx: Transaction): void {
    this.quickUpdate(tx, { ignored: false });
  }

  /** Back to the default (ignored) so it doesn't double-count once the fatura is imported. */
  ignoreBillPayment(tx: Transaction): void {
    this.quickUpdate(tx, { ignored: true });
  }

  /**
   * Applies a single-field change. A classification change asks how far to propagate; a pure
   * ignore/unignore toggle (e.g. counting a bill payment) skips that dialog — there's nothing to
   * propagate — and applies only to this transaction.
   */
  private quickUpdate(tx: Transaction, patch: QuickPatch): void {
    const onlyIgnoredToggle =
      patch.ignored !== undefined &&
      patch.type === undefined &&
      patch.categoryId === undefined &&
      patch.budgetGroup === undefined &&
      patch.investmentDirection === undefined;

    if (onlyIgnoredToggle) {
      this.applyUpdate(tx, patch, 'CURRENT');
      return;
    }

    const ref = this.dialog.open(PropagateScopeDialogComponent);
    ref.afterClosed().subscribe((scope: PropagateScope | undefined) => {
      if (scope === undefined) {
        // User cancelled — reload to restore the original value shown in the row
        this.load();
        return;
      }
      this.applyUpdate(tx, patch, scope);
    });
  }

  private applyUpdate(tx: Transaction, patch: QuickPatch, scope: PropagateScope): void {
    const type = patch.type ?? tx.type;
    const isExpense = type === 'EXPENSE';
    const isInvestment = type === 'INVESTMENT';
    // Category applies to expense + income (investment carries none).
    const keepsCategory = type === 'EXPENSE' || type === 'INCOME';
    const req: UpdateTransactionRequest = {
      description: tx.description,
      amount: tx.amount,
      type,
      date: tx.date,
      competenceDate: tx.competenceDate,
      categoryId: keepsCategory ? (patch.categoryId ?? tx.categoryId) : undefined,
      budgetGroup: isExpense ? (patch.budgetGroup ?? tx.budgetGroup) : undefined,
      investmentDirection: isInvestment
        ? (patch.investmentDirection ?? tx.investmentDirection)
        : undefined,
      ignored: patch.ignored ?? tx.ignored,
      notes: tx.notes,
      shared: tx.shared,
      totalAmount: tx.totalAmount,
      userShare: tx.userShare,
      propagate: scope,
    };
    this.txService.update(tx.id, req).subscribe({
      next: (updated) => {
        // CURRENT touches only this row; ALL/FUTURE propagate to siblings, so refresh the page to
        // reflect the propagated classification instead of leaving stale rows behind.
        if (scope === 'CURRENT') this.patchRow(updated);
        else this.load();
        this.snackBar.open('Transação atualizada.', 'Fechar', { duration: 2000 });
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Erro ao salvar.', 'Fechar', {
          duration: 4000,
        });
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
