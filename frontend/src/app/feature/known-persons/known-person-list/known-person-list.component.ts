import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { KnownPersonService } from '../known-person.service';
import { KnownPerson } from '../../../core/models/known-person.model';
import { KnownPersonFormDialogComponent } from '../known-person-form-dialog/known-person-form-dialog.component';

@Component({
  selector: 'app-known-person-list',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatListModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatDialogModule,
    MatTooltipModule,
  ],
  templateUrl: './known-person-list.component.html',
  styleUrl: './known-person-list.component.scss',
})
export class KnownPersonListComponent implements OnInit {
  persons: KnownPerson[] = [];
  loading = true;

  readonly relationshipLabel: Record<string, string> = {
    HOUSE_MEMBER: 'Morador',
    FAMILY: 'Família',
    FRIEND: 'Amigo(a)',
    OTHER: 'Outro',
  };

  readonly treatmentLabel: Record<string, string> = {
    INCOME: 'Receita',
    IGNORE: 'Ignorar',
    ALWAYS_REVIEW: 'Sempre Revisar',
  };

  constructor(
    private knownPersonService: KnownPersonService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.knownPersonService.getAll().subscribe({
      next: (persons) => {
        this.persons = persons;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  openCreate(): void {
    this.dialog
      .open(KnownPersonFormDialogComponent, { width: '460px', data: null })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.knownPersonService.create(result).subscribe({
            next: () => {
              this.snackBar.open('Pessoa adicionada!', 'Fechar', { duration: 3000 });
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

  openEdit(person: KnownPerson): void {
    this.dialog
      .open(KnownPersonFormDialogComponent, { width: '460px', data: person })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.knownPersonService.update(person.id, result).subscribe({
            next: () => {
              this.snackBar.open('Pessoa atualizada!', 'Fechar', { duration: 3000 });
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

  deactivate(person: KnownPerson): void {
    this.knownPersonService.deactivate(person.id).subscribe({
      next: () => {
        this.snackBar.open('Pessoa desativada.', 'Fechar', { duration: 3000 });
        this.load();
      },
      error: (err) =>
        this.snackBar.open(err.error?.message || 'Erro ao desativar.', 'Fechar', {
          duration: 4000,
        }),
    });
  }
}
