import { Injectable } from '@angular/core';
import { HttpBackend, HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../../environments/environment';

export interface AppConfig {
  apiUrl: string;
  sentryDsn?: string;
}

/**
 * Carga configuración desde assets/config.json en tiempo de runtime.
 * Permite cambiar la URL de API sin rebuild, solo reemplazando el archivo.
 *
 * Uso en main.ts:
 *   {
 *     provide: APP_INITIALIZER,
 *     useFactory: (configService: ConfigService) => () => configService.load(),
 *     deps: [ConfigService],
 *     multi: true
 *   }
 */
@Injectable({ providedIn: 'root' })
export class ConfigService {
  private config: AppConfig | null = null;

  /**
   * HttpClient sin interceptores: el `authInterceptor` inyecta AuthService y
   * construiria AuthApi (y otras APIs) antes de que exista la configuracion.
   */
  private readonly http: HttpClient;

  constructor(httpBackend: HttpBackend) {
    this.http = new HttpClient(httpBackend);
  }

  /**
   * Carga configuración desde assets/config.json
   * Se invoca en APP_INITIALIZER antes de que Angular inicie
   */
  async load(): Promise<void> {
    // En desarrollo la API se sirve por el proxy relativo (`proxy.conf.json` -> localhost:8080).
    // El `config.json` versionado trae la URL de PRODUCCION, asi que usarlo en dev mandaria
    // la app contra el backend real: los E2E locales verian datos de otra base.
    if (!environment.production) {
      this.config = {
        apiUrl: environment.apiUrl,
        sentryDsn: environment.sentryDsn,
      };
      return;
    }

    try {
      this.config = await firstValueFrom(
        this.http.get<AppConfig>('/assets/config.json'),
      );
    } catch (error) {
      // Fallback: usar valores por defecto si falla la carga
      console.warn(
        '[Config] Failed to load config.json, using defaults',
        error,
      );
      // Fallback: environment.prod.ts. En Cloudflare Pages la API vive en otro
      // origen (Railway); una ruta relativa iria a Pages y recibiria 405.
      this.config = {
        apiUrl: environment.apiUrl,
        sentryDsn: environment.sentryDsn,
      };
    }
  }

  /**
   * Retorna la configuración cargada
   * @throws Error si se llama antes de que load() termine
   */
  getConfig(): AppConfig {
    if (!this.config) {
      throw new Error(
        'ConfigService not initialized. Make sure ConfigService.load() was called in APP_INITIALIZER.',
      );
    }
    return this.config;
  }

  /**
   * Acceso directo a apiUrl (el valor más usado)
   * Throws if ConfigService not initialized
   */
  getApiUrl(): string {
    return this.getConfig().apiUrl;
  }

  /**
   * Acceso seguro a apiUrl con fallback (para tests)
   * No throws si ConfigService no está inicializado
   */
  getApiUrlSafe(): string {
    if (!this.config) {
      // Antes de load(): el valor del build (prod: API de Railway, dev: proxy).
      return environment.apiUrl;
    }
    return this.config.apiUrl;
  }

  /**
   * Acceso directo a sentryDsn
   */
  getSentryDsn(): string {
    return this.getConfig().sentryDsn || '';
  }
}
