import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';

import { AuthStore } from '../../auth/services/auth.store';
import { LegalService } from './legal.service';

/**
 * Decide si el usuario tiene que aceptar la version vigente de los terminos.
 *
 * <p>Es la pieza que responde a la pregunta "¿puede este usuario seguir
 * trabajando?", y por eso tiene tres reglas:
 *
 * <ul>
 *   <li>La version que vale es la del <b>servidor</b>. El frontend compara, no
 *       decide: si el cliente mandara la version, podria aceptar "1.0" con el
 *       "1.1" ya publicado.
 *   <li>Si no se puede saber la version (backend caido, sin red), se deja
 *       pasar. Bloquear a todo el equipo electoral porque el endpoint de la
 *       version fallo seria peor que dejar pasar a alguien que aun no ha
 *       aceptado: la aceptacion se vuelve a exigir en cuanto el backend
 *       responde.
 *   <li>La aceptacion se recuerda en {@code sessionStorage}: el modal aparece
 *       una vez por sesion de navegador, no en cada navegacion.
 * </ul>
 */
@Injectable({ providedIn: 'root' })
export class TermsAcceptanceService {
  private readonly legal = inject(LegalService);
  private readonly store = inject(AuthStore);

  /** Clave del record de aceptacion en `sessionStorage`. */
  static readonly CLAVE = 'ce.terms.accepted';

  /**
   * Version vigente cacheada en memoria.
   *
   * El guard la consulta en cada navegacion a ruta protegida: sin cache cada
   * cambio de pantalla seria un GET extra al backend.
   */
  private versionCache: string | null = null;

  /**
   * ¿El usuario tiene pendiente aceptar la version vigente?
   */
  requiereAceptacion(): Observable<boolean> {
    return this.versionVigente().pipe(
      map((version) => this.pendienteDeAceptar(version)),
      catchError(() => of(false)),
    );
  }

  /**
   * Version vigente de los documentos legales.
   *
   * Se cachea en memoria: dentro de una sesion la version no cambia (si
   * cambiara, es porque se redespliega el backend, y entonces la sesion
   * tambien se reinicia).
   */
  versionVigente(): Observable<string> {
    if (this.versionCache !== null) {
      return of(this.versionCache);
    }
    return this.legal.versionVigente().pipe(
      tap((version) => {
        this.versionCache = version;
      }),
    );
  }

  /**
   * Registra la aceptacion y deja constancia de ella en la sesion.
   *
   * @return la version que quedo aceptada
   */
  aceptar(): Observable<string> {
    return this.legal.aceptarTerminos().pipe(
      tap((version) => {
        this.versionCache = version;
        this.marcarAceptadaEnSesion(version);
        this.store.setAcceptedTermsVersion(version);
      }),
    );
  }

  /**
   * ¿La version dada sigue pendiente para este usuario?
   *
   * Es pura a proposito: la logica de decision se prueba sin red y sin
   * backend.
   */
  pendienteDeAceptar(version: string): boolean {
    if (!version) {
      // Sin version no hay nada que comparar: no se bloquea a nadie.
      return false;
    }

    if (this.aceptadaEnEstaSesion() === version) {
      return false;
    }

    const aceptada = this.store.user()?.acceptedTermsVersion ?? null;
    return aceptada !== version;
  }

  /** Version aceptada en esta sesion de navegador, si la hay. */
  aceptadaEnEstaSesion(): string | null {
    try {
      return (
        globalThis.sessionStorage?.getItem(TermsAcceptanceService.CLAVE) ?? null
      );
    } catch {
      // Modo privado de algunos navegadores: sin cache, el modal vuelve a
      // aparecer. Es peor que romper la app.
      return null;
    }
  }

  /** Deja constancia de la aceptacion en la sesion de navegador. */
  marcarAceptadaEnSesion(version: string): void {
    try {
      globalThis.sessionStorage?.setItem(TermsAcceptanceService.CLAVE, version);
    } catch {
      // Ver `aceptadaEnEstaSesion`: el fallo se ignora a proposito.
    }
  }
}
