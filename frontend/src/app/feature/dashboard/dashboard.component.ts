import { Component, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DashboardService } from './dashboard.service';
import { DashboardResponse } from '../../core/models/dashboard.model';
import { PeriodService } from '../../core/services/period.service';
import { AnomalyService } from '../anomalies/anomaly.service';
import { Anomaly } from '../../core/models/anomaly.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  data: DashboardResponse | null = null;
  loading = true;
  errorMessage = '';
  alerts: Anomaly[] = [];

  constructor(
    private dashboardService: DashboardService,
    private anomalyService: AnomalyService,
    public period: PeriodService
  ) {
    effect(() => {
      this.period.period();
      this.load();
    });
    // Anomalies are a recent-window signal (last 90 days), not month-bound.
    this.loadAlerts();
  }

  loadAlerts(): void {
    this.anomalyService.getAll(false).subscribe({
      next: (a) => (this.alerts = a),
      error: () => (this.alerts = []),
    });
  }

  load(): void {
    this.loading = true;
    this.errorMessage = '';
    this.dashboardService.getMonthly(this.period.year(), this.period.month()).subscribe({
      next: (d) => {
        this.data = d;
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'Não foi possível carregar o dashboard.';
        this.loading = false;
      },
    });
  }

  fmt(val: number | null | undefined): string {
    if (val == null) return 'R$ 0,00';
    return val.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  clamp(val: number): number {
    return Math.min(100, Math.max(0, val ?? 0));
  }

  hasBase(): boolean {
    return (this.data?.rendaBase ?? 0) > 0;
  }

  /** Which buckets are expanded to show their category drill-down (50/30/20 + entradas). */
  expanded: { ess: boolean; nao: boolean; inv: boolean; ent: boolean } = {
    ess: false,
    nao: false,
    inv: false,
    ent: false,
  };

  toggle(bucket: 'ess' | 'nao' | 'inv' | 'ent'): void {
    this.expanded[bucket] = !this.expanded[bucket];
  }

  /** Target amount in R$ for a bucket (fraction of the income base): 0.5 / 0.3 / 0.2. */
  meta(fraction: number): number {
    return (this.data?.rendaBase ?? 0) * fraction;
  }

  /**
   * Gap vs target for the two expense buckets (caps): positive = folga, negative = estouro.
   * Investments are a floor, handled separately in the template.
   */
  folga(realizado: number, fraction: number): number {
    return this.meta(fraction) - realizado;
  }

  /** Share (%) of a category within its bucket total, for the drill-down bars. */
  bucketPct(total: number, bucketTotal: number): number {
    return bucketTotal > 0 ? (total / bucketTotal) * 100 : 0;
  }

  abs(val: number | null | undefined): number {
    return Math.abs(val ?? 0);
  }

  /** True when at least one insight block has content worth rendering. */
  hasAnyInsight(): boolean {
    const i = this.data?.insights;
    if (!i) return false;
    return !!(
      i.maioresGastos?.length ||
      i.recorrentes?.length ||
      i.metasEstouradas?.length ||
      i.pequenosGastos?.length ||
      i.totalMesAnterior > 0 ||
      (i.mesCorrente && i.projecaoFechamento != null)
    );
  }

  baseLabel(): string {
    if (!this.data) return '';
    // The salary floors the base every month; label it accordingly when it's the one being used.
    if (this.data.salarioEsperado > this.data.entradas) {
      return this.data.usandoSalarioPrevisto ? 'salário previsto' : 'salário configurado';
    }
    return this.data.entradas > 0 ? 'renda do mês' : 'salário configurado';
  }
}
