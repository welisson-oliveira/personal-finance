import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { CategoryService } from '../../../core/services/category.service';
import { Category } from '../../../core/models/category.model';
import { CreateMerchantRuleRequest, MerchantRule } from '../../../core/models/merchant-rule.model';

@Component({
  selector: 'app-merchant-rule-form-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatIconModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ title }}</h2>
    <mat-dialog-content>
      @if (data?.global) {
        <p class="override-note">
          <mat-icon inline>info</mat-icon>
          Esta é uma regra do sistema. Ao salvar, criamos uma <strong>regra pessoal</strong> que a
          sobrepõe — a original continua disponível para outros.
        </p>
      }
      @if (data && !data.global) {
        <p class="match-hint">
          Casa com: <code>{{ data.normalizedName }}</code>
        </p>
      }
      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Estabelecimento</mat-label>
          <input matInput formControlName="merchantName" placeholder="Ex: Padaria do Zé" />
          <mat-hint>Texto do lançamento que a regra deve reconhecer</mat-hint>
          <mat-error *ngIf="form.get('merchantName')?.hasError('required')"
            >Estabelecimento é obrigatório</mat-error
          >
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Tipo</mat-label>
          <mat-select formControlName="type">
            <mat-option value="EXPENSE">Despesa</mat-option>
            <mat-option value="INCOME">Receita</mat-option>
            <mat-option value="INVESTMENT">Investimento</mat-option>
          </mat-select>
        </mat-form-field>

        @if (isExpense || isIncome) {
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Categoria</mat-label>
            <mat-select formControlName="categoryId">
              <mat-option [value]="null">Nenhuma</mat-option>
              @for (cat of categories; track cat.id) {
                <mat-option [value]="cat.id">{{ cat.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }

        @if (isExpense) {
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Grupo de orçamento (50/30/20)</mat-label>
            <mat-select formControlName="expenseType">
              <mat-option value="ESSENTIAL">Essencial (50%)</mat-option>
              <mat-option value="NON_ESSENTIAL">Não Essencial (30%)</mat-option>
            </mat-select>
          </mat-form-field>
        }

        @if (isInvestment) {
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Direção do investimento</mat-label>
            <mat-select formControlName="investmentDirection">
              <mat-option value="CONTRIBUTION">Aporte</mat-option>
              <mat-option value="REDEMPTION">Resgate</mat-option>
            </mat-select>
            <mat-error *ngIf="form.get('investmentDirection')?.hasError('required')"
              >Direção é obrigatória</mat-error
            >
          </mat-form-field>
        }

        @if (isExpense || isIncome) {
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Subcategoria (opcional)</mat-label>
            <input matInput formControlName="subcategory" />
          </mat-form-field>
        }

        <mat-checkbox formControlName="ignored" class="ignore-check">
          Ignorar nos cálculos (ex.: transferência entre contas próprias)
        </mat-checkbox>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="dialogRef.close()">Cancelar</button>
      <button mat-raised-button color="primary" [disabled]="form.invalid" (click)="save()">
        Salvar
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    '.dialog-form { display: flex; flex-direction: column; gap: 8px; padding-top: 8px; min-width: 380px; }',
    '.full-width { width: 100%; }',
    '.ignore-check { margin-top: 4px; }',
    '.override-note { display: flex; gap: 6px; align-items: flex-start; background: rgba(63,81,181,0.08); color: #283593; padding: 10px 12px; border-radius: 8px; font-size: 0.85rem; margin: 0 0 8px; }',
    '.match-hint { font-size: 0.8rem; color: #777; margin: 0 0 4px; }',
    '.match-hint code { background: rgba(0,0,0,0.06); padding: 1px 5px; border-radius: 4px; }',
  ],
})
export class MerchantRuleFormDialogComponent implements OnInit {
  form: FormGroup;
  categories: Category[] = [];
  title: string;

  constructor(
    public dialogRef: MatDialogRef<MerchantRuleFormDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: MerchantRule | null,
    private fb: FormBuilder,
    private categoryService: CategoryService
  ) {
    this.title = !data
      ? 'Nova regra'
      : data.global
        ? 'Personalizar regra do sistema'
        : 'Editar regra';
    this.form = this.fb.group({
      merchantName: [data?.merchantName ?? '', Validators.required],
      type: [data?.type ?? 'EXPENSE', Validators.required],
      categoryId: [data?.categoryId ?? null],
      expenseType: [data?.expenseType ?? 'NON_ESSENTIAL'],
      investmentDirection: [data?.investmentDirection ?? null],
      subcategory: [data?.subcategory ?? ''],
      ignored: [data?.ignored ?? false],
    });
    this.applyTypeValidators(this.form.value.type);
    this.form.get('type')!.valueChanges.subscribe((t) => this.applyTypeValidators(t));
  }

  ngOnInit(): void {
    this.categoryService.getAll().subscribe((cats) => (this.categories = cats));
  }

  get isExpense(): boolean {
    return this.form.value.type === 'EXPENSE';
  }
  get isIncome(): boolean {
    return this.form.value.type === 'INCOME';
  }
  get isInvestment(): boolean {
    return this.form.value.type === 'INVESTMENT';
  }

  private applyTypeValidators(type: string): void {
    const direction = this.form.get('investmentDirection')!;
    direction.setValidators(type === 'INVESTMENT' ? [Validators.required] : []);
    direction.updateValueAndValidity({ emitEvent: false });
  }

  save(): void {
    if (this.form.invalid) return;
    const v = this.form.value;
    const req: CreateMerchantRuleRequest = {
      merchantName: v.merchantName,
      type: v.type,
      ignored: v.ignored,
      // Keep the payload coherent with the type (backend also normalizes defensively).
      categoryId: v.type === 'INVESTMENT' ? null : (v.categoryId ?? null),
      subcategory: v.type === 'INVESTMENT' ? null : v.subcategory || null,
      expenseType: v.type === 'EXPENSE' ? v.expenseType : null,
      investmentDirection: v.type === 'INVESTMENT' ? v.investmentDirection : null,
    };
    this.dialogRef.close(req);
  }
}
