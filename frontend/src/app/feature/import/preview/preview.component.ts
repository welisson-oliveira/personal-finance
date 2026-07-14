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
import { ImportPreviewResponse, ParsedTransaction } from '../../../core/models/import.model';
import { Category } from '../../../core/models/category.model';
import { ImportService } from '../import.service';
import { CategoryService } from '../../../core/services/category.service';
import { CategorySelectComponent } from '../../../shared/category-select/category-select.component';

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
    CategorySelectComponent,
  ],
  templateUrl: './preview.component.html',
  styleUrl: './preview.component.scss',
})
export class PreviewComponent implements OnInit {
  preview: ImportPreviewResponse | null = null;
  categories: Category[] = [];
  loading = false;
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
    'notes',
  ];

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
      this.reconcileSelection = (state.preview.reconciliation ?? []).map((s) => s.suggestedId);
    }
    this.notesEdited$
      .pipe(debounceTime(600), takeUntilDestroyed())
      .subscribe(() => this.persistEdits());
  }

  ngOnInit(): void {
    if (!this.preview) {
      this.router.navigate(['/import']);
      return;
    }
    this.categoryService.getAll().subscribe({ next: (cats) => (this.categories = cats) });
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
