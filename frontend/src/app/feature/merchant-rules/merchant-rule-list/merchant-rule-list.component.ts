import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { MerchantRuleService } from '../merchant-rule.service';
import { MerchantRule } from '../../../core/models/merchant-rule.model';
import { MerchantRuleFormDialogComponent } from '../merchant-rule-form-dialog/merchant-rule-form-dialog.component';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-merchant-rule-list',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatListModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatDialogModule,
    MatTooltipModule,
    MatDividerModule,
  ],
  templateUrl: './merchant-rule-list.component.html',
  styleUrl: './merchant-rule-list.component.scss',
})
export class MerchantRuleListComponent implements OnInit {
  rules: MerchantRule[] = [];
  loading = true;

  readonly groupLabel: Record<string, string> = {
    ESSENTIAL: 'Essencial',
    NON_ESSENTIAL: 'Não Essencial',
  };

  readonly typeLabel: Record<string, string> = {
    INCOME: 'Receita',
    EXPENSE: 'Despesa',
    INVESTMENT: 'Investimento',
  };

  readonly directionLabel: Record<string, string> = {
    CONTRIBUTION: 'Aporte',
    REDEMPTION: 'Resgate',
  };

  constructor(
    private merchantRuleService: MerchantRuleService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.merchantRuleService.getAll().subscribe({
      next: (rules) => {
        this.rules = rules;
        this.loading = false;
      },
      error: () => (this.loading = false),
    });
  }

  openCreate(): void {
    this.dialog
      .open(MerchantRuleFormDialogComponent, { width: '460px', data: null })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.merchantRuleService.create(result).subscribe({
            next: () => {
              this.snackBar.open('Regra criada!', 'Fechar', { duration: 3000 });
              this.load();
            },
            error: (err) => this.showError(err, 'Erro ao criar.'),
          });
        }
      });
  }

  /** Edit a user rule in place, or personalize a global one (creates a personal override). */
  openEdit(rule: MerchantRule): void {
    this.dialog
      .open(MerchantRuleFormDialogComponent, { width: '460px', data: rule })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.merchantRuleService.update(rule.id, result).subscribe({
            next: () => {
              this.snackBar.open(
                rule.global ? 'Regra personalizada!' : 'Regra atualizada!',
                'Fechar',
                {
                  duration: 3000,
                }
              );
              this.load();
            },
            error: (err) => this.showError(err, 'Erro ao salvar.'),
          });
        }
      });
  }

  confirmDelete(rule: MerchantRule): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        width: '400px',
        data: { message: `Excluir a regra de "${rule.merchantName}"?` },
      })
      .afterClosed()
      .subscribe((confirmed: boolean) => {
        if (confirmed) {
          this.merchantRuleService.delete(rule.id).subscribe({
            next: () => {
              this.snackBar.open('Regra excluída.', 'Fechar', { duration: 3000 });
              this.load();
            },
            error: (err) => this.showError(err, 'Erro ao excluir.'),
          });
        }
      });
  }

  private showError(err: { error?: { message?: string } }, fallback: string): void {
    this.snackBar.open(err.error?.message || fallback, 'Fechar', { duration: 4000 });
  }
}
