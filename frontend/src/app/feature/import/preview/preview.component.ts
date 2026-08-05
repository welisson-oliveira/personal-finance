import { Component, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
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
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { ImportPreviewResponse, ParsedTransaction } from '../../../core/models/import.model';
import { Category } from '../../../core/models/category.model';
import { Transaction, UpdateTransactionRequest } from '../../../core/models/transaction.model';
import { ImportService } from '../import.service';
import { CategoryService } from '../../../core/services/category.service';
import { CategorySelectComponent } from '../../../shared/category-select/category-select.component';
import { AutofocusDirective } from '../../../shared/autofocus/autofocus.directive';
import { TransactionEditDialogComponent } from '../../transactions/transaction-edit-dialog/transaction-edit-dialog.component';
import {
  PreviewPropagateDialogComponent,
  PreviewPropagateScope,
} from './preview-propagate-dialog.component';

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
    MatCheckboxModule,
    MatTooltipModule,
    MatDialogModule,
    CategorySelectComponent,
    AutofocusDirective,
  ],
  templateUrl: './preview.component.html',
  styleUrl: './preview.component.scss',
})
export class PreviewComponent implements OnInit {
  preview: ImportPreviewResponse | null = null;
  categories: Category[] = [];
  loading = false;
  /** Competence month applied to the whole fatura (payment month), as an <input type="month"> value. */
  faturaCompetence = '';
  /** True once the import was confirmed/cancelled — stops autosave from rewriting a cleared preview. */
  private finalized = false;
  /** Debounces free-text (notes) edits so we don't PUT on every keystroke. */
  private notesEdited$ = new Subject<void>();
  /** Per reconciliation slot: the selected candidate id (or null = "Não conciliar"). */
  reconcileSelection: (string | null)[] = [];

  displayedColumns = [
    'included',
    'date',
    'description',
    'amount',
    'type',
    'budgetGroup',
    'direction',
    'category',
    'actions',
  ];

  /** Rows currently shown in the table (preview.transactions after search/type filtering). */
  displayedTransactions: ParsedTransaction[] = [];
  filterSearch = '';
  filterType = '';

  /** Inline apelido (notes) editing — parity with the transactions list. */
  editingApelidoFor: ParsedTransaction | null = null;
  apelidoDraft = '';

  typeLabels: Record<string, string> = {
    INCOME: 'Receita',
    EXPENSE: 'Despesa',
    INVESTMENT: 'Investimento',
  };

  budgetGroups = [
    {
      value: 'ESSENTIAL',
      label: 'Essencial',
      tooltip: 'Gastos necessários: moradia, alimentação, transporte, saúde. Meta: 50%',
    },
    {
      value: 'NON_ESSENTIAL',
      label: 'Não Essencial',
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

  types = [
    { value: 'INCOME', label: 'Receita' },
    { value: 'EXPENSE', label: 'Despesa' },
    { value: 'INVESTMENT', label: 'Investimento' },
  ];

  constructor(
    private router: Router,
    private importService: ImportService,
    private categoryService: CategoryService,
    private snackBar: MatSnackBar,
    private dialog: MatDialog
  ) {
    const nav = this.router.getCurrentNavigation();
    const state = nav?.extras?.state as { preview: ImportPreviewResponse } | undefined;
    if (state?.preview) {
      this.preview = state.preview;
      this.reconcileSelection = (state.preview.reconciliation ?? []).map((s) => s.suggestedId);
      // Seed the batch competence control from the parsed default (= fatura due month).
      const seed = state.preview.transactions.find((t) => t.competenceDate)?.competenceDate;
      if (seed) this.faturaCompetence = seed.slice(0, 7); // yyyy-MM for <input type="month">
    }
    this.notesEdited$
      .pipe(debounceTime(600), takeUntilDestroyed())
      .subscribe(() => this.persistEdits());
  }

  get isFatura(): boolean {
    return this.preview?.documentType === 'FATURA';
  }

  /** Applies the chosen competence month to every transaction in the fatura and persists it. */
  onCompetenceChange(): void {
    if (!this.preview || !this.faturaCompetence) return;
    const competence = `${this.faturaCompetence}-01`; // first day of the chosen month
    this.preview.transactions.forEach((t) => (t.competenceDate = competence));
    this.persistEdits();
  }

  ngOnInit(): void {
    if (!this.preview) {
      this.router.navigate(['/import']);
      return;
    }
    this.refreshDisplayed();
    this.categoryService.getAll().subscribe({ next: (cats) => (this.categories = cats) });
  }

  /** Recomputes the visible rows from the search + type filters (kept stable for the mat-table). */
  refreshDisplayed(): void {
    const txs = this.preview?.transactions ?? [];
    const q = this.filterSearch.trim().toLowerCase();
    this.displayedTransactions = txs.filter((t) => {
      if (this.filterType && t.type !== this.filterType) return false;
      if (!q) return true;
      return (
        (t.description || '').toLowerCase().includes(q) ||
        (t.normalizedDescription || '').toLowerCase().includes(q) ||
        (t.notes || '').toLowerCase().includes(q)
      );
    });
  }

  clearFilters(): void {
    this.filterSearch = '';
    this.filterType = '';
    this.refreshDisplayed();
  }

  // --- Inline apelido (notes) ---
  startApelido(tx: ParsedTransaction): void {
    this.editingApelidoFor = tx;
    this.apelidoDraft = tx.notes || '';
  }

  cancelApelido(): void {
    this.editingApelidoFor = null;
    this.apelidoDraft = '';
  }

  /** Saves the apelido on every row with the same effective name in this import, then persists. */
  saveApelido(tx: ParsedTransaction): void {
    const value = this.apelidoDraft.trim() || undefined;
    const name = tx.normalizedDescription || tx.description;
    (this.preview?.transactions ?? []).forEach((t) => {
      if ((t.normalizedDescription || t.description) === name) t.notes = value;
    });
    this.editingApelidoFor = null;
    this.apelidoDraft = '';
    this.persistEdits();
  }

  includedCount(): number {
    return this.preview?.transactions.filter((t) => t.included).length ?? 0;
  }

  autoClassificationLabel(ac: string | undefined): string {
    const labels: Record<string, string> = {
      OWN_TRANSFER: 'Transferência própria',
      INVESTMENT: 'Investimento',
      INTERNAL: 'Transação interna',
      INTERNAL_FATURA_EXISTS: 'Fatura já importada',
    };
    return ac ? (labels[ac] ?? ac) : '';
  }

  autoClassificationTooltip(ac: string | undefined): string {
    const tooltips: Record<string, string> = {
      OWN_TRANSFER:
        'Transferência entre contas próprias detectada automaticamente. Não será contabilizada como receita',
      INVESTMENT:
        'Resgate de aplicação detectado automaticamente. Não será contabilizado como receita do mês',
      INTERNAL:
        'Pagamento de fatura. Fica registrado como "Ignorada" (não entra nos totais/relatórios) para não duplicar com os itens da fatura — ao importar a fatura do cartão depois, este pagamento é substituído pelos itens individuais',
      INTERNAL_FATURA_EXISTS:
        'A fatura deste período já foi importada. Incluir este pagamento causaria dupla contagem.',
    };
    return ac ? (tooltips[ac] ?? ac) : '';
  }

  /** Fires immediately when a discrete control (checkbox/select/category) changes. */
  onEdit(): void {
    this.persistEdits();
  }

  /**
   * Inline type change on a parsed row (parity with the transactions list). Normalizes the fields
   * that don't apply to the new type — INCOME keeps a category but no group/direction; EXPENSE keeps
   * category + group; INVESTMENT keeps a direction only — then persists.
   */
  onTypeChange(tx: ParsedTransaction): void {
    this.normalizeForType(tx);
    this.classifyWithScope(tx, (sib) => {
      sib.type = tx.type;
      this.normalizeForType(sib);
      sib.budgetGroup = tx.budgetGroup;
      sib.investmentDirection = tx.investmentDirection;
      sib.categoryId = tx.categoryId;
    });
  }

  onGroupChange(tx: ParsedTransaction): void {
    this.classifyWithScope(tx, (sib) => (sib.budgetGroup = tx.budgetGroup));
  }

  onDirectionChange(tx: ParsedTransaction): void {
    this.classifyWithScope(tx, (sib) => (sib.investmentDirection = tx.investmentDirection));
  }

  onCategoryChange(tx: ParsedTransaction): void {
    this.classifyWithScope(tx, (sib) => (sib.categoryId = tx.categoryId));
  }

  private normalizeForType(tx: ParsedTransaction): void {
    if (tx.type === 'INCOME') {
      tx.budgetGroup = undefined;
      tx.investmentDirection = undefined;
    } else if (tx.type === 'EXPENSE') {
      tx.investmentDirection = undefined;
    } else if (tx.type === 'INVESTMENT') {
      tx.budgetGroup = undefined;
      tx.categoryId = undefined;
    }
  }

  private effectiveName(tx: ParsedTransaction): string {
    return tx.normalizedDescription || tx.description;
  }

  /**
   * Applies a classification to the row (already set via ngModel) and flags it to teach a rule on
   * confirm. When the same merchant appears on other rows in this import, asks whether to apply to
   * all of them too (parity with the transactions list's propagation dialog).
   */
  private classifyWithScope(
    tx: ParsedTransaction,
    copyToSibling: (sib: ParsedTransaction) => void
  ): void {
    tx.learn = true;
    const name = this.effectiveName(tx);
    const siblings = (this.preview?.transactions ?? []).filter(
      (t) => t !== tx && this.effectiveName(t) === name
    );
    if (siblings.length === 0) {
      this.refreshDisplayed();
      this.persistEdits();
      return;
    }
    this.dialog
      .open(PreviewPropagateDialogComponent, {
        data: { merchant: tx.notes || name, count: siblings.length + 1 },
        width: '420px',
      })
      .afterClosed()
      .subscribe((scope: PreviewPropagateScope | undefined) => {
        if (scope === 'BATCH') {
          siblings.forEach((sib) => {
            copyToSibling(sib);
            sib.learn = true;
          });
        }
        this.refreshDisplayed();
        this.persistEdits();
      });
  }

  /** Resolves the "needs review" flag right in the preview (parity with the list's confirm-review). */
  confirmReviewRow(tx: ParsedTransaction): void {
    tx.needsReview = false;
    this.persistEdits();
  }

  /**
   * Opens the full transaction editor on a parsed row (fields the inline controls don't cover:
   * type, amount, date, description, per-row competence). Reuses {@link TransactionEditDialogComponent}
   * with the propagate section hidden — nothing is persisted yet, so there are no siblings to
   * propagate to. The returned patch is written back onto the in-memory row and autosaved.
   */
  openRowEditor(tx: ParsedTransaction): void {
    const asTransaction: Transaction = {
      id: '',
      description: tx.description,
      normalizedDescription: tx.normalizedDescription,
      amount: tx.amount,
      type: tx.type,
      budgetGroup: tx.budgetGroup,
      investmentDirection: tx.investmentDirection,
      ignored: tx.ignored ?? false,
      reimbursement: tx.reimbursement ?? false,
      needsReview: tx.needsReview,
      date: tx.date,
      competenceDate: tx.competenceDate,
      notes: tx.notes,
      categoryId: tx.categoryId,
      categoryName: tx.categoryName,
      source: this.preview?.documentType ?? '',
      cardHolder: tx.cardHolder,
      installmentInfo: tx.installmentInfo,
      shared: false,
    };
    this.dialog
      .open(TransactionEditDialogComponent, {
        data: {
          tx: asTransaction,
          categories: this.categories,
          hidePropagate: true,
          title: 'Editar linha da importação',
        },
        width: '520px',
      })
      .afterClosed()
      .subscribe((req: UpdateTransactionRequest | undefined) => {
        if (!req) return;
        tx.description = req.description;
        tx.amount = req.amount;
        tx.type = req.type;
        tx.date = req.date;
        tx.competenceDate = req.competenceDate;
        tx.categoryId = req.categoryId;
        tx.budgetGroup = req.budgetGroup;
        tx.investmentDirection = req.investmentDirection;
        tx.ignored = req.ignored;
        tx.reimbursement = req.reimbursement;
        this.persistEdits();
      });
  }

  /** Fires on notes typing; debounced so we don't PUT on every keystroke. */
  onNotesInput(): void {
    this.notesEdited$.next();
  }

  /** Persists the current edits onto the pending session so they survive leaving the screen. */
  private persistEdits(): void {
    if (!this.preview || this.finalized) return;
    this.importService.savePreview(this.preview.sessionId, this.preview.transactions).subscribe({
      error: (err) => console.error('Failed to autosave import preview', err),
    });
  }

  confirm(): void {
    if (!this.preview) return;
    this.loading = true;
    this.finalized = true;

    // Apply the reconciliation choices: FATURA → ids of extrato payments to delete; EXTRATO →
    // flag the chosen bill payments as reconciled so the backend skips creating them.
    let reconcileIds: string[] | undefined;
    const slots = this.preview.reconciliation ?? [];
    if (this.preview.documentType === 'FATURA') {
      reconcileIds = slots
        .map((_, i) => this.reconcileSelection[i])
        .filter((id): id is string => !!id);
    } else {
      slots.forEach((slot, i) => {
        if (slot.paymentIndex != null) {
          this.preview!.transactions[slot.paymentIndex].reconciled = !!this.reconcileSelection[i];
        }
      });
    }

    // The category selector uses '' for "no category"; send undefined so the backend doesn't
    // try to parse an empty string as a UUID.
    const transactions = this.preview.transactions.map((tx) => ({
      ...tx,
      categoryId: tx.categoryId || undefined,
    }));
    this.importService.confirm(this.preview.sessionId, transactions, reconcileIds).subscribe({
      next: () => {
        this.snackBar.open('Import confirmed successfully!', 'Close', { duration: 3000 });
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Failed to confirm import', 'Close', {
          duration: 4000,
        });
        this.loading = false;
        this.finalized = false;
      },
    });
  }

  cancel(): void {
    if (!this.preview) return;
    this.finalized = true;
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

  hasReconciliation(): boolean {
    return (this.preview?.reconciliation?.length ?? 0) > 0;
  }

  reconcileTitle(): string {
    return this.preview?.documentType === 'FATURA'
      ? 'Conciliar com o pagamento no extrato'
      : 'Conciliar com uma fatura importada';
  }

  fmtBRL(v: number): string {
    return v?.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  formatAmount(tx: ParsedTransaction): string {
    const sign = tx.type === 'EXPENSE' ? '-' : '+';
    return `${sign} R$ ${Math.abs(tx.amount).toFixed(2)}`;
  }
}
