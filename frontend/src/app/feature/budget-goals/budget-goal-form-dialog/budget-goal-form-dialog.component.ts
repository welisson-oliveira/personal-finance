import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { BudgetGoal, CreateBudgetGoalRequest } from '../../../core/models/budget-goal.model';
import { Category } from '../../../core/models/category.model';
import { CurrencyMaskDirective } from '../../../shared/currency-mask/currency-mask.directive';
import { CategorySelectComponent } from '../../../shared/category-select/category-select.component';

interface DialogData {
  goal: BudgetGoal | null;
  categories: Category[];
  usedCategoryIds: string[];
}

@Component({
  selector: 'app-budget-goal-form-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    CurrencyMaskDirective,
    CategorySelectComponent,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.goal ? 'Editar' : 'Nova' }} meta</h2>
    <mat-dialog-content>
      <div class="form-fields">
        @if (data.goal) {
          <p class="edit-category">
            Categoria: <strong>{{ data.goal.categoryName }}</strong>
          </p>
        } @else {
          <app-category-select
            [categories]="availableCategories"
            label="Categoria"
            [(value)]="categoryId"
          />
        }

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Meta mensal</mat-label>
          <span matTextPrefix>R$&nbsp;</span>
          <input matInput type="text" inputmode="numeric" appCurrencyMask [(ngModel)]="amount" />
          <mat-hint>Teto de gasto por mês nesta categoria</mat-hint>
        </mat-form-field>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="dialogRef.close()">Cancelar</button>
      <button mat-raised-button color="primary" (click)="save()" [disabled]="!valid">Salvar</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .form-fields {
        display: flex;
        flex-direction: column;
        gap: 8px;
        min-width: 360px;
        padding-top: 8px;
      }
      .full-width {
        width: 100%;
      }
      .edit-category {
        font-size: 0.9rem;
        color: #555;
        margin: 0 0 8px;
      }
    `,
  ],
})
export class BudgetGoalFormDialogComponent {
  categoryId = '';
  amount: number | null = null;
  availableCategories: Category[];

  constructor(
    public dialogRef: MatDialogRef<BudgetGoalFormDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: DialogData
  ) {
    if (data.goal) {
      this.categoryId = data.goal.categoryId;
      this.amount = data.goal.amount;
    }
    // On create, only categories without an existing goal can be chosen
    this.availableCategories = data.categories.filter((c) => !data.usedCategoryIds.includes(c.id));
  }

  get valid(): boolean {
    return !!this.categoryId && this.amount != null && this.amount > 0;
  }

  save(): void {
    if (!this.valid) return;
    const req: CreateBudgetGoalRequest = { categoryId: this.categoryId, amount: this.amount! };
    this.dialogRef.close(req);
  }
}
