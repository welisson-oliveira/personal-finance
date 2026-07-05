import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { Category } from '../../core/models/category.model';

/**
 * Category picker with a search box inside the dropdown panel. Two-way bound via [(value)]
 * (empty string means "no category"). Options show the category icon.
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
  ],
  templateUrl: './category-select.component.html',
  styleUrl: './category-select.component.scss',
})
export class CategorySelectComponent {
  @Input() categories: Category[] = [];
  @Input() label = 'Categoria';
  @Input() value: string | undefined = '';
  @Output() valueChange = new EventEmitter<string | undefined>();

  search = '';

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
}
