import { Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelect, MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Category } from '../../core/models/category.model';
import { CategoryService } from '../../core/services/category.service';
import { CategoryFormDialogComponent } from '../../feature/categories/category-form-dialog/category-form-dialog.component';

/**
 * Category picker with a search box inside the dropdown panel. Two-way bound via [(value)]
 * (empty string means "no category"). Options show the category icon.
 *
 * The panel also offers "➕ Nova categoria…", which opens the category form dialog, persists the
 * new category and auto-selects it. Parents should keep their local list in sync via
 * `(categoryCreated)="categories = [...categories, $event]"` so the new option renders immediately.
 */
@Component({
  selector: 'app-category-select',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatIconModule,
    MatDialogModule,
    MatSnackBarModule,
  ],
  templateUrl: './category-select.component.html',
  styleUrl: './category-select.component.scss',
})
export class CategorySelectComponent {
  @Input() categories: Category[] = [];
  @Input() label = 'Categoria';
  @Input() value: string | undefined = '';
  /** When true, renders a bare (form-field-less) select for use inside compact rows/tables. */
  @Input() compact = false;
  @Output() valueChange = new EventEmitter<string | undefined>();
  /** Emitted (before valueChange) when a category is created inline, so parents can sync their list. */
  @Output() categoryCreated = new EventEmitter<Category>();

  @ViewChild(MatSelect) private select?: MatSelect;
  @ViewChild('searchInput') private searchInput?: ElementRef<HTMLInputElement>;

  search = '';

  constructor(
    private dialog: MatDialog,
    private categoryService: CategoryService,
    private snackBar: MatSnackBar
  ) {}

  get filtered(): Category[] {
    const q = this.search.trim().toLowerCase();
    if (!q) return this.categories;
    return this.categories.filter((c) => c.name.toLowerCase().includes(q));
  }

  onSelectionChange(value: string): void {
    this.value = value;
    this.valueChange.emit(value);
  }

  onPanelClosed(): void {
    this.search = '';
  }

  /** Move focus to the in-panel search box when the dropdown opens (after Material's own focus). */
  onOpened(): void {
    setTimeout(() => this.searchInput?.nativeElement.focus());
  }

  createNew(): void {
    this.select?.close();
    this.dialog
      .open(CategoryFormDialogComponent, { data: null, width: '440px' })
      .afterClosed()
      .subscribe((result) => {
        if (!result) return;
        this.categoryService.create(result).subscribe({
          next: (created) => {
            // Emit first so the parent's list already holds the new option before we select it.
            this.categoryCreated.emit(created);
            this.value = created.id;
            this.valueChange.emit(created.id);
            this.snackBar.open(`Categoria "${created.name}" criada.`, 'Fechar', { duration: 2500 });
          },
          error: (err) => {
            this.snackBar.open(err.error?.message || 'Erro ao criar categoria.', 'Fechar', {
              duration: 4000,
            });
          },
        });
      });
  }
}
