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
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTooltipModule } from '@angular/material/tooltip';
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
    MatCheckboxModule,
    MatTooltipModule,
  ],
  templateUrl: './preview.component.html',
  styleUrl: './preview.component.scss',
})
export class PreviewComponent implements OnInit {
  preview: ImportPreviewResponse | null = null;
  categories: Category[] = [];
  loading = false;

  displayedColumns = [
    'included',
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
    {
      value: 'INCOME',
      label: 'Receita real',
      tooltip: 'Entrada de dinheiro que conta para o orçamento mensal',
    },
    {
      value: 'REIMBURSEMENT',
      label: 'Reembolso',
      tooltip: 'Devolução de um gasto já registrado. Não entra no cálculo de receita',
    },
    {
      value: 'OWN_TRANSFER',
      label: 'Transf. própria',
      tooltip: 'Movimentação entre contas próprias. Ignorada no cálculo de receita',
    },
    {
      value: 'INVESTMENT',
      label: 'Investimento',
      tooltip: 'Resgate de aplicação financeira. Não conta como receita do mês',
    },
  ];

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
    {
      value: 'INVESTMENT',
      label: 'Investimento',
      tooltip: 'Aportes em reservas e aplicações financeiras. Meta: 20%',
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
    }
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
        'Transação interna (fatura ou RDB). Desmarcada para evitar dupla contagem — inclua apenas se souber o que está fazendo',
    };
    return ac ? (tooltips[ac] ?? ac) : '';
  }

  confirm(): void {
    if (!this.preview) return;
    this.loading = true;
    this.importService.confirm(this.preview.sessionId, this.preview.transactions).subscribe({
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
