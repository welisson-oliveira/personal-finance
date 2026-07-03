import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { TransactionService } from '../transaction.service';
import { Page, Transaction } from '../../../core/models/transaction.model';
import { Category } from '../../../core/models/category.model';
import { CategoryService } from '../../../core/services/category.service';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';

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
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatDialogModule,
  ],
  templateUrl: './transaction-list.component.html',
  styleUrl: './transaction-list.component.scss',
})
export class TransactionListComponent implements OnInit {
  page: Page<Transaction> | null = null;
  loading = true;
  categories: Category[] = [];

  filterMonth = '';
  filterType = '';
  filterCategoryId = '';
  pageIndex = 0;
  pageSize = 20;

  displayedColumns = [
    'date',
    'description',
    'category',
    'type',
    'budgetGroup',
    'amount',
    'actions',
  ];

  typeOptions = [
    { value: '', label: 'Todos' },
    { value: 'INCOME', label: 'Receita' },
    { value: 'EXPENSE', label: 'Despesa' },
  ];

  constructor(
    private txService: TransactionService,
    private categoryService: CategoryService,
    private route: ActivatedRoute,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    const monthParam = this.route.snapshot.queryParams['month'];
    if (monthParam) {
      this.filterMonth = monthParam;
    } else {
      const now = new Date();
      this.filterMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
    }

    this.categoryService.getAll().subscribe((cats) => (this.categories = cats));
    this.load();
  }

  load(): void {
    this.loading = true;
    this.txService
      .findAll({
        month: this.filterMonth || undefined,
        type: this.filterType || undefined,
        categoryId: this.filterCategoryId || undefined,
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
    const now = new Date();
    this.filterMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
    this.filterType = '';
    this.filterCategoryId = '';
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
