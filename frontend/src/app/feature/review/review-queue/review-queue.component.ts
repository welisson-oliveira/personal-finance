import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { ReviewService } from '../review.service';
import { CategoryService } from '../../../core/services/category.service';
import { ReviewQueueItem, ResolveReviewRequest } from '../../../core/models/review.model';
import { Category } from '../../../core/models/category.model';
import { ResolveDialogComponent } from '../resolve-dialog/resolve-dialog.component';

@Component({
  selector: 'app-review-queue',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatDialogModule,
  ],
  templateUrl: './review-queue.component.html',
  styleUrl: './review-queue.component.scss',
})
export class ReviewQueueComponent implements OnInit {
  items: ReviewQueueItem[] = [];
  categories: Category[] = [];
  loading = true;

  constructor(
    private reviewService: ReviewService,
    private categoryService: CategoryService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadData();
  }

  private loadData(): void {
    this.loading = true;
    this.reviewService.getPending().subscribe({
      next: (items) => {
        this.items = items;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
    this.categoryService.getAll().subscribe({ next: (cats) => (this.categories = cats) });
  }

  openResolveDialog(item: ReviewQueueItem): void {
    const ref = this.dialog.open(ResolveDialogComponent, {
      width: '480px',
      data: { item, categories: this.categories },
    });

    ref.afterClosed().subscribe((result: ResolveReviewRequest | undefined) => {
      if (result) {
        this.resolve(item.id, result);
      }
    });
  }

  private resolve(id: string, req: ResolveReviewRequest): void {
    this.reviewService.resolve(id, req).subscribe({
      next: () => {
        this.items = this.items.filter((i) => i.id !== id);
        this.snackBar.open('Item classified successfully!', 'Close', { duration: 3000 });
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Failed to resolve item', 'Close', {
          duration: 4000,
        });
      },
    });
  }
}
