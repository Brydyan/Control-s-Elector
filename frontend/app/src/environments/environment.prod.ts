export const environment = {
  production: true,
  // Production uses absolute URL to Railway via Cloudflare subdomain
  // env var allows per-environment configuration
  apiUrl:
    (import.meta.env.VITE_API_URL as string) || 'https://api.brydyan.me/api/v1',
  // Sentry error tracking and crash reporting (dev: 100% sampling, prod: 10%)
  sentryDsn: '',
};
