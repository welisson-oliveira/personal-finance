import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { KnownPerson } from '../../../core/models/known-person.model';

@Component({
  selector: 'app-known-person-form-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data ? 'Editar' : 'Nova' }} Pessoa Conhecida</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Nome</mat-label>
          <input matInput formControlName="name" />
          <mat-error *ngIf="form.get('name')?.hasError('required')">Nome é obrigatório</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Relacionamento</mat-label>
          <mat-select formControlName="relationship">
            <mat-option value="HOUSE_MEMBER">Morador da Casa</mat-option>
            <mat-option value="FAMILY">Família</mat-option>
            <mat-option value="FRIEND">Amigo(a)</mat-option>
            <mat-option value="OTHER">Outro</mat-option>
          </mat-select>
          <mat-error *ngIf="form.get('relationship')?.hasError('required')"
            >Relacionamento é obrigatório</mat-error
          >
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Tipo de Entrada Padrão</mat-label>
          <mat-select formControlName="defaultIncomeType">
            <mat-option value="REIMBURSEMENT">Reembolso (não conta como receita)</mat-option>
            <mat-option value="INCOME">Receita (renda real)</mat-option>
            <mat-option value="OWN_TRANSFER">Transferência Própria (ignorar)</mat-option>
            <mat-option value="ALWAYS_REVIEW">Sempre Revisar</mat-option>
          </mat-select>
          <mat-error *ngIf="form.get('defaultIncomeType')?.hasError('required')"
            >Tipo é obrigatório</mat-error
          >
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Rótulo Padrão (opcional)</mat-label>
          <input matInput formControlName="defaultLabel" placeholder="Ex: Reembolso - Aluguel" />
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
  ],
})
export class KnownPersonFormDialogComponent {
  form: FormGroup;

  constructor(
    public dialogRef: MatDialogRef<KnownPersonFormDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: KnownPerson | null,
    private fb: FormBuilder
  ) {
    this.form = this.fb.group({
      name: [data?.name ?? '', Validators.required],
      relationship: [data?.relationship ?? 'HOUSE_MEMBER', Validators.required],
      defaultIncomeType: [data?.defaultIncomeType ?? 'REIMBURSEMENT', Validators.required],
      defaultLabel: [data?.defaultLabel ?? ''],
    });
  }

  save(): void {
    if (this.form.valid) this.dialogRef.close(this.form.value);
  }
}
