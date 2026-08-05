import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatRadioModule } from '@angular/material/radio';

export type PropagateScope = 'ALL' | 'FUTURE' | 'CURRENT';

@Component({
  selector: 'app-propagate-scope-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatButtonModule, MatRadioModule],
  templateUrl: './propagate-scope-dialog.component.html',
  styleUrl: './propagate-scope-dialog.component.scss',
})
export class PropagateScopeDialogComponent {
  scope: PropagateScope = 'CURRENT';

  constructor(private dialogRef: MatDialogRef<PropagateScopeDialogComponent>) {}

  confirm(): void {
    this.dialogRef.close(this.scope);
  }

  cancel(): void {
    this.dialogRef.close(undefined);
  }
}
