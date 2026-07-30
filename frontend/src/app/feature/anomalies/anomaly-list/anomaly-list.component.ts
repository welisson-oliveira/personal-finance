import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AnomalyService } from '../anomaly.service';
import { Anomaly } from '../../../core/models/anomaly.model';

@Component({
  selector: 'app-anomaly-list',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatSlideToggleModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatSnackBarModule,
  ],
  templateUrl: './anomaly-list.component.html',
  styleUrl: './anomaly-list.component.scss',
})
export class AnomalyListComponent implements OnInit {
  anomalies: Anomaly[] = [];
  loading = true;
  includeResolved = false;

  readonly typeIcon: Record<string, string> = {
    AMOUNT_OUTLIER: 'trending_up',
    DUPLICATE_CHARGE: 'content_copy',
  };

  readonly statusLabel: Record<string, string> = {
    FALSE_POSITIVE: 'Falso positivo',
    ACKNOWLEDGED: 'Revisada',
  };

  constructor(
    private anomalyService: AnomalyService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.anomalyService.getAll(this.includeResolved).subscribe({
      next: (a) => {
        this.anomalies = a;
        this.loading = false;
      },
      error: () => (this.loading = false),
    });
  }

  toggleResolved(): void {
    this.includeResolved = !this.includeResolved;
    this.load();
  }

  markFalsePositive(a: Anomaly): void {
    this.feedback(a, 'FALSE_POSITIVE', 'Marcado como falso positivo.');
  }

  acknowledge(a: Anomaly): void {
    this.feedback(a, 'ACKNOWLEDGED', 'Marcado como revisado.');
  }

  reopen(a: Anomaly): void {
    this.anomalyService.reopen(a.transactionId, a.type).subscribe({
      next: () => {
        this.snackBar.open('Alerta reaberto.', 'Fechar', { duration: 3000 });
        this.load();
      },
      error: (err) => this.showError(err),
    });
  }

  private feedback(a: Anomaly, status: string, msg: string): void {
    this.anomalyService
      .submitFeedback({ transactionId: a.transactionId, type: a.type, status })
      .subscribe({
        next: () => {
          this.snackBar.open(msg, 'Fechar', { duration: 3000 });
          this.load();
        },
        error: (err) => this.showError(err),
      });
  }

  private showError(err: { error?: { message?: string } }): void {
    this.snackBar.open(err.error?.message || 'Erro ao atualizar.', 'Fechar', { duration: 4000 });
  }
}
