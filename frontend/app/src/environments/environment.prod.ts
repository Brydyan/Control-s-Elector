export const environment = {
  production: true,
  // Local development API URL (fallback when config.json is not available)
  // In production, the actual URL is loaded from assets/config.json at runtime
  apiUrl: 'https://api.brydyan.me/api/v1',
  // Sentry error tracking and crash reporting (dev: 100% sampling, prod: 10%)
  sentryDsn: '',
};
