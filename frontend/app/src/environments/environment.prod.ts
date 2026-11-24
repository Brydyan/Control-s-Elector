export const environment = {
  production: true,
  // Production API URL: se carga desde assets/config.json en runtime (ConfigService, APP_INITIALIZER)
  // Este valor es el fallback si config.json no está disponible
  apiUrl: 'https://api.brydyan.me/api/v1',
  // Sentry error tracking and crash reporting (dev: 100% sampling, prod: 10%)
  sentryDsn: '',
};
