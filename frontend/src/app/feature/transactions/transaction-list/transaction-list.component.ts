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

/** View preferences kept across sessions (localStorage). */
interface TxPrefs {
  pageSize?: number;
  sortActive?: string;
  sortDirection?: 'asc' | 'desc';
}

/** Filters + page kept only until the tab closes (sessionStorage) — survive a refresh, not forever. */
interface TxFilters {
  filterType?: string;
  filterCategoryId?: string;
  filterSearch?: string;
  filterBudgetGroup?: string;
  pendingOnly?: boolean;
  showIgnored?: boolean;
  pageIndex?: number;
}

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

  /** Guards the first effect run so a restored page isn't reset before the initial load. */
  private initialized = false;
  // Persistence keys: prefs (definitive) vs filters+page (only until the tab closes).
  private readonly PREFS_KEY = 'tx_view_prefs';
  private readonly FILTERS_KEY = 'tx_view_filters';

  private searchInput$ = new Subject<void>();

  displayedColumns = [
    'select',
    'date',
    'description',
    'type',
    'category',
    'group',
    'amount',
    'actions',
  ];

  /** Ids of the rows ticked for a bulk action (kept only within the current page/filter view). */
  selectedIds = new Set<string>();
  /** Transient bulk-bar controls (reset when the selection is cleared). */
  bulkGroup = '';
  bulkCategoryId = '';
  bulkCompetenceMonth = '';

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
      // Month change → back to the first page. The very first run keeps the restored page.
      if (this.initialized) this.pageIndex = 0;
      this.initialized = true;
      this.load();
    });
    this.searchInput$.pipe(debounceTime(350), takeUntilDestroyed()).subscribe(() => {
      this.applyFilters();
    });
  }

  ngOnInit(): void {
    // Restore first so a refresh keeps filters/sort/page; query params (deep-links) win over it.
    this.restoreState();
    const params = this.route.snapshot.queryParams;
    if (params['month']) {
      this.period.setFromMonthString(params['month']);
    }
    // Deep-link from the Reports "onde vai seu dinheiro" chart: focus this category, fresh page.
    if (params['categoryId']) {
      this.filterCategoryId = params['categoryId'];
      this.pageIndex = 0;
    }
    this.categoryService.getAll().subscribe((cats) => (this.categories = cats));
  }

  load(): void {
    this.saveState();
    // A fresh page/filter/month invalidates the current selection (ids may not be on this page).
    this.clearSelection();
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

  // --- Bulk selection + edit ---
  private get pageIds(): string[] {
    return this.page?.content.map((t) => t.id) ?? [];
  }

  isSelected(tx: Transaction): boolean {
    return this.selectedIds.has(tx.id);
  }

  toggleRow(tx: Transaction): void {
    if (this.selectedIds.has(tx.id)) this.selectedIds.delete(tx.id);
    else this.selectedIds.add(tx.id);
  }

  isAllSelected(): boolean {
    const ids = this.pageIds;
    return ids.length > 0 && ids.every((id) => this.selectedIds.has(id));
  }

  isSomeSelected(): boolean {
    return this.selectedIds.size > 0 && !this.isAllSelected();
  }

  toggleAll(): void {
    if (this.isAllSelected()) this.pageIds.forEach((id) => this.selectedIds.delete(id));
    else this.pageIds.forEach((id) => this.selectedIds.add(id));
  }

  clearSelection(): void {
    this.selectedIds.clear();
    this.bulkGroup = '';
    this.bulkCategoryId = '';
    this.bulkCompetenceMonth = '';
  }

  private runBulk(patch: {
    budgetGroup?: string;
    categoryId?: string;
    competenceMonth?: string;
    ignored?: boolean;
  }): void {
    const ids = [...this.selectedIds];
    if (!ids.length) return;
    this.txService.bulkUpdate({ ids, ...patch }).subscribe({
      next: (updated) => {
        const n = updated.length;
        this.snackBar.open(
          `${n} ${n === 1 ? 'transação atualizada' : 'transações atualizadas'}.`,
          'Fechar',
          { duration: 2500 }
        );
        // load() clears the selection and refreshes the page with the new values.
        this.load();
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Erro na edição em lote.', 'Fechar', {
          duration: 4000,
        });
      },
    });
  }

  bulkSetGroup(): void {
    if (this.bulkGroup) this.runBulk({ budgetGroup: this.bulkGroup });
  }

  bulkSetCategory(): void {
    if (this.bulkCategoryId) this.runBulk({ categoryId: this.bulkCategoryId });
  }

  bulkSetCompetence(): void {
    if (this.bulkCompetenceMonth) this.runBulk({ competenceMonth: this.bulkCompetenceMonth });
  }

  bulkIgnore(ignored: boolean): void {
    this.runBulk({ ignored });
  }

  /**
   * Restores the saved view: page size + sort are kept across sessions (localStorage); the active
   * filters and current page only survive a refresh (sessionStorage), so they don't stick forever.
   */
  private restoreState(): void {
    const prefs = this.readJson<TxPrefs>(localStorage, this.PREFS_KEY);
    if (prefs) {
      this.pageSize = prefs.pageSize ?? this.pageSize;
      this.sortActive = prefs.sortActive ?? this.sortActive;
      this.sortDirection = prefs.sortDirection ?? this.sortDirection;
    }
    const f = this.readJson<TxFilters>(sessionStorage, this.FILTERS_KEY);
    if (f) {
      this.filterType = f.filterType ?? '';
      this.filterCategoryId = f.filterCategoryId ?? '';
      this.filterSearch = f.filterSearch ?? '';
      this.filterBudgetGroup = f.filterBudgetGroup ?? '';
      this.pendingOnly = f.pendingOnly ?? false;
      this.showIgnored = f.showIgnored ?? false;
      this.pageIndex = f.pageIndex ?? 0;
    }
  }

  /** Persists the current view on every load. Best-effort — storage may be unavailable. */
  private saveState(): void {
    try {
      const prefs: TxPrefs = {
        pageSize: this.pageSize,
        sortActive: this.sortActive,
        sortDirection: this.sortDirection,
      };
      localStorage.setItem(this.PREFS_KEY, JSON.stringify(prefs));
      const filters: TxFilters = {
        filterType: this.filterType,
        filterCategoryId: this.filterCategoryId,
        filterSearch: this.filterSearch,
        filterBudgetGroup: this.filterBudgetGroup,
        pendingOnly: this.pendingOnly,
        showIgnored: this.showIgnored,
        pageIndex: this.pageIndex,
      };
      sessionStorage.setItem(this.FILTERS_KEY, JSON.stringify(filters));
    } catch {
      // storage full/unavailable (e.g. private mode) — persistence is optional
    }
  }

  private readJson<T>(store: Storage, key: string): T | null {
    try {
      const raw = store.getItem(key);
      return raw ? (JSON.parse(raw) as T) : null;
    } catch {
      return null;
    }
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
    // Preserve the reimbursement flag (income-only) through inline edits; a reimbursement keeps its
    // budget group so it still offsets the right 50/30/20 bucket.
    const isReimbursement = type === 'INCOME' && tx.reimbursement;
    const req: UpdateTransactionRequest = {
      description: tx.description,
      amount: tx.amount,
      type,
      date: tx.date,
      competenceDate: tx.competenceDate,
      categoryId: keepsCategory ? (patch.categoryId ?? tx.categoryId) : undefined,
      budgetGroup: isExpense || isReimbursement ? (patch.budgetGroup ?? tx.budgetGroup) : undefined,
      investmentDirection: isInvestment
        ? (patch.investmentDirection ?? tx.investmentDirection)
        : undefined,
      ignored: patch.ignored ?? tx.ignored,
      reimbursement: isReimbursement,
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
