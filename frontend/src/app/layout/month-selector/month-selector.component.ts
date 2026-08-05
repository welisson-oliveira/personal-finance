import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { PeriodService } from '../../core/services/period.service';

@Component({
  selector: 'app-month-selector',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatSelectModule,
    MatFormFieldModule,
  ],
  templateUrl: './month-selector.component.html',
  styleUrl: './month-selector.component.scss',
})
export class MonthSelectorComponent {
  years: number[] = [];

  constructor(public period: PeriodService) {
    const now = new Date().getFullYear();
    for (let y = now; y >= now - 3; y--) {
      this.years.push(y);
    }
  }

  onMonthChange(month: number): void {
    this.period.set(this.period.year(), month);
  }

  onYearChange(year: number): void {
    this.period.set(year, this.period.month());
  }
}
