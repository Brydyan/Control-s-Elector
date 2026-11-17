export const environment = {
  production: true,
  // Production API URL: se inyecta desde assets/config.json en runtime (sin rebuild necesario)
  // ConfigService carga config.json en APP_INITIALIZER (main.ts)
  apiUrl: 'https://api.brydyan.me/api/v1',
  // Sentry error tracking and crash reporting (dev: 100% sampling, prod: 10%)
  sentryDsn: '',
};
