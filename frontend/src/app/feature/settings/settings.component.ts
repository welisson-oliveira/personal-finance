import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { SettingsService } from './settings.service';
import { AuthService } from '../../core/auth/auth.service';
import { CurrencyMaskDirective } from '../../shared/currency-mask/currency-mask.directive';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    CurrencyMaskDirective,
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent implements OnInit {
  loading = true;
  saving = false;
  monthlyNetIncome: number | null = null;

  constructor(
    private settingsService: SettingsService,
    private auth: AuthService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.settingsService.getMe().subscribe({
      next: (user) => {
        this.monthlyNetIncome = user.monthlyNetIncome ?? null;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.snackBar.open('Erro ao carregar configurações.', 'Fechar', { duration: 3000 });
      },
    });
  }

  save(): void {
    if (this.monthlyNetIncome != null && this.monthlyNetIncome < 0) {
      this.snackBar.open('O salário líquido não pode ser negativo.', 'Fechar', { duration: 3000 });
      return;
    }
    this.saving = true;
    this.settingsService.updateProfile(this.monthlyNetIncome).subscribe({
      next: (user) => {
        this.auth.updateStoredUser(user);
        this.saving = false;
        this.snackBar.open('Configurações salvas.', 'Fechar', { duration: 2500 });
      },
      error: () => {
        this.saving = false;
        this.snackBar.open('Erro ao salvar configurações.', 'Fechar', { duration: 3000 });
      },
    });
  }
}
