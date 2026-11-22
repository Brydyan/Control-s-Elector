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
   * Carga configuración desde assets/config.json
   * Se invoca en APP_INITIALIZER antes de que Angular inicie
   */
  async load(): Promise<void> {
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
      this.config = {
        apiUrl: 'https://api.brydyan.me/api/v1',
        sentryDsn: '',
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
      return '/api/v1';
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
