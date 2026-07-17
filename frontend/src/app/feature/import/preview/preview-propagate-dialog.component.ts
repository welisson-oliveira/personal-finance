import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatRadioModule } from '@angular/material/radio';
import { FormsModule } from '@angular/forms';

export type PreviewPropagateScope = 'CURRENT' | 'BATCH';

interface DialogData {
  merchant: string;
  count: number;
}

/**
 * Asks whether a classification change in the import preview should apply only to this row or to all
 * rows of the same merchant in the current import. In both cases the confirm learns a merchant rule
 * for the future (parity with the transactions list, where editing always teaches a rule).
 */
@Component({
  selector: 'app-preview-propagate-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatButtonModule, MatRadioModule],
  template: `
    <h2 mat-dialog-title>Aplicar classificação</h2>
    <mat-dialog-content>
      <p class="hint">
        Há <strong>{{ data.count }}</strong> linhas de <strong>{{ data.merchant }}</strong> nesta
        importação. Onde aplicar esta classificação?
      </p>
      <mat-radio-group [(ngModel)]="scope" class="options">
        <mat-radio-button value="CURRENT">Somente esta linha</mat-radio-button>
        <mat-radio-button value="BATCH">Todas as {{ data.count }} linhas iguais</mat-radio-button>
      </mat-radio-group>
      <p class="learn-note">Nos dois casos, o sistema aprende para as próximas importações.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="cancel()">Cancelar</button>
      <button mat-raised-button color="primary" (click)="confirm()">Aplicar</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .hint {
        margin: 0 0 12px;
      }
      .options {
        display: flex;
        flex-direction: column;
        gap: 6px;
      }
      .learn-note {
        margin: 12px 0 0;
        font-size: 0.8rem;
        color: #78909c;
      }
    `,
  ],
})
export class PreviewPropagateDialogComponent {
  scope: PreviewPropagateScope = 'BATCH';

  constructor(
    private dialogRef: MatDialogRef<PreviewPropagateDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: DialogData
  ) {}

  confirm(): void {
    this.dialogRef.close(this.scope);
  }

  cancel(): void {
    this.dialogRef.close(undefined);
  }
}
