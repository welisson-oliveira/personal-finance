import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ImportService } from '../import.service';
import { PendingReconciliation } from '../../../core/models/import.model';

@Component({
  selector: 'app-reconciliation',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
  ],
  templateUrl: './reconciliation.component.html',
  styleUrl: './reconciliation.component.scss',
})
export class ReconciliationComponent implements OnInit {
  items: PendingReconciliation[] = [];
  selection: (string | null)[] = [];
  loading = true;

  constructor(
    private importService: ImportService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.importService.getReconciliation().subscribe({
      next: (items) => {
        this.items = items;
        this.selection = items.map((i) => i.suggestedFaturaId);
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  reconcile(index: number): void {
    const item = this.items[index];
    const faturaId = this.selection[index];
    if (!faturaId) return;
    this.importService.reconcile(item.paymentId, faturaId).subscribe({
      next: () => {
        this.items.splice(index, 1);
        this.selection.splice(index, 1);
        this.snackBar.open('Pagamento conciliado — substituído pelos itens da fatura.', 'Fechar', {
          duration: 3000,
        });
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Erro ao conciliar.', 'Fechar', {
          duration: 4000,
        });
      },
    });
  }

  fmtBRL(v: number): string {
    return v?.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }
}
