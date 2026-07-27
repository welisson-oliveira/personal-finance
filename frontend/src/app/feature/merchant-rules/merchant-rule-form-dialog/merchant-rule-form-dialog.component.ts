import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { CategoryService } from '../../../core/services/category.service';
import { Category } from '../../../core/models/category.model';
import { MerchantRule } from '../../../core/models/merchant-rule.model';

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
          <mat-label>Categoria</mat-label>
          <mat-select formControlName="categoryId">
            <mat-option [value]="null">Nenhuma</mat-option>
            @for (cat of categories; track cat.id) {
              <mat-option [value]="cat.id">{{ cat.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Grupo de orçamento (50/30/20)</mat-label>
          <mat-select formControlName="expenseType">
            <mat-option value="ESSENTIAL">Essencial (50%)</mat-option>
            <mat-option value="NON_ESSENTIAL">Não Essencial (30%)</mat-option>
          </mat-select>
          <mat-error *ngIf="form.get('expenseType')?.hasError('required')"
            >Grupo é obrigatório</mat-error
          >
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Subcategoria (opcional)</mat-label>
          <input matInput formControlName="subcategory" />
        </mat-form-field>
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
      categoryId: [data?.categoryId ?? null],
      expenseType: [data?.expenseType ?? 'NON_ESSENTIAL', Validators.required],
      subcategory: [data?.subcategory ?? ''],
    });
  }

  ngOnInit(): void {
    this.categoryService.getAll().subscribe((cats) => (this.categories = cats));
  }

  save(): void {
    if (this.form.valid) this.dialogRef.close(this.form.value);
  }
}
