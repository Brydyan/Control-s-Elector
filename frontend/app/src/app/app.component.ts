import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { AuthStore } from './features/auth/services/auth.store';
import { PinUnlockModalComponent } from './shared/components/pin-unlock-modal/pin-unlock-modal.component';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  imports: [CommonModule, RouterOutlet, PinUnlockModalComponent],
})
export class AppComponent {
  authStore = inject(AuthStore);
}
