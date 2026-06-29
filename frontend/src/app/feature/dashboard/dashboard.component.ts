import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [MatCardModule, MatIconModule],
  template: `
    <h1>Dashboard</h1>
    <p style="color:#666">Dashboard will be implemented in Checkpoint 8.</p>
  `,
})
export class DashboardComponent {}
