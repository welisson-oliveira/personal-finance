import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { Category } from '../../../core/models/category.model';

@Component({
  selector: 'app-category-form-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data ? 'Editar' : 'Nova' }} Categoria</h2>
    <mat-dialog-content>
      <!-- Live preview -->
      <div class="preview">
        <span class="preview-label">Pré-visualização</span>
        <div class="preview-chip" [style.background]="tint(form.value.color)">
          <mat-icon [style.color]="form.value.color || '#555'">{{
            form.value.icon || 'label'
          }}</mat-icon>
          <span class="preview-name">{{ form.value.name || 'Nome da categoria' }}</span>
        </div>
      </div>

      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Nome</mat-label>
          <input matInput formControlName="name" cdkFocusInitial />
          <mat-error *ngIf="form.get('name')?.hasError('required')">Nome é obrigatório</mat-error>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Ícone (Material Icon)</mat-label>
          <input matInput formControlName="icon" placeholder="Ex: shopping_cart" />
        </mat-form-field>

        <div class="picker-label">Ícones sugeridos</div>
        <div class="icon-grid">
          @for (ic of suggestedIcons; track ic) {
            <button
              type="button"
              class="icon-btn"
              [class.selected]="form.value.icon === ic"
              (click)="selectIcon(ic)"
            >
              <mat-icon>{{ ic }}</mat-icon>
            </button>
          }
        </div>

        <div class="picker-label">Cor</div>
        <div class="color-grid">
          @for (c of palette; track c) {
            <button
              type="button"
              class="color-swatch"
              [class.selected]="form.value.color === c"
              [style.background]="c"
              (click)="selectColor(c)"
              [title]="c"
            ></button>
          }
        </div>
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
    `
      .dialog-form {
        display: flex;
        flex-direction: column;
        gap: 8px;
        padding-top: 8px;
        min-width: 340px;
      }
      .full-width {
        width: 100%;
      }
      .preview {
        display: flex;
        flex-direction: column;
        gap: 6px;
        margin-bottom: 12px;
      }
      .preview-label,
      .picker-label {
        font-size: 0.75rem;
        color: #888;
        font-weight: 600;
      }
      .preview-chip {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        padding: 8px 14px;
        border-radius: 20px;
        align-self: flex-start;
        max-width: 100%;
      }
      .preview-name {
        font-weight: 600;
        color: #333;
      }
      .icon-grid {
        display: grid;
        grid-template-columns: repeat(8, 1fr);
        gap: 4px;
      }
      .icon-btn {
        display: flex;
        align-items: center;
        justify-content: center;
        border: 1px solid rgba(0, 0, 0, 0.12);
        background: transparent;
        border-radius: 8px;
        padding: 6px;
        cursor: pointer;
        color: #555;
      }
      .icon-btn.selected {
        border-color: #3f51b5;
        background: rgba(63, 81, 181, 0.1);
        color: #3f51b5;
      }
      .color-grid {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
      }
      .color-swatch {
        width: 28px;
        height: 28px;
        border-radius: 50%;
        border: 2px solid transparent;
        cursor: pointer;
      }
      .color-swatch.selected {
        border-color: #333;
        box-shadow: 0 0 0 2px #fff inset;
      }
    `,
  ],
})
export class CategoryFormDialogComponent {
  form: FormGroup;

  suggestedIcons = [
    'shopping_cart',
    'restaurant',
    'local_gas_station',
    'home',
    'health_and_safety',
    'directions_car',
    'pets',
    'fitness_center',
    'school',
    'flight',
    'sports_esports',
    'subscriptions',
    'checkroom',
    'bolt',
    'savings',
    'card_giftcard',
  ];

  palette = [
    '#e53935',
    '#d81b60',
    '#8e24aa',
    '#5e35b1',
    '#3949ab',
    '#1e88e5',
    '#039be5',
    '#00acc1',
    '#00897b',
    '#43a047',
    '#7cb342',
    '#fdd835',
    '#fb8c00',
    '#f4511e',
    '#6d4c41',
    '#757575',
  ];

  constructor(
    public dialogRef: MatDialogRef<CategoryFormDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: Category | null,
    private fb: FormBuilder
  ) {
    this.form = this.fb.group({
      name: [data?.name ?? '', Validators.required],
      icon: [data?.icon ?? ''],
      color: [data?.color ?? ''],
    });
  }

  selectIcon(icon: string): void {
    this.form.patchValue({ icon });
  }

  selectColor(color: string): void {
    // Toggle off when clicking the already-selected swatch
    this.form.patchValue({ color: this.form.value.color === color ? '' : color });
  }

  tint(color: string | null | undefined): string {
    return color ? `${color}22` : '#f0f0f0';
  }

  save(): void {
    if (this.form.valid) this.dialogRef.close(this.form.value);
  }
}
