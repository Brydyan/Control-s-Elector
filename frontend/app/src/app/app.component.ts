import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonApp, IonRouterOutlet } from '@ionic/angular/standalone';
import { AuthStore } from './core/auth/auth.store';
import { PinUnlockModalComponent } from './shared/components/pin-unlock-modal/pin-unlock-modal.component';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  imports: [CommonModule, IonApp, IonRouterOutlet, PinUnlockModalComponent],
})
export class AppComponent {
  authStore = inject(AuthStore);
}
