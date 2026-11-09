export const environment = {
  production: true,
  // In production the SPA is served from the same origin as the API,
  // so a relative base keeps the httpOnly refresh cookie working.
  apiUrl: '/api/v1',
  // Sentry error tracking and crash reporting (dev: 100% sampling, prod: 10%)
  sentryDsn: '',
};
