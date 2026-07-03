import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { FormsModule } from '@angular/forms';
import { ImportService } from '../import.service';
import { ImportPreviewResponse } from '../../../core/models/import.model';

@Component({
  selector: 'app-upload',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './upload.component.html',
  styleUrl: './upload.component.scss',
})
export class UploadComponent {
  documentType: 'EXTRATO' | 'FATURA' = 'EXTRATO';
  selectedFile: File | null = null;
  isDragOver = false;
  loading = false;
  errorMessage = '';

  constructor(
    private importService: ImportService,
    private router: Router
  ) {}

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
      this.errorMessage = 'Only PDF files are accepted';
      return;
    }
    this.selectedFile = file;
    this.errorMessage = '';
  }

  upload(): void {
    if (!this.selectedFile) return;
    this.loading = true;
    this.errorMessage = '';
    this.importService.parse(this.selectedFile, this.documentType).subscribe({
      next: (preview: ImportPreviewResponse) => {
        this.router.navigate(['/import/preview'], { state: { preview } });
      },
      error: (err) => {
        this.errorMessage = err.error?.message || 'Failed to parse PDF';
        this.loading = false;
      },
    });
  }

  clearFile(): void {
    this.selectedFile = null;
    this.errorMessage = '';
  }
}
