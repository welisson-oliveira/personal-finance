import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { ImportService } from '../import.service';
import { ImportSessionResponse } from '../../../core/models/import.model';

interface MonthGroup {
  label: string;
  sessions: ImportSessionResponse[];
}

@Component({
  selector: 'app-import-history',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatDividerModule,
  ],
  templateUrl: './import-history.component.html',
  styleUrl: './import-history.component.scss',
})
export class ImportHistoryComponent implements OnInit {
  groups: MonthGroup[] = [];
  loading = true;

  constructor(
    private importService: ImportService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.importService.getHistory().subscribe({
      next: (sessions) => {
        this.groups = this.groupByMonth(sessions);
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  private groupByMonth(sessions: ImportSessionResponse[]): MonthGroup[] {
    const map = new Map<string, ImportSessionResponse[]>();
    for (const s of sessions) {
      const date = s.periodStart ? new Date(s.periodStart + 'T00:00:00') : new Date(s.createdAt);
      const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
      const label = date.toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });
      if (!map.has(key)) map.set(key, []);
      map
        .get(key)!
        .push({ ...s, _monthLabel: label } as ImportSessionResponse & { _monthLabel: string });
    }
    return Array.from(map.entries())
      .sort((a, b) => b[0].localeCompare(a[0]))
      .map(([, group]) => ({
        label: (group[0] as ImportSessionResponse & { _monthLabel: string })._monthLabel,
        sessions: group,
      }));
  }

  formatPeriod(session: ImportSessionResponse): string {
    if (!session.periodStart || !session.periodEnd) return '—';
    const fmt = (d: string) =>
      new Date(d + 'T00:00:00').toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
    return `${fmt(session.periodStart)} – ${fmt(session.periodEnd)}`;
  }

  statusLabel(status: string): string {
    return (
      { CONFIRMED: 'Confirmado', CANCELLED: 'Cancelado', PENDING: 'Pendente' }[status] ?? status
    );
  }

  statusColor(status: string): string {
    return { CONFIRMED: 'primary', CANCELLED: 'warn', PENDING: 'accent' }[status] ?? '';
  }

  goToTransactions(session: ImportSessionResponse): void {
    if (session.periodStart) {
      const d = new Date(session.periodStart + 'T00:00:00');
      const month = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
      this.router.navigate(['/transactions'], { queryParams: { month } });
    }
  }

  goToImport(): void {
    this.router.navigate(['/import']);
  }
}
