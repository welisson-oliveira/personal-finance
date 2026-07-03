import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { ReviewQueueItem, ResolveReviewRequest } from '../../../core/models/review.model';
import { Category } from '../../../core/models/category.model';

interface DialogData {
  item: ReviewQueueItem;
  categories: Category[];
}

@Component({
  selector: 'app-resolve-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
  ],
  templateUrl: './resolve-dialog.component.html',
  styleUrl: './resolve-dialog.component.scss',
})
export class ResolveDialogComponent {
  categoryId: string | undefined;
  budgetGroup = 'NON_ESSENTIAL';
  merchantName: string;
  transactionNotes = '';

  budgetGroups = [
    { value: 'ESSENTIAL', label: 'Essencial (50%)' },
    { value: 'NON_ESSENTIAL', label: 'Não Essencial (30%)' },
    { value: 'INVESTMENT', label: 'Investimento (20%)' },
  ];

  constructor(
    public dialogRef: MatDialogRef<ResolveDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: DialogData
  ) {
    this.categoryId = data.item.suggestedCategoryId;
    this.merchantName = data.item.normalizedDescription || data.item.rawDescription;
  }

  confirm(): void {
    const req: ResolveReviewRequest = {
      categoryId: this.categoryId,
      budgetGroup: this.budgetGroup,
      merchantName: this.merchantName,
      transactionNotes: this.transactionNotes || undefined,
    };
    this.dialogRef.close(req);
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
