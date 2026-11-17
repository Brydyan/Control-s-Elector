import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

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

  constructor(private http: HttpClient) {}

  /**
   * Carga configuración con prioridad:
   * 1. Cloudflare Pages ENV var (NG_APP_API_URL, NG_APP_SENTRY_DSN)
   * 2. assets/config.json (dynamic, sin rebuild)
   * 3. Hardcoded fallback
   */
  async load(): Promise<void> {
    try {
      const configJson = await firstValueFrom(
        this.http.get<AppConfig>('/assets/config.json'),
      );

      // Priority: ENV vars > config.json > hardcoded
      this.config = {
        apiUrl:
          (import.meta.env['NG_APP_API_URL'] as string | undefined) ||
          configJson.apiUrl ||
          'https://api.brydyan.me/api/v1',
        sentryDsn:
          (import.meta.env['NG_APP_SENTRY_DSN'] as string | undefined) ||
          configJson.sentryDsn ||
          '',
      };

      // Expone config a window para que environment.prod.ts pueda acceder
      (window as any).__config = this.config;
    } catch (error) {
      // Fallback: ENV vars > hardcoded
      console.warn(
        '[Config] Failed to load config.json, using ENV vars or defaults',
        error,
      );
      this.config = {
        apiUrl:
          (import.meta.env['NG_APP_API_URL'] as string | undefined) ||
          'https://api.brydyan.me/api/v1',
        sentryDsn:
          (import.meta.env['NG_APP_SENTRY_DSN'] as string | undefined) || '',
      };
      (window as any).__config = this.config;
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
   */
  getApiUrl(): string {
    return this.getConfig().apiUrl;
  }

  /**
   * Acceso directo a sentryDsn
   */
  getSentryDsn(): string {
    return this.getConfig().sentryDsn || '';
  }
}
