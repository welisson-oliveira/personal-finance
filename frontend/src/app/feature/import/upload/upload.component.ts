import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { ImportService } from '../import.service';
import { ImportPreviewResponse, ImportSessionResponse } from '../../../core/models/import.model';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';

interface MonthGroup {
  label: string;
  sessions: ImportSessionResponse[];
}

@Component({
  selector: 'app-upload',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatDividerModule,
    MatSnackBarModule,
    MatDialogModule,
  ],
  templateUrl: './upload.component.html',
  styleUrl: './upload.component.scss',
})
export class UploadComponent implements OnInit {
  selectedFile: File | null = null;
  isDragOver = false;
  loading = false;
  errorMessage = '';

  groups: MonthGroup[] = [];
  historyLoading = true;

  constructor(
    private importService: ImportService,
    private router: Router,
    private snackBar: MatSnackBar,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void {
    this.loadHistory();
  }

  loadHistory(): void {
    this.historyLoading = true;
    this.importService.getHistory().subscribe({
      next: (sessions) => {
        this.groups = this.groupByMonth(sessions);
        this.historyLoading = false;
      },
      error: () => {
        this.historyLoading = false;
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

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      this.setFile(files[0]);
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.setFile(input.files[0]);
    }
  }

  private setFile(file: File): void {
    if (file.type !== 'application/pdf') {
      this.errorMessage = 'Apenas arquivos PDF são aceitos';
      return;
    }
    this.selectedFile = file;
    this.errorMessage = '';
  }

  upload(): void {
    if (!this.selectedFile) return;
    this.loading = true;
    this.errorMessage = '';
    this.importService.parse(this.selectedFile).subscribe({
      next: (preview: ImportPreviewResponse) => {
        this.router.navigate(['/import/preview'], { state: { preview } });
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Falha ao processar o PDF';
        this.loading = false;
      },
    });
  }

  clearFile(): void {
    this.selectedFile = null;
    this.errorMessage = '';
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

  confirmDelete(session: ImportSessionResponse, event: Event): void {
    event.stopPropagation();
    const label = session.documentType === 'EXTRATO' ? 'Extrato Nubank' : 'Fatura Nubank';
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: {
        message: `Excluir "${label}" (${this.formatPeriod(session)}) e todas as ${session.transactionCount} transações geradas por ele?`,
      },
      width: '400px',
    });
    ref.afterClosed().subscribe((confirmed: boolean) => {
      if (confirmed) this.deleteSession(session.id);
    });
  }

  private deleteSession(id: string): void {
    this.importService.deleteSession(id).subscribe({
      next: () => {
        this.snackBar.open('Importação excluída.', 'Fechar', { duration: 3000 });
        this.loadHistory();
      },
      error: (err) => {
        this.snackBar.open(err.error?.message || 'Erro ao excluir.', 'Fechar', { duration: 4000 });
      },
    });
  }
}
