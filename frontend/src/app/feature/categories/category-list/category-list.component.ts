import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { CategoryService } from '../../../core/services/category.service';
import { Category } from '../../../core/models/category.model';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { CategoryFormDialogComponent } from '../category-form-dialog/category-form-dialog.component';

@Component({
  selector: 'app-category-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatListModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatDialogModule,
  ],
  templateUrl: './category-list.component.html',
  styleUrl: './category-list.component.scss',
})
export class CategoryListComponent implements OnInit {
  categories: Category[] = [];
  loading = true;

  /** Categories grouped as a tree: each top-level with its (alphabetical) subcategories. */
  get tree(): { parent: Category; children: Category[] }[] {
    const byName = (a: Category, b: Category) => a.name.localeCompare(b.name);
    return this.categories
      .filter((c) => !c.parentId)
      .sort(byName)
      .map((parent) => ({
        parent,
        children: this.categories.filter((c) => c.parentId === parent.id).sort(byName),
      }));
  }

  /** Opens the create dialog with the parent pre-selected (add a subcategory under it). */
  addSubcategory(parent: Category): void {
    this.dialog
      .open(CategoryFormDialogComponent, {
        width: '420px',
        data: { id: '', name: '', global: false, parentId: parent.id } as Category,
      })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.categoryService.create({ ...result, parentId: parent.id }).subscribe({
            next: () => {
              this.snackBar.open('Subcategoria criada!', 'Fechar', { duration: 3000 });
              this.load();
            },
            error: (err) =>
              this.snackBar.open(err.error?.message || 'Erro ao criar.', 'Fechar', {
                duration: 4000,
              }),
          });
        }
      });
  }

  constructor(
    private categoryService: CategoryService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.categoryService.getAll().subscribe({
      next: (cats) => {
        this.categories = cats;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  openCreate(): void {
    this.dialog
      .open(CategoryFormDialogComponent, { width: '420px', data: null })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.categoryService.create(result).subscribe({
            next: () => {
              this.snackBar.open('Categoria criada!', 'Fechar', { duration: 3000 });
              this.load();
            },
            error: (err) =>
              this.snackBar.open(err.error?.message || 'Erro ao criar.', 'Fechar', {
                duration: 4000,
              }),
          });
        }
      });
  }

  openEdit(cat: Category): void {
    if (cat.global) return;
    this.dialog
      .open(CategoryFormDialogComponent, { width: '420px', data: cat })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.categoryService.update(cat.id, result).subscribe({
            next: () => {
              this.snackBar.open('Categoria atualizada!', 'Fechar', { duration: 3000 });
              this.load();
            },
            error: (err) =>
              this.snackBar.open(err.error?.message || 'Erro ao atualizar.', 'Fechar', {
                duration: 4000,
              }),
          });
        }
      });
  }

  confirmDelete(cat: Category): void {
    if (cat.global) return;
    this.dialog
      .open(ConfirmDialogComponent, {
        width: '360px',
        data: { message: `Excluir categoria "${cat.name}"?` },
      })
      .afterClosed()
      .subscribe((confirmed: boolean) => {
        if (confirmed) {
          this.categoryService.delete(cat.id).subscribe({
            next: () => {
              this.snackBar.open('Categoria excluída.', 'Fechar', { duration: 3000 });
              this.load();
            },
            error: (err) =>
              this.snackBar.open(err.error?.message || 'Erro ao excluir.', 'Fechar', {
                duration: 4000,
              }),
          });
        }
      });
  }
}
