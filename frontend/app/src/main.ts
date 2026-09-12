import { bootstrapApplication } from '@angular/platform-browser';
import {
  RouteReuseStrategy,
  provideRouter,
  withPreloading,
  PreloadAllModules,
} from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  IonicRouteStrategy,
  provideIonicAngular,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  analyticsOutline,
  cameraOutline,
  checkmarkCircleOutline,
  closeOutline,
  documentTextOutline,
  gridOutline,
  homeOutline,
  logOutOutline,
  menuOutline,
  notificationsOutline,
  peopleOutline,
  settingsOutline,
} from 'ionicons/icons';

import { routes } from './app/app.routes';
import { AppComponent } from './app/app.component';
import { authInterceptor } from './app/core/auth/auth.interceptor';

addIcons({
  'menu-outline': menuOutline,
  'close-outline': closeOutline,
  'home-outline': homeOutline,
  'document-text-outline': documentTextOutline,
  'grid-outline': gridOutline,
  'checkmark-circle-outline': checkmarkCircleOutline,
  'camera-outline': cameraOutline,
  'settings-outline': settingsOutline,
  'people-outline': peopleOutline,
  'analytics-outline': analyticsOutline,
  'log-out-outline': logOutOutline,
  'notifications-outline': notificationsOutline,
});

bootstrapApplication(AppComponent, {
  providers: [
    { provide: RouteReuseStrategy, useClass: IonicRouteStrategy },
    provideIonicAngular(),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideRouter(routes, withPreloading(PreloadAllModules)),
  ],
});
