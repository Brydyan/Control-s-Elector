export const environment = {
  production: true,
  // Production API URL: se inyecta desde assets/config.json en runtime (sin rebuild necesario)
  // Ver: CLOUDFLARE_PAGES_SETUP.md para instrucciones de configuración
  apiUrl: 'https://api.brydyan.me/api/v1',
  // Sentry error tracking and crash reporting (dev: 100% sampling, prod: 10%)
  sentryDsn: '',
};
