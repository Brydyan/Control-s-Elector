import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { AuthStore } from './features/auth/services/auth.store';
import { PinUnlockModalComponent } from './shared/components/pin-unlock-modal/pin-unlock-modal.component';
import { ToastComponent } from './shared/ui/toast/toast.component';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  imports: [
    CommonModule,
    RouterOutlet,
    PinUnlockModalComponent,
    ToastComponent,
  ],
})
export class AppComponent {
  authStore = inject(AuthStore);
}
