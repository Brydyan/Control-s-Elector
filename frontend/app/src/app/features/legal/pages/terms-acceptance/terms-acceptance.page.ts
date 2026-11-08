import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '../../../auth/services/auth.service';
import { AuthStore } from '../../../auth/services/auth.store';
import { TermsAcceptanceModalComponent } from '../../components/terms-acceptance-modal/terms-acceptance-modal.component';
import { TermsAcceptanceService } from '../../services/terms-acceptance.service';

/**
 * Pantalla de aceptacion de terminos (T-LEGAL-01.7 / T-LEGAL-01.8).
 *
 * <p>Es la ruta a la que el guard manda a quien tiene pendiente aceptar. La
 * ruta existe para que el modal tenga un lugar donde vivir: un modal no puede
 * ser una ruta, pero la pantalla que lo contiene si, y asi el boton de atras
 * del navegador no deja al usuario en un estado a medias.
 *
 * <p>El flujo es de una sola salida:
 *
 * <ul>
 *   <li>Acepta → se registra la aceptacion y se sigue al dashboard.
 *   <li>Cierra sin aceptar → se cierra la sesion y se vuelve al login.
 * </ul>
 */
@Component({
  selector: 'app-terms-acceptance-page',
  standalone: true,
  imports: [TermsAcceptanceModalComponent],
  template: `
    <div class="fondo" data-testid="terms-acceptance-page">
      <app-terms-acceptance-modal
        [visible]="true"
        [version]="version"
        (accepted)="onAccepted()"
        (closed)="onClosed()"
      ></app-terms-acceptance-modal>
    </div>
  `,
  styles: `
    .fondo {
      min-height: 100dvh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--color-surface-page);
    }
  `,
})
export class TermsAcceptancePage implements OnInit {
  private readonly terms = inject(TermsAcceptanceService);
  private readonly auth = inject(AuthService);
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);

  version: string | null = null;

  ngOnInit(): void {
    this.terms.versionVigente().subscribe({
      next: (version) => {
        this.version = version;
      },
      error: () => {
        this.version = null;
      },
    });
  }

  /**
   * Acepto: la sesion sigue y el usuario entra al sistema.
   *
   * <p>El destino depende del rol, igual que en el login: un OBSERVER no puede
   * entrar a `/dashboard` (el backend le responde 403) y su pantalla de inicio
   * son sus JRVs.
   */
  onAccepted(): void {
    const destino =
      this.store.user()?.role === 'OBSERVER'
        ? '/observer/mis-jrvs'
        : '/dashboard';
    void this.router.navigateByUrl(destino);
  }

  /**
   * Cierre sin aceptar: se cierra la sesion.
   *
   * El logout se dispara aunque falle la llamada: lo que importa es que la
   * sesion local quede limpia, y el store se limpia en `tap` del servicio.
   */
  onClosed(): void {
    this.auth.logout().subscribe({
      next: () => void this.router.navigateByUrl('/login'),
      error: () => {
        // Si el logout falla (sin red), la sesion local se limpia igual: el
        // usuario no acepto y no puede quedarse dentro.
        this.store.clear();
        void this.router.navigateByUrl('/login');
      },
    });
  }
}
