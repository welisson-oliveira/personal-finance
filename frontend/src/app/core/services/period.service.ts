import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'selected_period';

const MONTH_LABELS = [
  'Janeiro',
  'Fevereiro',
  'Março',
  'Abril',
  'Maio',
  'Junho',
  'Julho',
  'Agosto',
  'Setembro',
  'Outubro',
  'Novembro',
  'Dezembro',
];

export interface Period {
  year: number;
  month: number;
}

@Injectable({ providedIn: 'root' })
export class PeriodService {
  private readonly state = signal<Period>(this.load());
  readonly period = this.state.asReadonly();

  readonly monthLabels = MONTH_LABELS;

  year(): number {
    return this.state().year;
  }

  month(): number {
    return this.state().month;
  }

  monthString(): string {
    const { year, month } = this.state();
    return `${year}-${String(month).padStart(2, '0')}`;
  }

  label(): string {
    const { year, month } = this.state();
    return `${MONTH_LABELS[month - 1]} ${year}`;
  }

  set(year: number, month: number): void {
    const period = { year, month };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(period));
    this.state.set(period);
  }

  setFromMonthString(value: string): void {
    const [year, month] = value.split('-').map(Number);
    if (year && month >= 1 && month <= 12) {
      this.set(year, month);
    }
  }

  isCurrentMonth(): boolean {
    const now = new Date();
    return this.year() === now.getFullYear() && this.month() === now.getMonth() + 1;
  }

  goToCurrent(): void {
    const now = new Date();
    this.set(now.getFullYear(), now.getMonth() + 1);
  }

  prev(): void {
    const { year, month } = this.state();
    if (month === 1) this.set(year - 1, 12);
    else this.set(year, month - 1);
  }

  next(): void {
    const { year, month } = this.state();
    if (month === 12) this.set(year + 1, 1);
    else this.set(year, month + 1);
  }

  private load(): Period {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      try {
        const parsed = JSON.parse(raw) as Period;
        if (parsed.year && parsed.month >= 1 && parsed.month <= 12) return parsed;
      } catch {
        // fall through to current month
      }
    }
    const now = new Date();
    return { year: now.getFullYear(), month: now.getMonth() + 1 };
  }
}
