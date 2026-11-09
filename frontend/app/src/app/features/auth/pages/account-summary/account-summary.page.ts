import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Button } from 'primeng/button';

/**
 * Datos de la cuenta que la pantalla de activacion deja en el estado de la
 * navegacion (ODD #504, B.5.2).
 */
export interface ResumenCuenta {
  /** Nombre y apellido de la persona. */
  nombre: string;
  /** Login: el correo de Control con el que entra al sistema. */
  email: string;
  /** Correo de contacto; null si la cuenta se creo sin correo. */
  emailPersonal: string | null;
  /** Rol asignado. */
  rolNombre: string;
}

/**
 * Como se le dice a cada rol en la pantalla.
 *
 * <p>El backend manda el nombre tecnico del rol; la persona lee el del rol que
 * conoce.
 */
const ROLES: Record<string, string> = {
  MASTER: 'Master',
  COORDINATOR: 'Coordinador',
  OBSERVER: 'Observador Electoral',
  ADMIN: 'Administrador',
};

/**
 * Resumen de la cuenta recien activada (ODD #504, B.5.2).
 *
 * <p>Es la ultima pantalla del flujo de invitacion: la persona ya eligio su
 * contraseña y lo que falta es mostrarle con que credencial entra y que la
 * invite a hacerlo. Los datos llegan desde la pantalla de activacion, en el
 * estado de la navegacion: si no estan, la pantalla lo dice y no inventa
 * nada.
 */
@Component({
  selector: 'app-account-summary',
  standalone: true,
  imports: [Button],
  templateUrl: './account-summary.page.html',
  styleUrl: './account-summary.page.scss',
})
export class AccountSummaryPage {
  private readonly router = inject(Router);

  /** La cuenta activada, si el navegador la trae del paso anterior. */
  readonly cuenta = signal<ResumenCuenta | null>(this.leerDelHistorial());

  /** El rol, con el nombre con el que lo conoce la persona. */
  readonly rolLegible = computed(() => {
    const rol = this.cuenta()?.rolNombre;
    if (!rol) return '';
    return ROLES[rol] ?? rol;
  });

  /**
   * El usuario de Control sin dominio: "juagarobs@control.ttcs" -> "juagarobs".
   *
   * <p>Es el usuario que la persona reconoce; el correo completo es con el que
   * entra (ODD #504, A.5.2 / B.5.2).
   */
  readonly usuarioCorto = computed(() => {
    const correo = this.cuenta()?.email;
    if (!correo) return null;
    const arroba = correo.indexOf('@');
    return arroba > 0 ? correo.slice(0, arroba) : correo;
  });

  /** Vuelve al login: la cuenta ya esta activa, solo falta entrar. */
  irAlLogin(): void {
    this.router.navigateByUrl('/login');
  }

  /**
   * Lee la cuenta del historial de la navegacion.
   *
   * <p>El router de Angular guarda el `state` de la navegacion en el historial
   * del navegador, asi que los datos siguen ahi aunque la persona recargue la
   * pestana.
   */
  private leerDelHistorial(): ResumenCuenta | null {
    const estado = (globalThis.history?.state ?? {}) as {
      cuenta?: ResumenCuenta;
    };
    return estado.cuenta ?? null;
  }
}
